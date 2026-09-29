package io.github.bookshelf.core.data.repository

import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.data.duplicates.DuplicateReport
import io.github.bookshelf.core.data.model.Book
import io.github.bookshelf.core.data.model.LibraryQuery
import io.github.bookshelf.core.data.model.MetadataPatch
import io.github.bookshelf.core.data.model.ReadingStatus
import io.github.bookshelf.core.data.paging.PageResult
import kotlinx.coroutines.flow.Flow

/**
 * Книги: чтение страницами и изменение пользовательских полей (docs/architecture.md §3.1).
 *
 * Единственная точка, через которую feature-модули видят книги: Room-entity и DAO наружу не
 * выходят. Все методы либо `Flow` (наблюдение), либо `suspend` + [AppResult] (команда) — правило
 * проекта: исключения наружу не пробрасываются (docs/architecture.md §1).
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

    /** Удалить запись из библиотеки. Файл книги не удаляется никогда (docs/eink-ux.md §6.7). */
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

    /** Прочитано из API-сервиса FBReader. */
    const val FBREADER_AIDL = "FBREADER_AIDL"
}
