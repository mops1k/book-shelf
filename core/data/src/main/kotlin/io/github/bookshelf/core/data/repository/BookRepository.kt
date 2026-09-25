package io.github.bookshelf.core.data.repository

import io.github.bookshelf.core.common.error.AppResult
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

    /** Удалить запись из библиотеки. Файл книги не удаляется никогда (docs/eink-ux.md §6.7). */
    suspend fun delete(bookId: String): AppResult<Unit>
}
