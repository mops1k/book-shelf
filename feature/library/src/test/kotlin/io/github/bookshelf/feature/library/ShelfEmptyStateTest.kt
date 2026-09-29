package io.github.bookshelf.feature.library

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Пустое состояние полки выбирается по причине пустоты, а не по самому факту.
 *
 * Дефект от 28.09.2026: при фильтре, который ничего не нашёл, полка предлагала «Сканировать» —
 * хотя книги в библиотеке есть и терять им нечего. Здесь проверяется выбор ветки, чтобы подсказка
 * снова не склеилась в одну.
 */
class ShelfEmptyStateTest {

    @Test
    fun `books on the shelf have no empty state`() {
        val state = LibraryUiState(
            books = listOf(BookCardUi(id = "1", title = "Флинкс", authors = "Алан Дин Фостер")),
            activeFilters = FilterChipsUi(authors = listOf("Алан Дин Фостер")),
        )

        assertThat(shelfEmptyState(state)).isNull()
    }

    @Test
    fun `empty shelf under filters reports that nothing was found`() {
        val state = LibraryUiState(
            activeFilters = FilterChipsUi(authors = listOf("Алан Дин Фостер"), tags = listOf("sf_space")),
        )

        assertThat(shelfEmptyState(state)).isEqualTo(ShelfEmptyState.FILTERED_OUT)
    }

    @Test
    fun `empty shelf without filters asks for a scan`() {
        assertThat(shelfEmptyState(LibraryUiState())).isEqualTo(ShelfEmptyState.NO_BOOKS)
    }
}
