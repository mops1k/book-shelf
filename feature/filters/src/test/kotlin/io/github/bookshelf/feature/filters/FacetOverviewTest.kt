package io.github.bookshelf.feature.filters

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.feature.filters.ui.facetOverview
import org.junit.Test

/**
 * Живая сводка фасетов над списком.
 *
 * Сводка существует из-за дефекта от 28.09.2026: пересчёт фасетов шёл на каждый тап, но при
 * раскрытой секции чужие строки уезжали вниз, и число доступных серий менялось за пределами
 * экрана — выглядело как «обновляется только после Применить». Поэтому проверяется, что сводка
 * печатает все пять критериев и печатает именно те фасеты, которые ей передали.
 */
class FacetOverviewTest {

    @Test
    fun `overview lists every criterion with its available values`() {
        val sections = listOf(
            section(FacetKind.AUTHORS, "a", "b", "c"),
            section(FacetKind.SERIES, "s"),
            section(FacetKind.TAGS, "t1", "t2"),
            section(FacetKind.FORMATS),
            section(FacetKind.STATUSES, "READING"),
        )

        assertThat(facetOverview(sections))
            .isEqualTo("Авторы 3 · Серии 1 · Жанры 2 · Форматы 0 · Статус 1")
    }

    @Test
    fun `overview follows the narrowed facet set instead of the old one`() {
        // Тот же критерий после выбора автора: серий осталось одна, и сводка обязана это показать.
        val narrowed = listOf(
            section(FacetKind.AUTHORS, "a", "b", "c"),
            section(FacetKind.SERIES, "s"),
        )

        assertThat(facetOverview(narrowed)).isEqualTo("Авторы 3 · Серии 1")
    }

    private fun section(kind: FacetKind, vararg ids: String) = FacetSectionUi(
        kind = kind,
        title = kind.name,
        items = ids.map { id -> FacetChipUi(id = id, label = id, count = 1) },
    )
}
