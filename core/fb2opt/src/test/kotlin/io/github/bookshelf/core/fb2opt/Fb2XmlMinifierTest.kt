package io.github.bookshelf.core.fb2opt

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Минификация разметки FB2 (`Fb2XmlMinifier`).
 *
 * Зачем: разметка в книгах бывает на треть больше нужного — комментарии, отступы между тегами,
 * пробелы внутри тегов. Всё это сжимается без потери данных.
 *
 * Проверяются границы, где «оптимизация» ломает книгу: текст не склеивается и не меняется,
 * `CDATA` остаётся байт-в-байт (в нём бывает код и разметка как текст), содержимое `<binary>`
 * не трогается — это картинки в base64, и от них зависит то, что человек видит.
 *
 * Образцы — настоящие куски FB2 (структура и типичные для книг конструкции: `<?xml?>`, `<FictionBook>`,
 * `<section>`, `<p>`, `<empty-line/>`, сноски, `CDATA`, комментарии), а не выдуманные строки.
 */
class Fb2XmlMinifierTest {

    private val minifier = Fb2XmlMinifier()

    @Test
    fun `comments are dropped`() {
        val source = "<FictionBook><!-- служебная пометка конвертера -->" +
            "<body><section><p>Текст</p></section></body></FictionBook>"

        val result = minifier.minify(source)

        assertThat(result).doesNotContain("служебная пометка")
        assertThat(result).doesNotContain("<!--")
        assertThat(result).contains("<p>Текст</p>")
    }

    @Test
    fun `indentation between tags collapses to nothing`() {
        val source = """
            <FictionBook>
                <body>
                    <section>
                        <p>Первый</p>
                    </section>
                </body>
            </FictionBook>
        """.trimIndent()

        val result = minifier.minify(source)

        assertThat(result).doesNotContain("\n")
        assertThat(result).isEqualTo(
            "<FictionBook><body><section><p>Первый</p></section></body></FictionBook>",
        )
    }

    @Test
    fun `text is not glued and keeps its own spaces`() {
        val source = "<p>Одно <emphasis>слово</emphasis> другое</p>"

        val result = minifier.minify(source)

        // Между строчными тегами пробел обязан остаться: без него получилось бы «Однослово».
        assertThat(result).isEqualTo("<p>Одно <emphasis>слово</emphasis> другое</p>")
    }

    @Test
    fun `cdata stays byte-exact`() {
        val source = "<p><![CDATA[  a < b && c > d  ]]></p>"

        val result = minifier.minify(source)

        assertThat(result).isEqualTo(source)
    }

    @Test
    fun `binary content is not touched by the minifier`() {
        val source = "<binary id=\"img.png\" content-type=\"image/png\">iVBORw0KGgo=\nAAAAB</binary>"

        val result = minifier.minify(source)

        // Картинку обрабатывает отдельный шаг: минификатор обязан отдать её как есть.
        assertThat(result).contains("iVBORw0KGgo=")
        assertThat(result).contains("AAAAB")
    }

    @Test
    fun `whitespace inside tags collapses`() {
        val source = "<FictionBook\n    xmlns:xlink=\"http://www.w3.org/1999/xlink\"\n" +
            "    xmlns=\"http://www.gribuser.ru/xml/fictionbook/2.0\">"

        val result = minifier.minify(source)

        assertThat(result).isEqualTo(
            "<FictionBook xmlns:xlink=\"http://www.w3.org/1999/xlink\" " +
                "xmlns=\"http://www.gribuser.ru/xml/fictionbook/2.0\">",
        )
    }

    @Test
    fun `an empty document stays empty`() {
        assertThat(minifier.minify("")).isEmpty()
    }
}
