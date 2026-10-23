package io.github.bookshelf.core.data.repository

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.getOrNull
import io.github.bookshelf.core.common.error.isFailure
import io.github.bookshelf.core.data.model.BookQuery
import io.github.bookshelf.core.data.model.Grouping
import io.github.bookshelf.core.data.model.LibraryQuery
import io.github.bookshelf.core.data.model.ReadingStatus
import io.github.bookshelf.core.data.model.SortOrder
import io.github.bookshelf.core.database.entity.ReadingProgressEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Чтение полки: фильтры, сортировка, страницы, группировка и одна книга по id.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class BookRepositoryQueryTest : BookRepositoryTestBase() {
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

        // Внутри одной серии порядок — по выпуску: «Война и мир» (1869) раньше «Анны Карениной»
        // (1877). Номера в серии у этих книг нет, поэтому решает год издания.
        assertThat(page.items.map { it.title }).isEqualTo(listOf("Война и мир", "Анна Каренина"))
    }

    @Test
    fun combines_different_facets_with_and_semantics() = runBlocking {
        givenLibrary()

        val page = repository.observePage(
            LibraryQuery(bookQuery = BookQuery(authorNames = setOf(TOLSTOY), series = setOf("Классика"))),
        ).first()

        // Внутри одной серии порядок — по выпуску: «Война и мир» (1869) раньше «Анны Карениной»
        // (1877). Номера в серии у этих книг нет, поэтому решает год издания.
        assertThat(page.items.map { it.title }).isEqualTo(listOf("Война и мир", "Анна Каренина"))
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
}
