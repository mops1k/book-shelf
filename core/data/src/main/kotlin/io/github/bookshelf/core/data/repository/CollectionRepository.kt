package io.github.bookshelf.core.data.repository

import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.data.model.Book
import io.github.bookshelf.core.data.model.Collection
import io.github.bookshelf.core.data.model.CollectionType
import kotlinx.coroutines.flow.Flow

/**
 * Коллекции: ручные полки и умные подборки (docs/architecture.md §3.3, ADR-008).
 *
 * Умные полки хранят правила JSON-ом в том же формате, что и фильтры, и вычисляются **тем же
 * предикатом**, что и полка, — иначе поведение фильтра и полки разъедется.
 */
interface CollectionRepository {

    fun observeCollections(): Flow<List<Collection>>

    /** Наблюдать книги коллекции (порядок — как в `collection_books`). */
    fun observeBooks(collectionId: String): Flow<List<Book>>

    /** Создать полку; для [CollectionType.SMART] правила задаются позже [updateSmartRules]. */
    suspend fun create(name: String, type: CollectionType = CollectionType.MANUAL): AppResult<Collection>

    suspend fun rename(collectionId: String, name: String): AppResult<Unit>

    /** Удалить полку (книги и файлы не трогаются). */
    suspend fun delete(collectionId: String): AppResult<Unit>

    suspend fun addBook(collectionId: String, bookId: String): AppResult<Unit>

    suspend fun removeBook(collectionId: String, bookId: String): AppResult<Unit>

    /** Правила умной полки в формате фильтров (docs/architecture.md §3.5). */
    suspend fun updateSmartRules(collectionId: String, rulesJson: String): AppResult<Unit>
}
