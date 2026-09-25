package io.github.bookshelf.core.data.repository.impl

import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.data.model.Book
import io.github.bookshelf.core.data.model.Collection
import io.github.bookshelf.core.data.model.CollectionType
import io.github.bookshelf.core.data.repository.CollectionRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Заглушка [CollectionRepository].
 *
 * Что должна делать (Sprint 7, ADR-008):
 * * CRUD `collections` (тип `MANUAL`/`SMART`, порядок, иконка);
 * * `SMART`-полки вычисляются тем же предикатом, что и фильтры, по `rulesJson` — одна реализация
 *   на фильтр и на полку, иначе поведение разъедется;
 * * `collection_books` с позицией для ручных полок.
 */
@Singleton
class CollectionRepositoryImpl @Inject constructor() : CollectionRepository {

    override fun observeCollections(): Flow<List<Collection>> = flowOf(emptyList())

    override fun observeBooks(collectionId: String): Flow<List<Book>> = flowOf(emptyList())

    override suspend fun create(name: String, type: CollectionType): AppResult<Collection> =
        AppResult.failure(AppError.Unsupported("STUB(Sprint 7): CollectionRepository.create"))

    override suspend fun rename(collectionId: String, name: String): AppResult<Unit> =
        AppResult.failure(AppError.Unsupported("STUB(Sprint 7): CollectionRepository.rename"))

    override suspend fun delete(collectionId: String): AppResult<Unit> =
        AppResult.failure(AppError.Unsupported("STUB(Sprint 7): CollectionRepository.delete"))

    override suspend fun addBook(collectionId: String, bookId: String): AppResult<Unit> =
        AppResult.failure(AppError.Unsupported("STUB(Sprint 7): CollectionRepository.addBook"))

    override suspend fun removeBook(collectionId: String, bookId: String): AppResult<Unit> =
        AppResult.failure(AppError.Unsupported("STUB(Sprint 7): CollectionRepository.removeBook"))

    override suspend fun updateSmartRules(collectionId: String, rulesJson: String): AppResult<Unit> =
        AppResult.failure(AppError.Unsupported("STUB(Sprint 7): CollectionRepository.updateSmartRules"))
}
