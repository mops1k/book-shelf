package io.github.bookshelf.feature.filters

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.feature.filters.ui.facetOverviewCounts
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
 *
 * Текст сводки живёт в ресурсах (`filters_overview`), поэтому здесь проверяется состав и порядок
 * критериев, а формы слов («1 автор», «2 автора», «5 авторов», «11 авторов») — в `FacetPluralsTest`
 * на самих ресурсах.
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

        assertThat(facetOverviewCounts(sections)).containsExactly(
            FacetKind.AUTHORS to 3,
            FacetKind.SERIES to 1,
            FacetKind.TAGS to 2,
            FacetKind.FORMATS to 0,
            FacetKind.STATUSES to 1,
        ).inOrder()
    }

    @Test
    fun `overview follows the narrowed facet set instead of the old one`() {
        // Тот же критерий после выбора автора: серий осталось одна, и сводка обязана это показать.
        val narrowed = listOf(
            section(FacetKind.AUTHORS, "a", "b", "c"),
            section(FacetKind.SERIES, "s"),
        )

        assertThat(facetOverviewCounts(narrowed)).containsExactly(
            FacetKind.AUTHORS to 3,
            FacetKind.SERIES to 1,
        ).inOrder()
    }

    @Test
    fun `empty facet set prints nothing`() {
        // Пустой список — это экран «фильтров пока нет»: подпись «Значений на выбор:» без чисел
        // выглядела бы как обрезанная строка.
        assertThat(facetOverviewCounts(emptyList())).isEmpty()
    }

    private fun section(kind: FacetKind, vararg ids: String) = FacetSectionUi(
        kind = kind,
        titleRes = R.string.filters_section_authors,
        items = ids.map { id -> FacetChipUi(id = id, labelCode = id, count = 1) },
    )
}
