package io.github.bookshelf.core.data.repository

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.getOrNull
import io.github.bookshelf.core.common.error.isFailure
import io.github.bookshelf.core.common.time.AppClock
import io.github.bookshelf.core.database.dao.BookDao
import io.github.bookshelf.core.database.dao.CollectionBookCount
import io.github.bookshelf.core.database.dao.CollectionDao
import io.github.bookshelf.core.database.entity.CollectionBookCrossRef
import io.github.bookshelf.core.database.entity.CollectionEntity
import io.github.bookshelf.core.data.repository.impl.CollectionRepositoryImpl
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Полки (коллекции): создание, порядок и наполнение.
 *
 * `BookDao` мокается целиком: проверяется работа с составом полки, а не поведение DAO книги.
 * Раньше реализация возвращала `Unsupported` на все шесть методов, и экран показывал
 * «пока не поддерживается».
 */
class CollectionRepositoryTest {

    private val dao = FakeCollectionDao()
    private val bookDao = mockk<BookDao>(relaxed = true)
    private fun repository() = CollectionRepositoryImpl(dao, bookDao, FixedClock)

    @Test
    fun creates_collection_with_given_name() = runTest {
        val created = repository().create("Классика").getOrNull()

        assertThat(created?.name).isEqualTo("Классика")
        assertThat(dao.rows.value).hasSize(1)
    }

    @Test
    fun rejects_blank_name() = runTest {
        assertThat(repository().create("   ").isFailure).isTrue()
        assertThat(dao.rows.value).isEmpty()
    }

    @Test
    fun new_collection_goes_to_the_end_of_the_list() = runTest {
        val repository = repository()
        repository.create("Первая")
        repository.create("Вторая")

        // Порядок пользовательский: новая полка не должна вставать в начало и сдвигать остальные.
        assertThat(repository.observeCollections().first().map { it.name })
            .isEqualTo(listOf("Первая", "Вторая"))
    }

    @Test
    fun adds_and_removes_book() = runTest {
        val repository = repository()
        val collection = repository.create("Полка").getOrNull()!!
        every { bookDao.observeById(any()) } returns MutableStateFlow(null)

        repository.addBook(collection.id, "book-1")
        assertThat(repository.observeBooks(collection.id).first()).isEmpty()

        repository.removeBook(collection.id, "book-1")
        assertThat(dao.refs).isEmpty()
    }

    @Test
    fun book_order_follows_position() = runTest {
        val repository = repository()
        val collection = repository.create("Полка").getOrNull()!!
        every { bookDao.observeById(any()) } returns MutableStateFlow(null)

        repository.addBook(collection.id, "book-1")
        repository.addBook(collection.id, "book-2")

        // Полка — это список: позиция задаёт порядок, а не множество.
        assertThat(dao.refs.map { it.bookId }).isEqualTo(listOf("book-1", "book-2"))
        assertThat(dao.refs.map { it.position }).isEqualTo(listOf(0, 1))
    }

    @Test
    fun rename_keeps_name_when_new_one_is_blank() = runTest {
        val repository = repository()
        val collection = repository.create("Полка").getOrNull()!!

        assertThat(repository.rename(collection.id, "  ").isFailure).isTrue()
        assertThat(repository.observeCollections().first().first().name).isEqualTo("Полка")
    }

    @Test
    fun rename_of_missing_collection_fails() = runTest {
        assertThat(repository().rename("нет", "Имя").isFailure).isTrue()
    }

    @Test
    fun delete_removes_collection_and_its_links() = runTest {
        val repository = repository()
        val collection = repository.create("Полка").getOrNull()!!
        repository.addBook(collection.id, "book-1")

        repository.delete(collection.id)

        assertThat(dao.rows.value).isEmpty()
        assertThat(dao.refs).isEmpty()
    }

    private class FakeCollectionDao : CollectionDao {
        val rows = MutableStateFlow<List<CollectionEntity>>(emptyList())
        val refs = mutableListOf<CollectionBookCrossRef>()

        /** Счётчик книг по полкам — производная от `refs`, а не отдельное хранилище. */
        override fun observeBookCounts(): Flow<List<CollectionBookCount>> = flow {
            emit(refs.groupingBy { ref -> ref.collectionId }.eachCount()
                .map { (id, count) -> CollectionBookCount(id, count) })
        }

        override suspend fun upsertAll(collections: List<CollectionEntity>) {
            rows.value = (rows.value + collections).distinctBy { it.id }
        }

        override fun observeAll(): Flow<List<CollectionEntity>> = rows

        override suspend fun getById(id: String) = rows.value.firstOrNull { it.id == id }

        override suspend fun deleteById(id: String) {
            rows.value = rows.value.filterNot { it.id == id }
            refs.removeAll { it.collectionId == id }
        }

        override suspend fun insertBooks(refs: List<CollectionBookCrossRef>) {
            this.refs += refs
        }

        override suspend fun removeBook(collectionId: String, bookId: String) {
            refs.removeAll { it.collectionId == collectionId && it.bookId == bookId }
        }

        override suspend fun getBooks(collectionId: String) = refs.filter { it.collectionId == collectionId }

        override fun getBooksFlow(collectionId: String): Flow<List<CollectionBookCrossRef>> =
            MutableStateFlow(refs.filter { it.collectionId == collectionId })
    }

    private object FixedClock : AppClock {
        override fun nowMillis(): Long = 1_000_000L
    }
}
