package io.github.bookshelf.feature.filters

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.feature.filters.ui.facetOverview
import org.junit.Test

/**
 * Живая сводка над списком разделов фильтров.
 *
 * Сводка существует из-за дефекта от 28.09.2026: пересчёт разделов шёл на каждый тап, но при
 * раскрытой секции чужие строки уезжали вниз, и число доступных серий менялось за пределами
 * экрана — выглядело как «обновляется только после Применить». Поэтому проверяется, что сводка
 * печатает все критерии, печатает именно те разделы, которые ей передали, и говорит словами:
 * раньше это была строка «Авторы 20 · Серии 14 · …», в которой число без пояснения читалось как
 * «выбрано 20 авторов» (замечание Александра, 29.09.2026).
 */
class FacetOverviewTest {

    @Test
    fun `overview names what the numbers mean and lists every criterion`() {
        val sections = listOf(
            section(FacetKind.AUTHORS, "a", "b", "c"),
            section(FacetKind.SERIES, "s"),
            section(FacetKind.TAGS, "t1", "t2"),
            section(FacetKind.FORMATS),
            section(FacetKind.STATUSES, "READING"),
        )

        assertThat(facetOverview(sections))
            .isEqualTo(
                "Значений на выбор: 3 автора · 1 серия · 2 жанра · 0 форматов · 1 статус",
            )
    }

    @Test
    fun `overview follows the narrowed facet set instead of the old one`() {
        // Тот же критерий после выбора автора: серий осталось одна, и сводка обязана это показать.
        val narrowed = listOf(
            section(FacetKind.AUTHORS, "a", "b", "c"),
            section(FacetKind.SERIES, "s"),
        )

        assertThat(facetOverview(narrowed))
            .isEqualTo("Значений на выбор: 3 автора · 1 серия")
    }

    @Test
    fun `words agree with the number`() {
        // Русские формы: 1 автор, 2 автора, 5 авторов, но 11 авторов и снова 21 автор.
        val sections = listOf(
            section(FacetKind.AUTHORS, *ids(1)),
            section(FacetKind.AUTHORS, *ids(2)),
            section(FacetKind.AUTHORS, *ids(5)),
            section(FacetKind.AUTHORS, *ids(11)),
            section(FacetKind.AUTHORS, *ids(21)),
            section(FacetKind.SERIES, *ids(22)),
            section(FacetKind.STATUSES, *ids(12)),
        )

        assertThat(facetOverview(sections)).isEqualTo(
            "Значений на выбор: 1 автор · 2 автора · 5 авторов · 11 авторов · 21 автор · " +
                "22 серии · 12 статусов",
        )
    }

    @Test
    fun `empty facet set prints nothing`() {
        // Пустой список — это экран «фильтров пока нет»: подпись «Значений на выбор:» без чисел
        // выглядела бы как обрезанная строка.
        assertThat(facetOverview(emptyList())).isEmpty()
    }

    private fun section(kind: FacetKind, vararg ids: String) = FacetSectionUi(
        kind = kind,
        title = kind.name,
        items = ids.map { id -> FacetChipUi(id = id, label = id, count = 1) },
    )

    private fun ids(count: Int): Array<String> = Array(count) { index -> "id-$index" }
}
