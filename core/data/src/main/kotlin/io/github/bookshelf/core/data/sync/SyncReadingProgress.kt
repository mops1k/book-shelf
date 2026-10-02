package io.github.bookshelf.core.data.sync

import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.common.error.appRunCatching
import io.github.bookshelf.core.common.error.getOrNull
import io.github.bookshelf.core.common.log.APP_LOG_TAG
import io.github.bookshelf.core.common.log.AppLogger
import io.github.bookshelf.core.data.model.Book
import io.github.bookshelf.core.data.model.ReadingStatus
import io.github.bookshelf.core.database.dao.BookCopyDao
import io.github.bookshelf.core.data.repository.BookRepository
import io.github.bookshelf.core.data.repository.ReadingProgressOrigins
import io.github.bookshelf.core.datastore.SettingsRepository
import io.github.bookshelf.core.readerlink.fbreader.FbReaderApi
import io.github.bookshelf.core.readerlink.fbreader.ReadingProgressSource
import javax.inject.Inject

/**
 * Что произошло при синхронизации прогресса.
 *
 * Отдельные случаи, а не «получилось/не получилось»: выключенная настройка, отсутствующая читалка
 * и открытая чужая книга — это нормальная жизнь, а не ошибка, и в журнале их надо различать.
 */
sealed interface ProgressSyncOutcome {

    /** Синхронизация выключена в настройках: читалку не спрашивали вовсе. */
    data object Disabled : ProgressSyncOutcome

    /** Читалки нет, её API недоступен или сервис не ответил. */
    data object ReaderUnavailable : ProgressSyncOutcome

    /** В читалке ничего не открыто либо она не отдала позицию. */
    data object NoBookOpen : ProgressSyncOutcome

    /** Книга открыта, но её нет в нашей библиотеке (открыта из самой читалки, а не из BookShelf). */
    data object BookUnknown : ProgressSyncOutcome

    /**
     * Прогресс сохранён.
     *
     * @param bookId наша книга, которой он принадлежит.
     * @param percentPercent прогресс в процентах, как его отдала читалка (для журнала и отладки).
     */
    data class Saved(val bookId: String, val percentPercent: Float) : ProgressSyncOutcome
}

/**
 * Синхронизация прогресса чтения из читалки.
 *
 * Читалка отдаёт данные **текущей открытой** книги, а не «прогресс по книге» (спайк 29.09.2026),
 * поэтому сценарий работает так: спросить, что открыто → посчитать процент → найти эту книгу в
 * библиотеке по адресу документа → записать прогресс.
 *
 * Порядок важен и проверяется тестами:
 * 1. выключенную настройку проверяем ДО обращения к читалке — иначе мы будили бы чужой сервис без
 *    надобности (и это было бы видно пользователю как «кто-то дёргает читалку»);
 * 2. сессию связи закрываем всегда — даже если чтение упало;
 * 3. чужая книга и книга не из библиотеки не трогают прогресс: записать чужой процент хуже, чем не
 *    записать ничего.
 */
class SyncReadingProgress @Inject constructor(
    /** Журнал: причина «книга не найдена по адресу читалки» нужна для разбора на устройстве. */
    private val logger: AppLogger,
    private val api: FbReaderApi,
    private val progressSource: ReadingProgressSource,
    private val books: BookRepository,
    private val settings: SettingsRepository,
    /**
     * Связи «файл-копия → книга».
     *
     * Нужны потому, что читалка читает свою копию (созданную внутри нашей библиотеки) и называет
     * книгу её адресом: без разбора через копии прогресс записывался копии, а книга пользователя
     * оставалась без прогресса — и полка показывала процент не у той книги (дефект 01.10.2026).
     */
    private val copies: BookCopyDao,
) {

    /** Прочитать прогресс открытой книги и сохранить его. Исключений наружу не выходит. */
    suspend fun syncNow(): AppResult<ProgressSyncOutcome> = appRunCatching {
        if (!settings.current().reader.enableFbReaderProgress) {
            return@appRunCatching ProgressSyncOutcome.Disabled
        }
        if (!api.connect()) {
            return@appRunCatching ProgressSyncOutcome.ReaderUnavailable
        }
        try {
            val open = api.currentBook() ?: return@appRunCatching ProgressSyncOutcome.NoBookOpen
            val snapshot = progressSource.progressFor(open.path).getOrNull()
                ?: return@appRunCatching ProgressSyncOutcome.NoBookOpen
            // Сначала как есть: для книги без архива читалка отдаёт ровно наш адрес. Затем —
            // приведённый вид: книгу в архиве она называет адресом внутри архива (`zip-entry://`),
            // и по такому адресу книга раньше не находилась вовсе (`BookUnknown`).
            val book = books.findByDocumentUri(open.path).getOrNull()
                ?: books.findByDocumentUri(ReaderBookPath.toDocumentUri(open.path)).getOrNull()
                ?: originalOfCopy(open.path)
                ?: run {
                    // Адрес читалки в журнале обязателен: без него непонятно, почему книга не нашлась
                    // (адрес копии, другое кодирование), и это не отлаживается снаружи.
                    logger.w(APP_LOG_TAG, "прогресс: книга не найдена по адресу читалки: ${open.path}")
                    return@appRunCatching ProgressSyncOutcome.BookUnknown
                }
            books.saveReadingProgress(
                bookId = book.id,
                // Читалка говорит процентами, таблица хранит долю.
                percent = snapshot.percent / PERCENT,
                locatorJson = snapshot.locatorJson,
                source = ReadingProgressOrigins.FBREADER_AIDL,
            )
            markAsReadingIfFresh(book)
            ProgressSyncOutcome.Saved(bookId = book.id, percentPercent = snapshot.percent)
        } finally {
            api.disconnect()
        }
    }

    /**
     * Отметить книгу как читаемую, если статус ещё не выставлен.
     *
     * Полка показывает процент только у книги со статусом «читаю» (`ReadingStatus.badge`), поэтому
     * без этой отметки синхронизация была бы невидимой: данные записаны, а на экране ничего.
     *
     * Уже выставленный статус не трогаем: «прочитано» синхронизация сбрасывать не должна — человек
     * мог открыть законченную книгу, чтобы перечитать главу.
     */
    /**
     * Книга, копией которой является адрес читалки.
     *
     * Читалка читает свою копию и называет книгу её адресом: у копии строки в `books` нет — она
     * живёт связью в `book_copies`, — поэтому по адресу копии находится оригинал, и прогресс
     * записывается ему. Адрес проверяется в том же порядке, что и для книг: как есть, затем в
     * приведённом виде (читалка может назвать файл внутри архива).
     */
    private suspend fun originalOfCopy(path: String): Book? {
        val copy = copies.findByUri(path)
            ?: copies.findByUri(ReaderBookPath.toDocumentUri(path))
            ?: return null
        return books.getById(copy.bookId).getOrNull()
    }

    private suspend fun markAsReadingIfFresh(book: Book) {
        if (book.readingStatus != ReadingStatus.UNREAD) return
        books.markRead(book.id, ReadingStatus.READING)
    }

    private companion object {
        const val PERCENT = 100f
    }
}
