package io.github.bookshelf.core.data.repository

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.getOrNull
import io.github.bookshelf.core.common.error.isFailure
import io.github.bookshelf.core.common.time.AppClock
import io.github.bookshelf.core.database.dao.BookDao
import io.github.bookshelf.core.database.dao.CollectionBookCount
import io.github.bookshelf.core.data.model.CollectionType
import io.github.bookshelf.core.data.model.SmartRules
import io.github.bookshelf.core.database.dao.CollectionDao
import io.github.bookshelf.core.database.entity.BookEntity
import io.github.bookshelf.core.database.entity.CollectionBookCrossRef
import io.github.bookshelf.core.database.entity.CollectionEntity
import io.github.bookshelf.core.data.repository.impl.CollectionRepositoryImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import io.github.bookshelf.core.testing.fake.FakeBookDao

/**
 * Полки (коллекции): создание, порядок и наполнение.
 *
 * `BookDao` мокается целиком: проверяется работа с составом полки, а не поведение DAO книги.
 * Раньше реализация возвращала `Unsupported` на все шесть методов, и экран показывал
 * «пока не поддерживается».
 */
class CollectionRepositoryTest {

    private val dao = FakeCollectionDao()
    /**
     * Фейк, а не `mockk`: `BookDao` — интерфейс проекта, свои типы в тестах не мокаются
     * (ADR-012, правило 2). Фейк умеет ровно то, что читает репозиторий полок, а на остальное
     * падает — «неожиданный путь» видно сразу, тогда как `relaxed`-мок вернул бы пустоту.
     */
    private val bookDao = FakeBookDao()
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
    fun smart_shelf_books_come_from_the_rules() = runTest {
        val repository = repository()
        val shelf = repository.create("Космос", CollectionType.SMART).getOrNull()!!
        repository.updateSmartRules(shelf.id, SmartRules(tags = setOf("sf_space")).toJson())
        bookDao.pageFlow = flowOf(emptyList())
        bookDao.countFlow = flowOf(4)

        repository.observeBooks(shelf.id).first()

        // Состав умной полки считается запросом по правилам, а не связями collection_books:
        // книг в связях у такой полки нет вовсе.
        assertThat(bookDao.pageCalls).isEqualTo(1)
        // И счётчик в списке полок берётся тем же предикатом, иначе умная полка показывала бы 0.
        assertThat(repository.observeCollections().first().single().bookCount).isEqualTo(4)
    }

    @Test
    fun manual_shelf_ignores_rules() = runTest {
        val repository = repository()
        val shelf = repository.create("Ручная").getOrNull()!!
        repository.updateSmartRules(shelf.id, SmartRules(tags = setOf("sf_space")).toJson())
        bookDao.byIdFlow = MutableStateFlow(null)

        repository.observeBooks(shelf.id).first()

        assertThat(bookDao.pageCalls).isEqualTo(0)
    }

    @Test
    fun smart_shelf_without_rules_is_empty_not_the_whole_library() = runTest {
        val repository = repository()
        val shelf = repository.create("Пустая умная", CollectionType.SMART).getOrNull()!!

        assertThat(repository.observeBooks(shelf.id).first()).isEmpty()
        assertThat(bookDao.pageCalls).isEqualTo(0)
    }

    @Test
    fun adds_and_removes_book() = runTest {
        val repository = repository()
        val collection = repository.create("Полка").getOrNull()!!
        bookDao.byIdFlow = MutableStateFlow(null)

        repository.addBook(collection.id, "book-1")
        assertThat(repository.observeBooks(collection.id).first()).isEmpty()

        repository.removeBook(collection.id, "book-1")
        assertThat(dao.refs).isEmpty()
    }

    @Test
    fun book_order_follows_position() = runTest {
        val repository = repository()
        val collection = repository.create("Полка").getOrNull()!!
        bookDao.byIdFlow = MutableStateFlow(null)

        repository.addBook(collection.id, "book-1")
        repository.addBook(collection.id, "book-2")

        // Полка — это список: позиция задаёт порядок, а не множество.
        assertThat(dao.refs.map { it.bookId }).isEqualTo(listOf("book-1", "book-2"))
        assertThat(dao.refs.map { it.position }).isEqualTo(listOf(0, 1))
    }

