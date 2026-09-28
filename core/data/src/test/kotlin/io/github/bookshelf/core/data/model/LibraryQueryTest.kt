package io.github.bookshelf.core.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Тест семантики [BookQuery] (docs/architecture.md §3.5, ADR-008).
 *
 * Ключевое, что фиксируется: пустое множество — это «фильтр не задан», а не «ничего не найдено»;
 * фильтр по автору — множественный и добавляется копированием значения.
 */
class LibraryQueryTest {

    @Test
    fun default_query_is_empty() {
        assertThat(BookQuery().isEmpty).isTrue()
    }

    @Test
    fun any_filter_makes_query_not_empty() {
        assertThat(BookQuery(sourceIds = setOf("src-1")).isEmpty).isFalse()
        assertThat(BookQuery(authorNames = setOf("author-1")).isEmpty).isFalse()
        assertThat(BookQuery(series = setOf("Серия")).isEmpty).isFalse()
        assertThat(BookQuery(formats = setOf("fb2")).isEmpty).isFalse()
        assertThat(BookQuery(statuses = setOf(ReadingStatus.UNREAD)).isEmpty).isFalse()
        assertThat(BookQuery(tags = setOf("фантастика")).isEmpty).isFalse()
        assertThat(BookQuery(favoritesOnly = true).isEmpty).isFalse()
        assertThat(BookQuery(searchTerm = "мастер").isEmpty).isFalse()
    }

    @Test
    fun blank_search_term_is_not_a_filter() {
        assertThat(BookQuery(searchTerm = "   ").isEmpty).isTrue()
        assertThat(BookQuery(searchTerm = "").isEmpty).isTrue()
    }

    @Test
    fun with_author_copies_and_appends_authors() {
        val original = BookQuery(authorNames = setOf("author-1"))
        val updated = original.withAuthor("author-2", "author-3")

        assertThat(updated.authorNames).containsExactly("author-1", "author-2", "author-3")
        // Исходное значение не изменилось: BookQuery — неизменяемое значение.
        assertThat(original.authorNames).containsExactly("author-1")
        assertThat(updated).isNotSameInstanceAs(original)
    }

    @Test
    fun with_author_replaces_nothing_else() {
        val query = LibraryQuery(
            bookQuery = BookQuery(formats = setOf("epub")),
            sort = SortOrder.AUTHOR,
            descending = true,
            page = 2,
        )
        val updated = query.copy(bookQuery = query.bookQuery.withAuthor("author-1"))

        assertThat(updated.bookQuery.formats).containsExactly("epub")
        assertThat(updated.sort).isEqualTo(SortOrder.AUTHOR)
        assertThat(updated.descending).isTrue()
        assertThat(updated.page).isEqualTo(2)
    }
}
