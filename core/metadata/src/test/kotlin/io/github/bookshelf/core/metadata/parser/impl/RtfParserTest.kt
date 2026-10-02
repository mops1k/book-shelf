package io.github.bookshelf.core.metadata.parser.impl

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.metadata.model.MetadataOrigin
import io.github.bookshelf.core.metadata.parser.BookInput
import io.github.bookshelf.core.metadata.parser.ParseFailureKind
import io.github.bookshelf.core.metadata.parser.ParseResult
import java.io.ByteArrayInputStream
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * RTF — текст с группами `{\info …}` («Форматы» §7).
 *
 * Дефект, ради которого парсер реализован: формат распознавался, но не разбирался — книга
 * получала заголовок из имени файла и пометку «формат не поддерживается», хотя `\info` с
 * заголовком, автором и годом лежит в первых сотнях байт файла.
 */
class RtfParserTest {

    @Test
    fun `info gives title, author, year and language`() = runTest {
        val result = RtfParser().parse(input(rtf(BOOK)))

        val success = result as ParseResult.Success
        assertThat(success.metadata.title).isEqualTo("Война и мир")
        assertThat(success.metadata.authors).containsExactly("Лев Толстой")
        assertThat(success.metadata.description).isEqualTo("Роман-эпопея")
        assertThat(success.metadata.publishedYear).isEqualTo(1869)
        // `\lang1049` — русский: в карточке нужен код языка, а не номер LANGID.
        assertThat(success.metadata.language).isEqualTo("ru")
        assertThat(success.metadata.source).isEqualTo(MetadataOrigin.EMBEDDED)
        assertThat(success.cover).isNull()
    }

    @Test
    fun `unicode escapes are decoded with the skip character`() = runTest {
        // `\uN?` — так RTF пишет символы вне текущей кодовой страницы: `?` после числа
        // отбрасывается (столько символов задаёт `\uc`), иначе в заголовке появились бы вопросы.
        val body = """{\rtf1\ansi\deff0{\info{\title \u1042?\u1086?\u1081?\u1085?\u1072?}}Текст}"""

        val result = RtfParser().parse(input(rtf(body))) as ParseResult.Success

        assertThat(result.metadata.title).isEqualTo("Война")
    }

    @Test
    fun `escaped braces and backslashes are decoded`() = runTest {
        // Кириллица в RTF приходит эскейпами (`\'hh` или `\uN`), поэтому «живые» не-ASCII
        // символы в этой проверке не нужны: она про скобки и обратный слэш.
        val body = """{\rtf1\ansi{\info{\title \{Braces\} and \\ slash}}Text}"""

        val result = RtfParser().parse(input(rtf(body))) as ParseResult.Success

        assertThat(result.metadata.title).isEqualTo("{Braces} and \\ slash")
    }

    @Test
    fun `without an info group the title comes from the file name`() = runTest {
        val body = """{\rtf1\ansi\deff0 Только текст, метаданных нет}"""

        val result = RtfParser().parse(input(rtf(body), fileName = "Черновик.rtf"))

        // `\info` — необязательная группа: её отсутствие не ошибка, а частичный разбор.
        assertThat(result).isInstanceOf(ParseResult.Partial::class.java)
        val partial = result as ParseResult.Partial
        assertThat(partial.metadata.title).isEqualTo("Черновик")
        assertThat(partial.metadata.source).isEqualTo(MetadataOrigin.FILENAME)
    }

    @Test
    fun `a file that is not rtf is reported as corrupt`() = runTest {
        val result = RtfParser().parse(input("обычный текст без разметки".toByteArray()))

        assertThat((result as ParseResult.Failed).kind).isEqualTo(ParseFailureKind.CORRUPT_FILE)
    }

    @Test
    fun `an empty file is reported as empty`() = runTest {
        val result = RtfParser().parse(input(ByteArray(0)))

        assertThat((result as ParseResult.Failed).kind).isEqualTo(ParseFailureKind.EMPTY_FILE)
    }

    private fun rtf(body: String): ByteArray = body.toByteArray(Charsets.ISO_8859_1)

    private fun input(bytes: ByteArray, fileName: String = "Война и мир.rtf") = BookInput(
        fileName = fileName,
        extension = "rtf",
        mimeType = null,
        sizeBytes = bytes.size.toLong(),
        openStream = { ByteArrayInputStream(bytes) },
        openSeekable = { null },
        bytes = { offset, length ->
            bytes.copyOfRange(offset.toInt(), (offset + length).toInt().coerceAtMost(bytes.size))
        },
    )

    private companion object {
        /** Кодировка объявлена как windows-1251, поэтому кириллица идёт байтами `\'hh`. */
        val BOOK = """
            {\rtf1\ansi\ansicpg1251\deff0\lang1049
            {\fonttbl{\f0\fnil Times New Roman;}}
            {\info
            {\title \'c2\'ee\'e9\'ed\'e0 \'e8 \'ec\'e8\'f0}
            {\author \'cb\'e5\'e2 \'d2\'ee\'eb\'f1\'f2\'ee\'e9}
            {\subject \'d0\'ee\'ec\'e0\'ed-\'fd\'ef\'ee\'ef\'e5\'ff}
            {\creatim\yr1869\mo1\dy1}
            }
            \pard \'d2\'e5\'ea\'f1\'f2 \par
            }
        """.trimIndent()
    }
}