    @Test
    fun manual_shelf_previews_covers_of_its_books() = runTest {
        val repository = repository()
        val shelf = repository.create("Полка").getOrNull()!!
        dao.covers[shelf.id] = listOf("/covers/1.webp", "/covers/2.webp")

        // Строка полки показывает обложки первых книг: без них полка в списке не узнаётся.
        assertThat(repository.observeCollections().first().single().previewCovers)
            .containsExactly("/covers/1.webp", "/covers/2.webp")
            .inOrder()
    }

    @Test
    fun smart_shelf_previews_covers_from_its_rules() = runTest {
        val repository = repository()
        val shelf = repository.create("Космос", CollectionType.SMART).getOrNull()!!
        repository.updateSmartRules(shelf.id, SmartRules(tags = setOf("sf_space")).toJson())
        // Настоящая сущность, а не мок: проверяется ровно одно поле — путь к обложке.
        val book = bookEntity(coverPath = "/covers/space.webp")
        bookDao.countFlow = flowOf(2)
        bookDao.pageFlow = flowOf(listOf(book))

        // У умной полки обложки берутся предикатом правил: связей collection_books у неё нет.
        assertThat(repository.observeCollections().first().single().previewCovers)
            .containsExactly("/covers/space.webp")
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

    /**
     * Настоящая сущность книги для проверок обложек.
     *
     * Раньше здесь стоял `mockk<BookEntity>(relaxed = true)` с одной подставленной строкой: тест
     * проходил, даже если сущность перестанет отдавать обложку иначе. Настоящий объект заодно
     * документирует, какие поля нужны странице.
     */
    private fun bookEntity(coverPath: String? = null): BookEntity = BookEntity(
        id = "book-1",
        sourceId = "source-1",
        documentUri = "content://documents/book.fb2",
        relativePath = "Автор/Книга.fb2",
        fileName = "Книга.fb2",
        extension = "fb2",
        mimeType = "application/fb2",
        sizeBytes = 1_000L,
        lastModified = 1_000L,
        title = "Книга",
        sortTitle = "Книга",
        authorsText = "Автор",
        coverPath = coverPath,
        addedAt = 1L,
        updatedAt = 1L,
        lastSeenAt = 1L,
    )

    private class FakeCollectionDao : CollectionDao {
        val rows = MutableStateFlow<List<CollectionEntity>>(emptyList())
        val refs = mutableListOf<CollectionBookCrossRef>()

        /** Обложки по полкам: в тесте — заданные значения, а не содержимое `books`. */
        val covers = mutableMapOf<String, List<String>>()

        override fun observePreviewCovers(collectionId: String, limit: Int): Flow<List<String>> =
            flowOf(covers[collectionId].orEmpty().take(limit))

        /** Счётчик книг по полкам — производная от `refs`, а не отдельное хранилище. */
        override fun observeBookCounts(): Flow<List<CollectionBookCount>> = flow {
            emit(refs.groupingBy { ref -> ref.collectionId }.eachCount()
                .map { (id, count) -> CollectionBookCount(id, count) })
        }

        /**
         * Как `@Upsert` в Room: существующая строка обновляется на месте, новая дописывается.
         *
         * Раньше здесь было `(rows + collections).distinctBy { it.id }`, и первым побеждал СТАРЫЙ
         * элемент: правка полки (имя, правила) не доходила до чтения, и тест умной полки видел
         * «правил нет» у полки, которой они только что записаны.
         */
        override suspend fun upsertAll(collections: List<CollectionEntity>) {
            val incoming = collections.associateBy { it.id }
            val updated = rows.value.map { existing -> incoming[existing.id] ?: existing }
            val added = collections.filter { candidate -> rows.value.none { it.id == candidate.id } }
            rows.value = updated + added
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

        override suspend fun removeBookFromAll(bookId: String) {
            refs.removeAll { it.bookId == bookId }
        }

        override suspend fun getBooks(collectionId: String) = refs.filter { it.collectionId == collectionId }

        override suspend fun collectionIdsOfBook(bookId: String) =
            refs.filter { it.bookId == bookId }.map { ref -> ref.collectionId }

        override fun getBooksFlow(collectionId: String): Flow<List<CollectionBookCrossRef>> =
            MutableStateFlow(refs.filter { it.collectionId == collectionId })
    }

    private object FixedClock : AppClock {
        override fun nowMillis(): Long = 1_000_000L
    }
}
