package io.github.bookshelf.core.data.repository

import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.data.duplicates.DuplicateReport
import io.github.bookshelf.core.data.model.Book
import io.github.bookshelf.core.data.model.BookCopy
import io.github.bookshelf.core.data.model.LibraryQuery
import io.github.bookshelf.core.data.model.MetadataPatch
import io.github.bookshelf.core.data.model.ReadingStatus
import io.github.bookshelf.core.data.paging.PageResult
import io.github.bookshelf.core.readerlink.progress.ProgressSourceKeys
import kotlinx.coroutines.flow.Flow

/**
 * Книги: чтение страницами и изменение пользовательских полей («Архитектура» §3.1).
 *
 * Единственная точка, через которую feature-модули видят книги: Room-entity и DAO наружу не
 * выходят. Все методы либо `Flow` (наблюдение), либо `suspend` + [AppResult] (команда) — правило
 * проекта: исключения наружу не пробрасываются («Архитектура» §1).
 *
 * Патч метаданных отделён от «прочитать/записать»: `updateMetadata` меняет только переданные поля
 * и не трогает `lastOpenedAt`/прогресс.
 */
interface BookRepository {

    /** Наблюдать страницу полки по фильтрам, сортировке и группировке. */
    fun observePage(query: LibraryQuery): Flow<PageResult<Book>>

    /** Наблюдать книгу по id (карточка книги). */
    fun observeById(id: String): Flow<Book?>

    /** Прочитать книгу один раз. */
    suspend fun getById(id: String): AppResult<Book?>

    /** Применить патч метаданных (ручное редактирование). */
    suspend fun updateMetadata(bookId: String, patch: MetadataPatch): AppResult<Unit>

    /** Полка «Избранное». */
    suspend fun setFavorite(bookId: String, favorite: Boolean): AppResult<Unit>

    /** Рейтинг 0..5; `null` — снять оценку. */
    suspend fun setRating(bookId: String, rating: Int?): AppResult<Unit>

    /** Отметить статус чтения вручную. */
    suspend fun markRead(bookId: String, status: ReadingStatus): AppResult<Unit>

    /**
     * Книга по адресу документа.
     *
     * Адрес — тот самый `content://`, которым книга открывается в читалке: синхронизация прогресса
     * получает от читалки именно его (проверено на устройстве 29.09.2026), поэтому поиск идёт по
     * `books.documentUri`, а не по пути и не по хешу.
     */
    suspend fun findByDocumentUri(documentUri: String): AppResult<Book?>

    /**
     * Сохранить прогресс чтения книги.
     *
     * @param percent доля прочитанного 0f..1f — в таблице `reading_progress` хранится доля, а не
     *   проценты: читалка отдаёт проценты, перевод делает вызывающий.
     * @param locatorJson позиция в формате читалки; `null`, если её нет.
     * @param source канал получения прогресса ([ReadingProgressOrigins]).
     */
    suspend fun saveReadingProgress(
        bookId: String,
        percent: Float,
        locatorJson: String? = null,
        source: String = ReadingProgressOrigins.MANUAL,
    ): AppResult<Unit>

    /** Удалить запись из библиотеки. Файл книги не удаляется никогда («E-ink UX» §6.7). */
    suspend fun delete(bookId: String): AppResult<Unit>

    /**
     * Удалить книги вместе с их файлами на устройстве.
     *
     * Файл удаляется первым: если провайдер откажет, запись остаётся в библиотеке. Иначе строка
     * исчезла бы, а следующий скан вернул бы книгу на полку — и удаление выглядело бы так, будто
     * оно отменилось само.
     *
     * @return сколько записей удалено и на скольких файлах провайдер отказал.
     */
    suspend fun deletePermanently(bookIds: List<String>): AppResult<DeletionResult>

    /** Найти книги с одинаковым содержимым и файлы, которые не читаются (нулевой размер). */
    suspend fun findDuplicates(): AppResult<DuplicateReport>

    /**
     * Файлы-копии книг, найденные сканом.
     *
     * Копии книгой не считаются (см. [BookCopy]), поэтому у них нет своего места на полке: список
     * нужен, чтобы показать занятое место и дать убрать файл.
     */
    fun copies(): Flow<List<BookCopy>>

    /**
     * Убрать файлы копий с устройства.
     *
     * Отказ провайдера не отменяет попытку для остальных и **не** убирает связь: файл на диске, и в
     * списке он должен остаться видимым, чтобы попробовать снова.
     */
    suspend fun removeCopies(copyUris: List<String>): AppResult<DeletionResult>

    /**
     * Книги, файлы которых не разобрались: имя и причина отказа.
     *
     * Нужны отчёту сканирования. Разбор идёт **после** скана (это отдельная работа), поэтому
     * список берётся из библиотеки, а не из статистики прогона: иначе раздел «Проблемные файлы»
     * всегда оставался бы пустым.
     */
    suspend fun booksWithParseErrors(): AppResult<List<Book>>

