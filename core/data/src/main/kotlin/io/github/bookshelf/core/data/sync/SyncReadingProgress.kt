package io.github.bookshelf.core.data.sync

import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.common.error.appRunCatching
import io.github.bookshelf.core.common.error.getOrNull
import io.github.bookshelf.core.common.log.APP_LOG_TAG
import io.github.bookshelf.core.common.log.AppLogger
import io.github.bookshelf.core.common.reader.ActiveReading
import io.github.bookshelf.core.common.reader.ReaderSession
import io.github.bookshelf.core.data.model.Book
import io.github.bookshelf.core.data.model.BookQuery
import io.github.bookshelf.core.data.model.LibraryQuery
import io.github.bookshelf.core.data.model.ReadingStatus
import io.github.bookshelf.core.data.repository.BookRepository
import io.github.bookshelf.core.database.dao.BookCopyDao
import io.github.bookshelf.core.datastore.SettingsRepository
import io.github.bookshelf.core.readerlink.progress.ProgressBookRef
import io.github.bookshelf.core.readerlink.progress.ProgressSourceId
import io.github.bookshelf.core.readerlink.progress.ReaderBookAddress
import io.github.bookshelf.core.readerlink.progress.ReadingProgressSource
import io.github.bookshelf.core.readerlink.progress.ReadingProgressSources
import io.github.bookshelf.core.readerlink.progress.progressSourceOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Что произошло при синхронизации прогресса.
 *
 * Отдельные случаи, а не «получилось/не получилось»: выключенная настройка, отсутствующая читалка
 * и открытая чужая книга — это нормальная жизнь, а не ошибка, и в журнале их надо различать.
 */
sealed interface ProgressSyncOutcome {

    /** Синхронизация выключена в настройках: читалки не спрашивали вовсе. */
    data object Disabled : ProgressSyncOutcome

