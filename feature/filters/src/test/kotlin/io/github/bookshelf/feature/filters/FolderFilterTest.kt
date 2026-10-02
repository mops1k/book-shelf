package io.github.bookshelf.feature.filters

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.data.model.BookQuery
import io.github.bookshelf.core.data.model.FacetItem
import io.github.bookshelf.core.data.model.FacetSet
import org.junit.Test

/**
 * Критерий «папки»: выбор источника и обратный перенос в запрос полки.
 *
 * Дефект, ради которого критерий появился: `BookQuery.sourceIds` в слое данных был (полка умела
 * фильтровать по источнику), а в интерфейсе выбрать папку было нельзя — условие существовало
 * и не использовалось. Здесь проверяется то, что легко разъезжается: выбор → запрос и запрос →
 * выбор при возврате на экран, а также то, что папки НЕ попадают в правила умной полки.
 */
class FolderFilterTest {

    @Test
    fun `a chosen folder becomes the source filter of the query`() {
        val query = FilterSelectionUi(sources = setOf("sd-card")).toBookQuery()

        assertThat(query.sourceIds).containsExactly("sd-card")
    }

    @Test
    fun `an applied query restores the chosen folders`() {
        val selection = filterSelectionFromQuery(BookQuery(sourceIds = setOf("internal", "sd-card")))

        assertThat(selection.sources).containsExactly("internal", "sd-card")
    }

    @Test
    fun `a query with folders is not empty`() {
        // Иначе полка считала бы выбор пустым и показала всю библиотеку вместо одной папки.
        assertThat(BookQuery(sourceIds = setOf("sd-card")).isEmptyQuery()).isFalse()
    }

    @Test
    fun `folders are not offered in smart shelf rules`() {
        val sections = FacetSet(
            sources = listOf(FacetItem(id = "sd-card", labelCode = "SD", count = 3)),
            tags = listOf(FacetItem(id = "sf_space", labelCode = "sf_space", count = 1)),
        ).toSectionsUi(kinds = FacetKind.entries.toSet() - FacetKind.SOURCES)

        assertThat(sections.map { it.kind }).doesNotContain(FacetKind.SOURCES)
    }

    @Test
    fun `folders are offered on the filters screen`() {
        val sections = FacetSet(
            sources = listOf(FacetItem(id = "sd-card", labelCode = "SD", count = 3)),
        ).toSectionsUi()

        assertThat(sections.map { it.kind }).containsExactly(FacetKind.SOURCES)
        assertThat(sections.single().items.single().labelCode).isEqualTo("SD")
    }
}
