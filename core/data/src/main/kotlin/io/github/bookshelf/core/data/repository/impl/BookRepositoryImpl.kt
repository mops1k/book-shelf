package io.github.bookshelf.core.data.repository.impl

import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.data.model.Book
import io.github.bookshelf.core.data.model.LibraryQuery
import io.github.bookshelf.core.data.model.MetadataPatch
import io.github.bookshelf.core.data.model.ReadingStatus
import io.github.bookshelf.core.data.paging.PageResult
import io.github.bookshelf.core.data.repository.BookRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Заглушка [BookRepository] на время каркаса.
 *
 * Что должна делать (Sprint 1, доработка в Sprint 4):
 * * `observePage` — `books` + `book_authors` + `reading_progress` одним запросом с `LIMIT/OFFSET`,
 *   `totalCount` отдельным `COUNT(*)` по тем же фильтрам, сортировка по индексированным полям;
 * * `observeById` — один `Flow` по `books` с разворотом авторов;
 * * `updateMetadata` — писать в `metadata_overrides` (а не в `books`), чтобы пересканирование не
 *   затирало правки; затем пересобрать доменную модель;
 * * `delete` — только запись: файл книги не удаляется никогда (docs/eink-ux.md §6.7).
 *
 * Сейчас все команды возвращают `AppError.Unsupported`, а наблюдения — пустые значения: UI должен
 * уметь работать с пустой полкой до появления БД (DoD Sprint 1).
 */
@Singleton
class BookRepositoryImpl @Inject constructor() : BookRepository {

    override fun observePage(query: LibraryQuery): Flow<PageResult<Book>> =
        flowOf(PageResult.empty(offset = query.page * query.pageSize, limit = query.pageSize))

    override fun observeById(id: String): Flow<Book?> = flowOf(null)

    override suspend fun getById(id: String): AppResult<Book?> =
        AppResult.failure(AppError.Unsupported("STUB(Sprint 1): BookRepository.getById"))

    override suspend fun updateMetadata(bookId: String, patch: MetadataPatch): AppResult<Unit> =
        AppResult.failure(AppError.Unsupported("STUB(Sprint 1): BookRepository.updateMetadata"))

    override suspend fun setFavorite(bookId: String, favorite: Boolean): AppResult<Unit> =
        AppResult.failure(AppError.Unsupported("STUB(Sprint 1): BookRepository.setFavorite"))

    override suspend fun setRating(bookId: String, rating: Int?): AppResult<Unit> =
        AppResult.failure(AppError.Unsupported("STUB(Sprint 1): BookRepository.setRating"))

    override suspend fun markRead(bookId: String, status: ReadingStatus): AppResult<Unit> =
        AppResult.failure(AppError.Unsupported("STUB(Sprint 1): BookRepository.markRead"))

    override suspend fun delete(bookId: String): AppResult<Unit> =
        AppResult.failure(AppError.Unsupported("STUB(Sprint 1): BookRepository.delete"))
}