    /**
     * Спросить было некого: источника для открытой читалки нет, либо читалка недоступна
     * (API не отвечает, источник не открылся).
     */
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
 * Синхронизация прогресса чтения из читалок.
 *
 * Работает поверх реестра источников (`ReadingProgressSources`): у каждой читалки свой способ хранить
 * прогресс, но для сценария она — источник с одинаковым контрактом. Объём работы ограничен явно
 * (требование Александра, 05.10.2026): разбирать состояние всех читалок по всей библиотеке нельзя —
 * слабое e-ink-устройство от этого подвешивается.
 *
 * Два режима, и они не смешиваются:
 * 1. **Идёт чтение** (`ReaderSession.active()`): спрашиваем **только ту читалку**, которой открыта
 *    книга. Книга тоже известна — она записана в сессии. Остальные хранилища в это время не
 *    читаются вовсе.
 * 2. **Чтения нет**: у читалок с API спрашиваем «что открыто» (FBReader сам это знает), а файловые
 *    источники читаются **по книгам, выбранным для чтения** — порциями и с уступкой сессии, если
 *    человек вернулся к чтению посреди прохода.
 *
 * Почему порядок действий именно такой и почему это проверяется тестами:
 * * выключенный источник не трогаем вовсе — иначе мы читали бы чужие файлы и будили чужой сервис
 *   без разрешения;
 * * привязку к сервису закрывает сам источник, и закрывает её **всегда** — даже если чтение упало;
 * * чужая книга и книга не из библиотеки не трогают прогресс: записать чужой процент хуже, чем не
 *   записать ничего.
 */
class SyncReadingProgress @Inject constructor(
    /** Журнал: причина «книга не найдена по адресу читалки» нужна для разбора на устройстве. */
    private val logger: AppLogger,
    private val sources: ReadingProgressSources,
    private val books: BookRepository,
    private val settings: SettingsRepository,
    /** Что читается сейчас: во время чтения спрашиваем только открытую читалку. */
    private val session: ReaderSession,
    /**
     * Связи «файл-копия → книга».
     *
     * Нужны потому, что читалка читает свою копию (созданную внутри нашей библиотеки) и называет
     * книгу её адресом: без разбора через копии прогресс записывался копии, а книга пользователя
     * оставалась без прогресса — и полка показывала процент не у той книги (дефект 01.10.2026).
     */
    private val copies: BookCopyDao,
) {

    /** Прочитать прогресс и сохранить его. Исключений наружу не выходит. */
    suspend fun syncNow(): AppResult<ProgressSyncOutcome> = appRunCatching {
        val enabled = settings.current().reader.enabledProgressSources()
        if (enabled.isEmpty()) return@appRunCatching ProgressSyncOutcome.Disabled
        val reading = session.active()
        val pass = passFor(reading, enabled)
        // Пустой проход означает «источника для открытой читалки нет»: спрашивать всех остальных
        // в этот момент запрещено — на этом и стоит правило нагрузки.
        if (pass.isEmpty()) return@appRunCatching ProgressSyncOutcome.ReaderUnavailable
        summarizeSyncOutcomes(pass.map { readFrom(it, reading) })
    }

    /** Кого спрашивать: во время чтения — только открытую читалку, иначе всех включённых. */
    private fun passFor(
        reading: ActiveReading?,
        enabled: Set<ProgressSourceId>,
    ): List<ReadingProgressSource> {
        if (reading == null) return sources.forPass(activeSourceId = null).filter { it.id in enabled }
        val active = reading.readerProfileId?.let { progressSourceOf(it) } ?: return emptyList()
        return sources.forPass(activeSourceId = active).filter { it.id in enabled }
    }

    private suspend fun readFrom(
        source: ReadingProgressSource,
        reading: ActiveReading?,
    ): ProgressSyncOutcome = when {
        // Читалка сама знает, что открыто, — её и спрашиваем: во время чтения это ещё и
        // единственный корректный способ для копий и архивов.
        source.knowsOpenBook -> readOpenBook(source)
        // Файловый источник: во время чтения книга известна из сессии.
        reading != null -> readBook(source, reading.bookId)
        else -> readSelectedBooks(source)
    }

    /** Проход «что открыто»: адрес называет читалка, книгу находим в библиотеке. */
    private suspend fun readOpenBook(source: ReadingProgressSource): ProgressSyncOutcome {
        if (!source.connect()) return ProgressSyncOutcome.ReaderUnavailable
        return try {
            val address = source.openedBook() ?: return ProgressSyncOutcome.NoBookOpen
            val book = findBook(address) ?: return unknownBook(source, address)
            saveFrom(source, book, address.documentUri)
        } finally {
            source.disconnect()
        }
    }

    /** Проход по одной книге: её называет сессия чтения (или фоновая работа по выбранным книгам). */
    private suspend fun readBook(source: ReadingProgressSource, bookId: String): ProgressSyncOutcome {
        if (!source.connect()) return ProgressSyncOutcome.ReaderUnavailable
        return try {
            val book = books.getById(bookId).getOrNull() ?: return ProgressSyncOutcome.BookUnknown
            saveFrom(source, book, book.documentUri)
        } finally {
            source.disconnect()
        }
    }

    /**
     * Проход по книгам, выбранным для чтения.
     *
     * Порциями ([BATCH_SIZE]) с паузой между ними и с проверкой сессии перед каждой книгой: если
     * человек вернулся к чтению, проход останавливается на месте, а не догоняет его нагрузкой.
     * Ограничение [SELECTED_LIMIT] — на случай, когда «читаю» стоит у половины библиотеки: один
     * проход всё равно не должен превращаться в полный обход.
     */
    private suspend fun readSelectedBooks(source: ReadingProgressSource): ProgressSyncOutcome {
        if (!source.connect()) return ProgressSyncOutcome.ReaderUnavailable
        return try {
            val selected = selectedBooks()
            var outcome: ProgressSyncOutcome = ProgressSyncOutcome.NoBookOpen
            for ((index, book) in selected.withIndex()) {
                if (session.isActive()) break
                val read = saveFrom(source, book, book.documentUri)
                if (read is ProgressSyncOutcome.Saved && outcome !is ProgressSyncOutcome.Saved) outcome = read
                if ((index + 1) % BATCH_SIZE == 0 && index + 1 < selected.size) delay(BATCH_PAUSE_MILLIS)
            }
            outcome
        } finally {
            source.disconnect()
        }
    }

    /** Прочитать прогресс книги и записать его; наружу — исход, а не исключение. */
    private suspend fun saveFrom(
        source: ReadingProgressSource,
        book: Book,
        readerAddress: String,
    ): ProgressSyncOutcome {
        val snapshot = source.progressFor(book.toRef(readerAddress)).getOrNull()
            ?: return ProgressSyncOutcome.NoBookOpen
        books.saveReadingProgress(
            bookId = book.id,
            // Читалка говорит процентами, таблица хранит долю.
            percent = snapshot.percent / PERCENT,
            locatorJson = snapshot.locatorJson,
            source = source.id.storageKey,
        )
        markAsReadingIfFresh(book)
        return ProgressSyncOutcome.Saved(bookId = book.id, percentPercent = snapshot.percent)
    }

    /** Книги, выбранные для чтения: по ним работает файловый источник вне сессии. */
    private suspend fun selectedBooks(): List<Book> = books.observePage(
        LibraryQuery(
            bookQuery = BookQuery(statuses = setOf(ReadingStatus.READING)),
            pageSize = SELECTED_LIMIT,
        ),
    ).first().items

    private fun unknownBook(source: ReadingProgressSource, address: ReaderBookAddress): ProgressSyncOutcome {
        // Адрес читалки в журнале обязателен: без него непонятно, почему книга не нашлась
        // (адрес копии, другое кодирование), и это не отлаживается снаружи.
        logger.w(
            APP_LOG_TAG,
            "прогресс ${source.id.storageKey}: книга не найдена по адресу ${address.documentUri}",
        )
        return ProgressSyncOutcome.BookUnknown
    }

    /**
     * Найти нашу книгу по тому, как её назвала читалка.
     *
     * Порядок попыток: адрес как есть (обычный случай — читалка отдаёт ровно наш `content://`), затем
     * приведённый вид (книга в архиве: читалка называет адрес внутри `zip-entry://`), затем связь
     * копии (читалка читает свою копию внутри библиотеки).
     */
    private suspend fun findBook(address: ReaderBookAddress): Book? =
        books.findByDocumentUri(address.documentUri).getOrNull()
            ?: books.findByDocumentUri(ReaderBookPath.toDocumentUri(address.documentUri)).getOrNull()
            ?: originalOfCopy(address.documentUri)

    /**
     * Книга, копией которой является адрес читалки.
     *
     * Читалка читает свою копию и называет книгу её адресом: у копии строки в `books` нет — она
     * живёт связью в `book_copies`, — поэтому по адресу копии находится оригинал, и прогресс
     * записывается ему.
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

    /**
     * Книга для источника: наши поля плюс адрес, которым её назвала читалка.
     *
     * Имя файла берём из относительного пути, а если его нет — из названия книги: у читалок, которые
     * знают только имя файла, другого ключа не будет.
     */
    private fun Book.toRef(readerAddress: String) = ProgressBookRef(
        bookId = id,
        documentUri = documentUri,
        readerAddress = readerAddress,
        fileName = relativePath.substringAfterLast('/').ifEmpty { title },
        relativePath = relativePath,
        sizeBytes = sizeBytes,
        librarySourceId = sourceId,
    )

    private companion object {
        const val PERCENT = 100f

        /** Сколько книг, выбранных для чтения, разбирает один проход. */
        const val SELECTED_LIMIT = 50

        /** Размер порции: между порциями делаем паузу, чтобы не грузить устройство подряд. */
        const val BATCH_SIZE = 10

        const val BATCH_PAUSE_MILLIS = 100L
    }
}