    /**
     * Оптимизировать книгу FB2 (или FB2.ZIP) без потери данных.
     *
     * Файл заменяется уменьшенным только если оптимизация дала выигрыш. Пока открыта читалка,
     * оптимизация не запускается: это тяжёлая работа, и она не должна конкурировать с чтением.
     *
     * Книга, которую уже проверяли и файл которой с тех пор не менялся, не перечитывается:
     * результат приходит сразу с `alreadyOptimized = true`. Иначе повторное нажатие
     * «Оптимизировать все книги FB2» заново пересобирало бы всю библиотеку — на 78 книгах это
     * около часа (замечено Александром 30.09.2026).
     */
    suspend fun optimizeBook(bookId: String): AppResult<BookOptimizationResult>

    /**
     * Оптимизировать все книги FB2 и FB2.ZIP без потери данных.
     *
     * Перебор и правило «уступаем чтению» живут здесь, а не на экране: открытая читалка
     * останавливает прогон между книгами, и об этом сообщается в итоге, а не молчанием.
     * Сбой одной книги не срывает остальные — испорченный файл в библиотеке не повод
     * отказать в оптимизации всем.
     *
     * Отмена — отменой корутины: прогон длинный, и человек вправе его прервать.
     *
     * @param onProgress сколько книг уже обработано и сколько их всего; вызывается и до первой
     *   книги, чтобы полоса прогресса знала общее число.
     */
    suspend fun optimizeAllFb2(
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): AppResult<BookOptimizationBatch>

    /**
     * Очистить библиотеку и заново просканировать все настроенные папки.
     *
     * Файлы на устройстве не трогаются: удаляются только записи в базе, после чего сканы
     * запускаются заново и книги находятся повторно. Нужен, когда метаданные или обложки
     * разобрались неверно — пересобрать их дешевле, чем править каждую книгу вручную.
     */
    suspend fun rescanAll(): AppResult<Unit>
}

/**
 * Итог удаления книг с устройства.
 *
 * @param deleted сколько записей удалено вместе с файлами.
 * @param failed на скольких книгах провайдер отказал в удалении файла: записи таких книг остались.
 */
data class DeletionResult(
    val deleted: Int,
    val failed: Int,
)

/**
 * Откуда пришёл прогресс чтения.
 *
 * Значения — строки в `reading_progress.source`: так их видно в базе и в диагностике, а новый канал
 * (встроенная читалка, ручная правка) добавляется без миграции.
 */
object ReadingProgressOrigins {

    /** Отметка вручную или правка на экране. */
    const val MANUAL = "MANUAL"

    /**
     * Каналы читалок.
     *
     * Значения берутся из [ProgressSourceKeys], а не пишутся здесь заново: их источник — читалка,
     * которая прогресс отдала, и разойтись эти строки не должны ни при каких правках.
     */
    const val FBREADER_AIDL: String = ProgressSourceKeys.FBREADER_AIDL

    /** Прогресс из sidecar-файла KOReader. */
    const val KOREADER_SIDECAR: String = ProgressSourceKeys.KOREADER_SIDECAR
}

/**
 * Итог оптимизации книги.
 *
 * @param savedBytes сколько байт сэкономлено; 0 — выигрыша не было и файл остался прежним.
 * @param beforeBytes размер до оптимизации.
 * @param afterBytes размер после (или прежний, если замены не было).
 * @param images сколько картинок пересобрано.
 */
data class BookOptimizationResult(
    val savedBytes: Int,
    val beforeBytes: Int,
    val afterBytes: Int,
    val images: Int,
    /** Книгу не перечитывали: эту версию файла уже проверяли. */
    val alreadyOptimized: Boolean = false,
) {
    /** Файл заменён: экономия есть. */
    val changed: Boolean get() = savedBytes > 0
}

/**
 * Итог оптимизации всей библиотеки FB2.
 *
 * @param total сколько книг подходило под оптимизацию.
 * @param optimized сколько файлов заменено уменьшенными.
 * @param skipped сколько книг проверено, но выигрыша не дало.
 * @param failed сколько книг не удалось обработать (файл недоступен, сбой записи).
 * @param savedBytes сколько байт сэкономлено всего.
 * @param stoppedByReader прогон прерван открытой читалкой — значит, обработано не всё, и
 *   выдавать это за «оптимизировано всё» нельзя.
 * @param alreadyOptimized сколько книг пропущено как уже проверенные: их файлы не менялись с
 *   прошлого прогона, и перечитывать их незачем.
 */
data class BookOptimizationBatch(
    val total: Int,
    val optimized: Int,
    val skipped: Int,
    val failed: Int,
    val savedBytes: Long,
    val stoppedByReader: Boolean,
    val alreadyOptimized: Int = 0,
) {
    /** Сколько книг реально просмотрено. */
    val processed: Int get() = optimized + skipped + failed
}
