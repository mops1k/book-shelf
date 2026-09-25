package io.github.bookshelf.feature.filters

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Тесты чистого выбора фасетов: счёт активных фильтров и переключение значений.
 */
class FilterChipStateTest {

    @Test
    fun `empty selection has no active filters`() {
        val selection = FilterSelectionUi()

        assertThat(activeFilterCount(selection)).isEqualTo(0)
        assertThat(hasActiveFilters(selection)).isFalse()
    }

    @Test
    fun `active filter count sums every facet`() {
        val selection = FilterSelectionUi(
            authors = setOf("author-1", "author-2"),
            series = setOf("series-1"),
            formats = setOf("FB2"),
            statuses = setOf("READING"),
            tags = setOf("tag-1", "tag-2", "tag-3"),
        )

        assertThat(activeFilterCount(selection)).isEqualTo(8)
        assertThat(hasActiveFilters(selection)).isTrue()
    }

    @Test
    fun `toggle adds a value that was not selected`() {
        val authors = emptySet<String>().toggle("author-1")

        assertThat(authors).containsExactly("author-1")
    }

    @Test
    fun `toggle removes a value that was already selected`() {
        val authors = setOf("author-1", "author-2").toggle("author-1")

        assertThat(authors).containsExactly("author-2")
    }
}
