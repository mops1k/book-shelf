package io.github.bookshelf.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.AppResult
import io.mockk.mockk
import io.github.bookshelf.core.common.error.getOrNull
import io.github.bookshelf.core.common.error.isFailure
import io.github.bookshelf.core.common.error.isSuccess
import io.github.bookshelf.core.data.model.BookQuery
import io.github.bookshelf.core.data.model.Grouping
import io.github.bookshelf.core.data.model.LibraryQuery
import io.github.bookshelf.core.data.model.MetadataPatch
import io.github.bookshelf.core.data.model.ReadingStatus
import io.github.bookshelf.core.data.model.SortOrder
import io.github.bookshelf.core.data.repository.impl.BookRepositoryImpl
import io.github.bookshelf.core.database.BookshelfDatabase
import io.github.bookshelf.core.database.entity.AuthorEntity
import io.github.bookshelf.core.database.entity.BookEntity
import io.github.bookshelf.core.database.entity.BookAuthorCrossRef
import io.github.bookshelf.core.database.entity.ReadingProgressEntity
import io.github.bookshelf.core.database.entity.SourceEntity
import io.github.bookshelf.core.database.model.SourceKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Чтение полки и команды над книгами (docs/architecture.md §3.1, §3.5).
 *
 * Проверяется ровно то, без чего экран полки работать не будет: фильтры должны сочетаться,
 * сортировка — применяться, страница — считаться по тем же фильтрам, а команды — менять состояние
 * книги, не трогая файл.
 *
 * Два акцента на том, где ошибка не видна глазом, но ломает полку:
 * * пагинация обязана быть **стабильной** — без добивочного ключа сортировки по `id` строки с
 *   одинаковым заголовком прыгают между страницами, и пользователь видит дубли и пропуски;
 * * `totalCount` обязан считаться по **тем же** фильтрам, что и выборка, — иначе подпись
 *   «Стр. 1/12» показывает число, которого на самом деле нет.
 *
 * Порядок ожидаемых заголовков задан явно: при `COLLATE NOCASE` русские заголовки сортируются по
 * кодам UTF-8, поэтому «Война» (0x412 0x439) идёт раньше «Воскресение» (0x412 0x441).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class BookRepositoryTest {

    private lateinit var database: BookshelfDatabase
    private lateinit var repository: BookRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BookshelfDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = BookRepositoryImpl(
            bookDao = database.bookDao(),
            overrideDao = database.metadataOverrideDao(),
            readingProgressDao = database.readingProgressDao(),
            // Зависимости пересканирования нужны конструктору, но тест их не трогает.
            sourceRepository = mockk(relaxed = true),
            scanRepository = mockk(relaxed = true),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun empty_library_yields_an_empty_page_with_zero_total() = runBlocking {
        val page = repository.observePage(LibraryQuery()).first()

        assertThat(page.items).isEmpty()
        assertThat(page.totalCount).isEqualTo(0)
    }

    @Test
    fun returns_all_books_when_no_filters_are_set() = runBlocking {
        givenLibrary()

        val page = repository.observePage(LibraryQuery()).first()

        assertThat(page.totalCount).isEqualTo(5)
        assertThat(page.items).hasSize(5)
    }

    @Test
    fun filters_by_author() = runBlocking {
        givenLibrary()

        val page = repository.observePage(
            LibraryQuery(bookQuery = BookQuery(authorNames = setOf(DOSTOEVSKY))),
        ).first()

        assertThat(page.totalCount).isEqualTo(1)
        assertThat(page.items.single().title).isEqualTo("Преступление и наказание")
    }

    @Test
    fun filters_by_series() = runBlocking {
        givenLibrary()

        val page = repository.observePage(
            LibraryQuery(bookQuery = BookQuery(series = setOf("Классика"))),
        ).first()

        assertThat(page.items.map { it.title }).isEqualTo(listOf("Анна Каренина", "Война и мир"))
    }

    @Test
    fun combines_different_facets_with_and_semantics() = runBlocking {
        givenLibrary()

        val page = repository.observePage(
            LibraryQuery(bookQuery = BookQuery(authorNames = setOf(TOLSTOY), series = setOf("Классика"))),
        ).first()

        assertThat(page.items.map { it.title }).isEqualTo(listOf("Анна Каренина", "Война и мир"))
    }

    @Test
    fun several_values_inside_one_facet_use_or_semantics() = runBlocking {
        givenLibrary()

        val page = repository.observePage(
            LibraryQuery(bookQuery = BookQuery(authorNames = setOf(TOLSTOY, DOSTOEVSKY))),
        ).first()

        // 3 книги Толстого + 1 Достоевского.
        assertThat(page.totalCount).isEqualTo(4)
    }

    @Test
    fun filters_by_favorites_only() = runBlocking {
        givenLibrary()
        repository.setFavorite("war", true).assertOk()

        val page = repository.observePage(
            LibraryQuery(bookQuery = BookQuery(favoritesOnly = true)),
        ).first()

        assertThat(page.totalCount).isEqualTo(1)
        assertThat(page.items.single().title).isEqualTo("Война и мир")
    }

    @Test
    fun filters_by_reading_status() = runBlocking {
        givenLibrary()
        repository.markRead("war", ReadingStatus.READ).assertOk()

        val page = repository.observePage(
            LibraryQuery(bookQuery = BookQuery(statuses = setOf(ReadingStatus.READ))),
        ).first()

        assertThat(page.totalCount).isEqualTo(1)
    }

    @Test
    fun filters_by_format() = runBlocking {
        givenLibrary()

        val page = repository.observePage(
            LibraryQuery(bookQuery = BookQuery(formats = setOf("pdf"))),
        ).first()

        assertThat(page.totalCount).isEqualTo(1)
        assertThat(page.items.single().extension).isEqualTo("pdf")
    }

    @Test
    fun filters_by_search_term() = runBlocking {
        givenLibrary()

        val page = repository.observePage(
            LibraryQuery(bookQuery = BookQuery(searchTerm = "каренина")),
        ).first()

        assertThat(page.totalCount).isEqualTo(1)
        assertThat(page.items.single().title).isEqualTo("Анна Каренина")
    }

    @Test
    fun sorts_by_year_ascending() = runBlocking {
        givenLibrary()

        val page = repository.observePage(LibraryQuery(sort = SortOrder.YEAR)).first()

        assertThat(page.items.map { it.publishedYear }).isEqualTo(listOf(1866, 1869, 1877, 1899, 1910))
    }

    @Test
    fun sorts_descending_when_asked() = runBlocking {
        givenLibrary()

        val page = repository.observePage(LibraryQuery(sort = SortOrder.YEAR, descending = true)).first()

        assertThat(page.items.map { it.publishedYear }).isEqualTo(listOf(1910, 1899, 1877, 1869, 1866))
    }

    @Test
    fun sorts_by_reading_progress_using_joined_progress() = runBlocking {
        givenLibrary()
        database.readingProgressDao().upsert(ReadingProgressEntity(bookId = "war", percent = 0.75f, updatedAt = 1L))
        database.readingProgressDao().upsert(ReadingProgressEntity(bookId = "anna", percent = 0.10f, updatedAt = 1L))

        val page = repository.observePage(LibraryQuery(sort = SortOrder.PROGRESS, descending = true)).first()

        // У остальных книг прогресса нет, то есть 0; порядок среди них задаёт добивочный ключ по id.
        assertThat(page.items.map { it.id }).isEqualTo(listOf("war", "anna", "crime", "mothers", "resurrection"))
    }

    @Test
    fun pagination_returns_disjoint_stable_pages() = runBlocking {
        givenLibrary()

        val firstPage = repository.observePage(LibraryQuery(page = 0, pageSize = 2)).first()
        val secondPage = repository.observePage(LibraryQuery(page = 1, pageSize = 2)).first()
        val thirdPage = repository.observePage(LibraryQuery(page = 2, pageSize = 2)).first()

        assertThat(firstPage.items.map { it.id }).isEqualTo(listOf("anna", "war"))
        assertThat(secondPage.items.map { it.id }).isEqualTo(listOf("resurrection", "mothers"))
        assertThat(thirdPage.items.map { it.id }).isEqualTo(listOf("crime"))
        assertThat(firstPage.totalCount).isEqualTo(5)
        assertThat(secondPage.totalCount).isEqualTo(5)
    }

    @Test
    fun total_count_respects_the_same_filters_as_the_selection() = runBlocking {
        givenLibrary()

        val page = repository.observePage(
            LibraryQuery(bookQuery = BookQuery(authorNames = setOf(TOLSTOY)), pageSize = 1),
        ).first()

        assertThat(page.items).hasSize(1)
        assertThat(page.totalCount).isEqualTo(3)
        assertThat(page.hasNext).isTrue()
    }

    @Test
    fun grouping_orders_by_group_key_first() = runBlocking {
        givenLibrary()

        val page = repository.observePage(LibraryQuery(grouping = Grouping.SERIES, sort = SortOrder.YEAR)).first()

        // Группы по алфавиту: «Классика», «Русская классика», «Советская».
        assertThat(page.items.first().seriesName).isEqualTo("Классика")
        assertThat(page.items.map { it.seriesName }.distinct()).hasSize(3)
    }

    @Test
    fun observes_single_book_with_its_authors() = runBlocking {
        givenLibrary()

        val book = repository.observeById("war").first()

        assertThat(book?.title).isEqualTo("Война и мир")
        assertThat(book?.authors).isEqualTo(listOf("Лев Толстой"))
    }

    @Test
    fun get_by_id_fails_for_unknown_book() = runBlocking {
        val result = repository.getById("нет-такой")

        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun set_favorite_changes_only_the_flag() = runBlocking {
        givenLibrary()
        val before = repository.getById("war").getOrNull()

        repository.setFavorite("war", true).assertOk()

        val after = repository.getById("war").getOrNull()
        assertThat(after?.favorite).isTrue()
        assertThat(after?.title).isEqualTo(before?.title)
    }

    @Test
    fun set_rating_rejects_a_value_outside_the_scale() = runBlocking {
        givenLibrary()

        assertThat(repository.setRating("war", 7).isFailure).isTrue()
        assertThat(repository.setRating("war", -1).isFailure).isTrue()
    }

    @Test
    fun set_rating_accepts_the_scale_boundaries() = runBlocking {
        givenLibrary()

        repository.setRating("war", 5).assertOk()
        assertThat(repository.getById("war").getOrNull()?.rating).isEqualTo(5)

        repository.setRating("war", null).assertOk()
        assertThat(repository.getById("war").getOrNull()?.rating).isNull()
    }

    @Test
    fun mark_read_changes_status_without_touching_metadata() = runBlocking {
        givenLibrary()
        val before = repository.getById("war").getOrNull()

        repository.markRead("war", ReadingStatus.READING).assertOk()

        val after = repository.getById("war").getOrNull()
        assertThat(after?.readingStatus).isEqualTo(ReadingStatus.READING)
        assertThat(after?.title).isEqualTo(before?.title)
    }

    @Test
    fun update_metadata_writes_to_overrides_and_is_visible_in_reads() = runBlocking {
        givenLibrary()

        repository.updateMetadata("war", MetadataPatch(title = "Война и мир (ред.)")).assertOk()

        assertThat(repository.getById("war").getOrNull()?.title).isEqualTo("Война и мир (ред.)")
        // Строка books не меняется: пересканирование не должно затирать правку.
        assertThat(database.bookDao().getById("war")?.title).isEqualTo("Война и мир")
    }

    @Test
    fun delete_removes_only_the_record() = runBlocking {
        givenLibrary()

        repository.delete("war").assertOk()

        assertThat(repository.getById("war").getOrNull()).isNull()
        assertThat(repository.observePage(LibraryQuery()).first().totalCount).isEqualTo(4)
    }

    private fun AppResult<Unit>.assertOk() {
        assertThat(isSuccess).isTrue()
    }

    private suspend fun givenLibrary() {
        database.sourceDao().insert(
            SourceEntity(
                id = SOURCE_ID,
                uri = "content://com.android.externalstorage.documents/tree/books",
                displayName = "Books",
                kind = SourceKind.SAF_TREE,
            ),
        )
        database.bookDao().upsertAll(
            listOf(
                book("war", "Война и мир", "Лев Толстой", series = "Классика", year = 1869, ext = "fb2"),
                book("anna", "Анна Каренина", "Лев Толстой", series = "Классика", year = 1877, ext = "epub"),
                book("crime", "Преступление и наказание", "Фёдор Достоевский", "Русская классика", 1866, "fb2"),
                book("mothers", "Матери и дети", "Максим Горький", "Советская", 1910, "pdf"),
                book("resurrection", "Воскресение", "Лев Толстой", "Русская классика", 1899, "epub"),
            ),
        )
        database.authorDao().upsertAll(
            listOf(
                AuthorEntity(id = TOLSTOY, name = "Лев Толстой", sortName = "Толстой"),
                AuthorEntity(id = DOSTOEVSKY, name = "Фёдор Достоевский", sortName = "Достоевский"),
                AuthorEntity(id = GORKY, name = "Максим Горький", sortName = "Горький"),
            ),
        )
        database.authorDao().insertBookAuthors(
            listOf(
                BookAuthorCrossRef(bookId = "war", authorId = TOLSTOY),
                BookAuthorCrossRef(bookId = "anna", authorId = TOLSTOY),
                BookAuthorCrossRef(bookId = "resurrection", authorId = TOLSTOY),
                BookAuthorCrossRef(bookId = "crime", authorId = DOSTOEVSKY),
                BookAuthorCrossRef(bookId = "mothers", authorId = GORKY),
            ),
        )
    }

    @Suppress("LongParameterList")
    private fun book(
        id: String,
        title: String,
        author: String,
        series: String? = null,
        year: Int? = null,
        ext: String,
    ) = BookEntity(
        id = id,
        sourceId = SOURCE_ID,
        documentUri = "content://tree/books/document/$id",
        relativePath = "$title.$ext",
        fileName = "$title.$ext",
        extension = ext,
        mimeType = "application/$ext",
        sizeBytes = 1000L,
        lastModified = 1000L,
        title = title,
        sortTitle = title,
        authorsText = author,
        seriesName = series,
        publishedYear = year,
        addedAt = 1000L,
        updatedAt = 1000L,
        lastSeenAt = 1000L,
    )

    private companion object {
        const val SOURCE_ID = "source-1"

        // Ключ автора — значение books.authorsText: нормализованные authors/book_authors
        // приложение не заполняет, и отбор по ним не находил ни одной книги.
        const val TOLSTOY = "Лев Толстой"
        const val DOSTOEVSKY = "Фёдор Достоевский"
        const val GORKY = "Максим Горький"
    }
}
