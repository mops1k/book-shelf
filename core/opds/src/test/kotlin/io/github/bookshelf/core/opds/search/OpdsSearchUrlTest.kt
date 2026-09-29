package io.github.bookshelf.core.opds.search

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Подстановка запроса в шаблон OpenSearch.
 *
 * Проверяется то, от чего зависит результат поиска на живом каталоге: запрос реально попадает
 * в адрес, кодируется (кириллица, пробелы, `&`) и не портит остальные параметры шаблона.
 */
class OpdsSearchUrlTest {

    @Test
    fun `query is substituted into the template`() {
        val url = OpdsSearchUrl.build(FLIBUSTA, "Лукьяненко")

        val encoded = "%D0%9B%D1%83%D0%BA%D1%8C%D1%8F%D0%BD%D0%B5%D0%BD%D0%BA%D0%BE"
        assertThat(url).isEqualTo("https://host/opds/search?searchTerm=$encoded")
        assertThat(url).doesNotContain(OpdsSearchUrl.TEMPLATE_MARKER)
    }

    @Test
    fun `space becomes percent20 not plus`() {
        // «+» в пути означает литеральный плюс, поэтому пробел кодируем как %20: он верен и в
        // параметре, и в сегменте пути.
        val url = OpdsSearchUrl.build(FLIBUSTA, "Алан Дин Фостер")

        assertThat(url).contains("%20")
        assertThat(url).doesNotContain("+")
    }

    @Test
    fun `rest of the template survives`() {
        val template = "https://host/opds/search?searchTerm={searchTerms}&sort=new&page=1"

        assertThat(OpdsSearchUrl.build(template, "Лем"))
            .isEqualTo("https://host/opds/search?searchTerm=%D0%9B%D0%B5%D0%BC&sort=new&page=1")
    }

    @Test
    fun `query is trimmed`() {
        assertThat(OpdsSearchUrl.build(FLIBUSTA, "  Лем  "))
            .isEqualTo("https://host/opds/search?searchTerm=%D0%9B%D0%B5%D0%BC")
    }

    @Test
    fun `ampersand and plus in the query are encoded`() {
        // Иначе «&» разрезал бы адрес на лишний параметр, а «+» превратился бы в пробел.
        val url = OpdsSearchUrl.build(FLIBUSTA, "Том & Джерри+1")

        assertThat(url).contains("%26")
        assertThat(url).contains("%2B")
        assertThat(url).doesNotContain(" & ")
    }

    @Test
    fun `blank query has nothing to search`() {
        assertThat(OpdsSearchUrl.build(FLIBUSTA, "   ")).isNull()
    }

    @Test
    fun `template without marker is not a search template`() {
        // Так выглядит дескриптор OpenSearch: подставлять в него запрос некуда, и молча отправить
        // его как запрос значит показать пользователю пустой список вместо объяснения.
        assertThat(OpdsSearchUrl.build("https://host/opensearch.xml", "Лем")).isNull()
    }

    private companion object {
        /** Реальный вид ссылки Флибусты (см. фикстуру flibusta-root.xml). */
        const val FLIBUSTA = "https://host/opds/search?searchTerm={searchTerms}"
    }
}
