package io.github.bookshelf.core.metadata.parser

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.metadata.model.MetadataOrigin
import io.github.bookshelf.core.metadata.parser.impl.Fb2Parser
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.Charset
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Проверки FB2-разбора на настоящих ZIP/XML-фикстурах.
 *
 * Фикстуры создаются во временной папке прямо в тесте: бинарные файлы в репозитории не нужны,
 * а кодировку и состав ZIP видно рядом с проверяемым XML.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class Fb2ParserTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val parser = Fb2Parser()

    @Test
    fun supports_fb2_but_not_txt() {
        assertThat(parser.supports("fb2", null)).isTrue()
        assertThat(parser.supports("txt", null)).isFalse()
    }

    @Test
    fun parses_utf8_metadata_and_multiple_authors() = runBlocking {
        val file = temporaryFolder.newFile("book.fb2")
        file.writeBytes(UTF8_XML.toByteArray(Charsets.UTF_8))

        val result = parser.parse(input(file, "fb2"))

        val success = result as ParseResult.Success
        assertThat(success.metadata.title).isEqualTo("Три товарища")
        assertThat(success.metadata.authors).isEqualTo(listOf("Антон Чехов", "Мария Иванова"))
        assertThat(success.metadata.description).isEqualTo("Описание книги")
        assertThat(success.metadata.language).isEqualTo("ru")
        assertThat(success.metadata.seriesName).isEqualTo("Приключения")
        assertThat(success.metadata.seriesIndex).isEqualTo(1.5)
        assertThat(success.metadata.publishedYear).isEqualTo(1869)
        assertThat(success.metadata.publisher).isEqualTo("Издательство")
        assertThat(success.metadata.isbn).isEqualTo("978-5-01")
        assertThat(success.metadata.tags).isEqualTo(listOf("роман", "классика"))
        assertThat(success.metadata.source).isEqualTo(MetadataOrigin.EMBEDDED)
        assertThat(success.cover).isNull()
    }

    @Test
    fun parses_windows_1251_without_damaging_cyrillic() = runBlocking {
        val file = temporaryFolder.newFile("legacy.fb2")
        file.writeBytes(CP1251_XML.toByteArray(WINDOWS_1251))

        val result = parser.parse(input(file, "fb2"))

        val success = result as ParseResult.Success
        assertThat(success.metadata.title).isEqualTo("Старый дом")
        assertThat(success.metadata.authors).isEqualTo(listOf("Иван Петров"))
        assertThat(success.metadata.seriesName).isNull()
        assertThat(success.metadata.seriesIndex).isNull()
    }

    @Test
    fun parses_fb2_zip_case_insensitively() = runBlocking {
        val archive = createZip(
            "BOOK.FB2" to UTF8_XML.toByteArray(Charsets.UTF_8),
            "README.txt" to "not a book".toByteArray(Charsets.UTF_8),
        )
        val input = input(archive, "fb2.zip")

        val result = parser.parse(input)

        val success = result as ParseResult.Success
        assertThat(success.metadata.title).isEqualTo("Три товарища")
    }

    @Test
    fun recognizes_zip_by_magic_when_extension_is_plain_fb2() = runBlocking {
        val archive = createZip("book.fb2" to UTF8_XML.toByteArray(Charsets.UTF_8))

        val result = parser.parse(input(archive, "fb2"))

        val success = result as ParseResult.Success
        assertThat(success.metadata.title).isEqualTo("Три товарища")
    }

    @Test
    fun extracts_embedded_cover_and_ignores_missing_cover() = runBlocking {
        val withCover = temporaryFolder.newFile("cover.fb2")
        withCover.writeBytes(FB2_WITH_COVER.toByteArray(Charsets.UTF_8))

        val result = parser.parse(input(withCover, "fb2"))

        val success = result as ParseResult.Success
        assertThat(success.cover).isNotNull()
        assertThat(success.cover?.bytes?.size).isEqualTo(5)
        assertThat(success.cover?.mimeType).isEqualTo("image/jpeg")
    }

    @Test
    fun oversized_cover_does_not_turn_successful_metadata_into_failure() = runBlocking {
        val payload = "A".repeat(3_000_000)
        val file = temporaryFolder.newFile("oversized-cover.fb2")
        file.writeBytes(FB2_WITH_COVER.replace("aGVsbG8=", payload).toByteArray(Charsets.UTF_8))

        val result = parser.parse(input(file, "fb2"))

        val success = result as ParseResult.Success
        assertThat(success.cover).isNull()
    }

    @Test
    fun returns_success_without_cover_when_binary_is_absent() = runBlocking {
        val file = temporaryFolder.newFile("without-cover.fb2")
        file.writeBytes(UTF8_XML.toByteArray(Charsets.UTF_8))

        val result = parser.parse(input(file, "fb2"))

        val success = result as ParseResult.Success
        assertThat(success.cover).isNull()
    }

    @Test
    fun malformed_xml_is_failed_instead_of_thrown() = runBlocking {
        val file = temporaryFolder.newFile("broken.fb2")
        file.writeBytes("<FictionBook><description>".toByteArray(Charsets.UTF_8))

        val result = parser.parse(input(file, "fb2"))

        val failed = result as ParseResult.Failed
        assertThat(failed.reason).contains("XML")
    }

    @Test
    fun empty_fb2_is_failed() = runBlocking {
        val file = temporaryFolder.newFile("empty.fb2")
        file.writeBytes(ByteArray(0))

        val result = parser.parse(input(file, "fb2"))

        val failed = result as ParseResult.Failed
        assertThat(failed.reason).contains("Пустой")
    }

    @Test
    fun zip_without_fb2_entry_is_failed_with_diagnostic_reason() = runBlocking {
        val archive = createZip("readme.txt" to "Это не FB2".toByteArray(WINDOWS_1251))

        val result = parser.parse(input(archive, "fb2.zip"))

        val failed = result as ParseResult.Failed
        assertThat(failed.reason).contains("fb2")
    }

    @Test
    fun parses_fb2_in_zip_larger_than_the_old_sixteen_mb_limit() = runBlocking {
        // Настоящий случай с устройства: «Книга Уткина Мы тут жизнь живём» — XML внутри архива
        // 19 465 467 Б, и прежний предел 16 МБ отказывал ей в разборе («FB2 внутри архива
        // превышает допустимый размер XML»), книга навсегда оставалась FAILED. Большой XML
        // получается из настоящего FB2 добавлением одного большого `<binary>` — именно так
        // выглядят реальные книги с картинками, а не выдуманной нагрузкой.
        val padded = padPastSixteenMb(UTF8_XML.toByteArray(Charsets.UTF_8))
        assertThat(padded.size).isGreaterThan(16 * 1_024 * 1_024)
        val file = createZip("book.fb2" to padded)

        val result = parser.parse(input(file, "fb2.zip"))

        val success = result as ParseResult.Success
        assertThat(success.metadata.title).isEqualTo("Три товарища")
    }

    @Test
    fun an_entry_over_the_limit_is_still_rejected_with_a_clear_reason() = runBlocking {
        // Предел нужен: без него архив-бомба распаковывается в память целиком. Проверяется на
        // настоящем FB2 с крошечным пределом — так же, как это сделает бомба.
        val file = createZip("book.fb2" to UTF8_XML.toByteArray(Charsets.UTF_8))

        val result = Fb2Parser(maxXmlBytes = 64).parse(input(file, "fb2.zip"))

        assertThat((result as ParseResult.Failed).reason).contains("превышает допустимый размер")
    }

    /**
     * Дополнить настоящий FB2 одним большим `<binary>`: так растут книги с картинками.
     *
     * Наполнение — псевдослучайное, а не повторяющийся символ: настоящие картинки в base64 почти
     * не сжимаются, а `"A".repeat(...)` дал бы отношение сжатия в сотни раз, и архив справедливо
     * попал бы под проверку zip-bomb (`ZipLimits`) — то есть проверялся бы не предел XML.
     */
    private fun padPastSixteenMb(xml: ByteArray): ByteArray {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        val random = Random(seed = 42)
        val size = 17 * 1_024 * 1_024
        val padding = buildString(size) {
            repeat(size) { append(alphabet[random.nextInt(alphabet.length)]) }
        }
        val binary = """<binary id="pad.bin" content-type="image/jpeg">$padding</binary>"""
        return String(xml, Charsets.UTF_8)
            .replace("</FictionBook>", binary + "</FictionBook>")
            .toByteArray(Charsets.UTF_8)
    }

    private fun input(file: File, extension: String): BookInput = BookInput(
        fileName = file.name,
        extension = extension,
        mimeType = null,
        sizeBytes = file.length(),
        openStream = { FileInputStream(file) },
        openSeekable = { null },
        bytes = { _, _ -> file.readBytes() },
    )

    private fun createZip(vararg entries: Pair<String, ByteArray>): File {
        val file = temporaryFolder.newFile("archive-${entries.size}.zip")
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return file
    }

    private companion object {
        val WINDOWS_1251: Charset = Charset.forName("windows-1251")

        const val UTF8_XML =
            """<?xml version="1.0" encoding="utf-8"?>
                <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
                  <description>
                    <title-info>
                      <genre>роман</genre>
                      <genre>классика</genre>
                      <author><first-name>Антон</first-name><last-name>Чехов</last-name></author>
                      <author><first-name>Мария</first-name><last-name>Иванова</last-name></author>
                      <book-title>Три товарища</book-title>
                      <annotation><p>Описание книги</p></annotation>
                      <lang>ru</lang>
                      <sequence name="Приключения" number="1.5"/>
                    </title-info>
                    <publish-info>
                      <isbn>978-5-01</isbn>
                      <year>1869 г.</year>
                      <publisher>Издательство</publisher>
                      <book-name>Игнорируемое имя</book-name>
                    </publish-info>
                    <coverpage><image href="#cover"/></coverpage>
                  </description>
                </FictionBook>"""

        const val CP1251_XML =
            """<?xml version="1.0" encoding="windows-1251"?>
                <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
                  <description>
                    <title-info>
                      <author><first-name>Иван</first-name><last-name>Петров</last-name></author>
                      <book-title>Старый дом</book-title>
                      <lang>ru</lang>
                    </title-info>
                  </description>
                </FictionBook>"""

        const val FB2_WITH_COVER =
            """<?xml version="1.0" encoding="utf-8"?>
                <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0"
                    xmlns:l="http://www.w3.org/1999/xlink">
                  <description>
                    <title-info><book-title>С обложкой</book-title></title-info>
                    <coverpage><image l:href="#cover-id"/></coverpage>
                  </description>
                  <binary id="cover-id" content-type="image/jpeg">aGVsbG8=</binary>
                </FictionBook>"""
    }
}
