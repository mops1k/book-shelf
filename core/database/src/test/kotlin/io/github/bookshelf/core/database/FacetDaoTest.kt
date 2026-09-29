package io.github.bookshelf.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.database.dao.FacetDao
import io.github.bookshelf.core.database.entity.BookTagCrossRef
import io.github.bookshelf.core.database.entity.SourceEntity
import io.github.bookshelf.core.database.entity.TagEntity
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
 * Авторские фасеты и серии автора для обзора полки.
 *
 * Дефект, ради которого появились тесты (найден на устройстве 2026-09-28): таблицы `authors` и
 * `book_authors` приложение не заполняет вовсе (0 строк при 72 книгах), поэтому прежние фасеты
 * авторов (JOIN по этим таблицам) были всегда пустыми, а фильтр по автору не находил ни одной
 * книги. Тесты намеренно НЕ создают ни одной строки в `authors`/`book_authors`: источником истины
 * для автора объявлено денормализованное `books.authorsText`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class FacetDaoTest {

    private lateinit var database: BookshelfDatabase
    private lateinit var facets: FacetDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BookshelfDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        facets = database.facetDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun author_facets_come_from_denormalized_authors_text() = runBlocking {
        givenBooks()

        val authors = facets.observeAuthors(authorsQuery()).first()

        assertThat(authors.map { it.name }).containsExactly("Лев Толстой", "Фёдор Достоевский")
        assertThat(authors.first { it.name == "Лев Толстой" }.bookCount).isEqualTo(3)
        assertThat(authors.first { it.name == "Фёдор Достоевский" }.bookCount).isEqualTo(1)
    }

    @Test
    fun series_of_author_are_counted_and_books_without_series_form_a_separate_bucket() = runBlocking {
        givenBooks()

        val rows = facets.observeSeriesOfAuthor("Лев Толстой").first()

        val series = rows.filter { !it.seriesName.isNullOrBlank() }
        assertThat(series.map { it.seriesName }).containsExactly("Классика")
        assertThat(series.single().bookCount).isEqualTo(2)
        // «Воскресение» — книга Толстого без серии: она обязана попасть в отдельный бакет,
        // иначе из обзора по авторам её не найти.
        assertThat(rows.firstOrNull { it.seriesName == null }?.bookCount).isEqualTo(1)
    }

    @Test
    fun series_of_another_author_are_not_mixed_in() = runBlocking {
        givenBooks()

        val rows = facets.observeSeriesOfAuthor("Фёдор Достоевский").first()

        assertThat(rows.map { it.seriesName }).containsExactly("Русская классика")
        assertThat(rows.single().bookCount).isEqualTo(1)
    }


    @Test
    fun `genre facet counts books and survives books without genres`() = runBlocking {
        givenBooks()
        database.tagDao().upsertAll(
            listOf(
                TagEntity(id = "tag:фэнтези", name = "фэнтези"),
                TagEntity(id = "tag:детектив", name = "детектив"),
            ),
        )
        database.tagDao().insertBookTags(
            listOf(
                BookTagCrossRef(bookId = "war", tagId = "tag:фэнтези"),
                BookTagCrossRef(bookId = "anna", tagId = "tag:фэнтези"),
                BookTagCrossRef(bookId = "crime", tagId = "tag:детектив"),
            ),
        )

        val tags = facets.observeTags(tagsQuery()).first()

        // Жанры считаются по связям book_tags: до появления фасета теги не были видны вообще,
        // и умная полка «по жанру» не могла наполниться.
        assertThat(tags.map { it.name }).containsExactly("фэнтези", "детектив")
        assertThat(tags.first { it.name == "фэнтези" }.bookCount).isEqualTo(2)
        assertThat(tags.first { it.name == "детектив" }.bookCount).isEqualTo(1)
    }


    /** Фасет авторов без фильтров: тест проверяет счётчики, а не предикат. */
    private fun authorsQuery() = androidx.sqlite.db.SimpleSQLiteQuery(
        "SELECT books.authorsText AS name, COUNT(*) AS bookCount FROM books " +
            "WHERE books.authorsText <> '' " +
            "GROUP BY books.authorsText ORDER BY books.authorsText COLLATE NOCASE ASC",
    )

    /** Фасет жанров без фильтров: считает книги по связям book_tags. */
    private fun tagsQuery() = androidx.sqlite.db.SimpleSQLiteQuery(
        "SELECT tags.name AS name, COUNT(book_tags.bookId) AS bookCount FROM tags " +
            "INNER JOIN book_tags ON book_tags.tagId = tags.id " +
            "GROUP BY tags.id, tags.name ORDER BY bookCount DESC, tags.name COLLATE NOCASE ASC",
    )

    private suspend fun givenBooks() {
        database.sourceDao().insert(SOURCE)
        database.bookDao().upsertAll(
            listOf(
                BookDaoTestFixtures.book(
                    id = "war",
                    sourceId = SOURCE_ID,
                    title = "Война и мир",
                    author = TOLSTOY,
                    series = "Классика",
                ),
                BookDaoTestFixtures.book(
                    id = "anna",
                    sourceId = SOURCE_ID,
                    title = "Анна Каренина",
                    author = TOLSTOY,
                    series = "Классика",
                ),
                BookDaoTestFixtures.book(
                    id = "resurrection",
                    sourceId = SOURCE_ID,
                    title = "Воскресение",
                    author = TOLSTOY,
                ),
                BookDaoTestFixtures.book(
                    id = "crime",
                    sourceId = SOURCE_ID,
                    title = "Преступление и наказание",
                    author = DOSTOEVSKY,
                    series = "Русская классика",
                ),
            ),
        )
    }

    private companion object {
        const val SOURCE_ID = "source-1"
        const val TOLSTOY = "Лев Толстой"
        const val DOSTOEVSKY = "Фёдор Достоевский"

        val SOURCE = SourceEntity(
            id = SOURCE_ID,
            uri = "content://com.android.externalstorage.documents/tree/books",
            displayName = "Books",
            kind = SourceKind.SAF_TREE,
        )
    }
}
