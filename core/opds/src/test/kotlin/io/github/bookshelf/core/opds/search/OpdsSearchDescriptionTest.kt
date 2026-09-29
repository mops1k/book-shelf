package io.github.bookshelf.core.opds.search

import com.google.common.truth.Truth.assertThat
import java.io.InputStream
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Разбор дескриптора OpenSearch на настоящем файле Project Gutenberg.
 *
 * Фикстура скачана с каталога, а не выдумана: выдуманный дескриптор с одним `<Url>` не показал бы
 * главной ловушки — у Gutenberg три `<Url>` с разными типами, и `text/html` идёт первым.
 *
 * Robolectric обязателен: `android.util.Xml` вне него недоступен.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class OpdsSearchDescriptionTest {

    @Test
    fun `atom template wins over html and suggestions`() {
        val template = OpdsSearchDescription.templateOf(GUTENBERG_DESCRIPTOR)

        // Взять первый попавшийся template значит открыть HTML-страницу поиска вместо OPDS-фида.
        assertThat(template).isEqualTo("http://m.gutenberg.org/ebooks/search.opds/?query={searchTerms}")
        assertThat(template).doesNotContain("text/html")
        assertThat(template).doesNotContain("suggest")
    }

    @Test
    fun `descriptor without a template yields null`() {
        val body = """
            <?xml version="1.0" encoding="UTF-8"?>
            <OpenSearchDescription xmlns="http://a9.com/-/spec/opensearch/1.1/">
              <ShortName>Каталог без поиска</ShortName>
            </OpenSearchDescription>
        """.trimIndent()

        assertThat(OpdsSearchDescription.templateOf(body)).isNull()
    }

    @Test
    fun `any template is used when the type is mislabelled`() {
        // Каталог назвал фид как угодно, но шаблон один: лучше открыть его, чем не искать вовсе.
        val body = """
            <OpenSearchDescription>
              <Url type="application/opds+xml" template="https://host/opds?q={searchTerms}"/>
            </OpenSearchDescription>
        """.trimIndent()

        assertThat(OpdsSearchDescription.templateOf(body)).isEqualTo("https://host/opds?q={searchTerms}")
    }

    @Test
    fun `broken descriptor does not throw`() {
        // Мусор вместо дескриптора не должен ронять поиск: вызывающий код перейдёт к отбору
        // по загруженному фиду.
        assertThat(OpdsSearchDescription.templateOf("<OpenSearchDescription><Url")).isNull()
        assertThat(OpdsSearchDescription.templateOf("не xml вовсе")).isNull()
        assertThat(OpdsSearchDescription.templateOf("")).isNull()
    }

    private companion object {
        val GUTENBERG_DESCRIPTOR: String = checkNotNull(
            OpdsSearchDescriptionTest::class.java.classLoader
                ?.getResourceAsStream("gutenberg-search-description.xml"),
        ).use(InputStream::readBytes).toString(Charsets.UTF_8)
    }
}
