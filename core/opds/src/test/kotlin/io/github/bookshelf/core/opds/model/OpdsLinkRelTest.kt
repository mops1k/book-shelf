package io.github.bookshelf.core.opds.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Тесты распознавания `rel`-ссылок OPDS и выбора лучшей ссылки приобретения.
 *
 * Это единственная пока реализованная логика модуля: от неё зависит и навигация по каталогу,
 * и то, что именно скачает `DownloadWorker`. Ошибка здесь стоит дорого (скачали «sample» вместо
 * книги или открыли файл книги как раздел), поэтому поведение зафиксировано тестами.
 */
class OpdsLinkRelTest {

    private fun link(
        rel: String,
        type: String? = null,
        href: String = "https://catalog.example.org/books/1.epub",
    ): OpdsLink = OpdsLink(rel = rel, href = href, type = type)

    @Test
    fun acquisition_covers_all_spec_variants() {
        val rels = listOf(
            "http://opds-spec.org/acquisition",
            "http://opds-spec.org/acquisition/open-access",
            "http://opds-spec.org/acquisition/buy",
            "http://opds-spec.org/acquisition/borrow",
            "http://opds-spec.org/acquisition/sample",
        )

        rels.forEach { rel ->
            assertThat(link(rel).isAcquisition()).isTrue()
        }
    }

    @Test
    fun alternate_is_not_acquisition() {
        assertThat(link("alternate").isAcquisition()).isFalse()
    }

    @Test
    fun image_covers_full_and_thumbnail() {
        assertThat(link("http://opds-spec.org/image").isImage()).isTrue()
        assertThat(link("http://opds-spec.org/image/thumbnail").isImage()).isTrue()
    }

    @Test
    fun acquisition_is_not_image() {
        assertThat(link("http://opds-spec.org/acquisition").isImage()).isFalse()
    }

    @Test
    fun navigation_is_detected_by_rel_subsection() {
        assertThat(link("subsection", type = "application/atom+xml;kind=navigation").isNavigation())
            .isTrue()
    }

    @Test
    fun navigation_is_detected_by_type_kind() {
        val navigation = link(
            rel = "http://opds-spec.org/sort/new",
            type = "application/atom+xml;profile=opds-catalog;kind=navigation",
        )

        assertThat(navigation.isNavigation()).isTrue()
    }

    @Test
    fun acquisition_link_is_not_navigation() {
        assertThat(link("http://opds-spec.org/acquisition").isNavigation()).isFalse()
    }

    @Test
    fun search_is_detected_by_rel_fragment() {
        assertThat(link("http://opds-spec.org/opensearch").isSearch()).isTrue()
    }

    @Test
    fun search_is_detected_by_opensearch_description_type() {
        val search = link(rel = "search", type = "application/opensearchdescription+xml")

        assertThat(search.isSearch()).isTrue()
    }

    @Test
    fun best_acquisition_prefers_mime_order() {
        val epub = link("http://opds-spec.org/acquisition", type = "application/epub+zip")
        val pdf = link("http://opds-spec.org/acquisition", type = "application/pdf")
        val entry = OpdsEntry(id = "1", title = "Book", links = listOf(pdf, epub))

        val best = entry.bestAcquisitionLink(
            preferredMimeTypes = listOf("application/epub+zip", "application/pdf"),
        )

        assertThat(best).isEqualTo(epub)
    }

    @Test
    fun best_acquisition_falls_back_to_first_acquisition_link() {
        val alternate = link("alternate")
        val acquisition = link("http://opds-spec.org/acquisition/buy", type = "application/pdf")
        val entry = OpdsEntry(id = "1", title = "Book", links = listOf(alternate, acquisition))

        val best = entry.bestAcquisitionLink(preferredMimeTypes = listOf("application/epub+zip"))

        assertThat(best).isEqualTo(acquisition)
    }

    @Test
    fun best_acquisition_is_null_without_acquisition_links() {
        val entry = OpdsEntry(
            id = "1",
            title = "Book",
            links = listOf(link("alternate"), link("http://opds-spec.org/image")),
        )

        assertThat(entry.bestAcquisitionLink(preferredMimeTypes = emptyList())).isNull()
    }
}
