package io.github.bookshelf.core.metadata.parser.impl

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.metadata.model.MetadataOrigin
import io.github.bookshelf.core.metadata.parser.BookInput
import io.github.bookshelf.core.metadata.parser.ParseFailureKind
import io.github.bookshelf.core.metadata.parser.ParseResult
import io.github.bookshelf.core.metadata.zip.ZipLimits
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * DOCX — ZIP с `docProps/core.xml` («Форматы» §7).
 *
 * Дефект, ради которого парсер реализован: формат распознавался, но разбора не было — книга
 * получала заголовок из имени файла и пометку «формат не поддерживается», хотя Dublin Core
 * в файле есть. Обложки у DOCX обычно нет, поэтому разбор отдаёт только метаданные.
 */
@RunWith(RobolectricTestRunner::class)
class DocxParserTest {

    @Test
    fun `core xml gives title, author, language and year`() = runTest {
        val archive = docx("docProps/core.xml" to CORE_XML.toByteArray())

        val result = DocxParser().parse(input(archive))

        val success = result as ParseResult.Success
        assertThat(success.metadata.title).isEqualTo("Война и мир")
        assertThat(success.metadata.authors).containsExactly("Лев Толстой")
        assertThat(success.metadata.language).isEqualTo("ru-RU")
        // Год берётся из `dcterms:created`: отдельного поля года в core.xml нет.
        assertThat(success.metadata.publishedYear).isEqualTo(1869)
        assertThat(success.metadata.source).isEqualTo(MetadataOrigin.EMBEDDED)
        assertThat(success.cover).isNull()
    }

    @Test
    fun `a document without core xml keeps the file name as the title`() = runTest {
        val archive = docx("word/document.xml" to "<w:document/>".toByteArray())

        val result = DocxParser().parse(input(archive, fileName = "Черновик.docx"))

        assertThat(result).isInstanceOf(ParseResult.Partial::class.java)
        val partial = result as ParseResult.Partial
        assertThat(partial.metadata.title).isEqualTo("Черновик")
        assertThat(partial.metadata.source).isEqualTo(MetadataOrigin.FILENAME)
    }

    @Test
    fun `an archive over the limits is refused instead of unpacked`() = runTest {
        val parser = DocxParser(limits = ZipLimits(maxEntries = 1))
        val archive = docx(
            "docProps/core.xml" to CORE_XML.toByteArray(),
            "word/document.xml" to "<w:document/>".toByteArray(),
        )

        val result = parser.parse(input(archive))

        assertThat((result as ParseResult.Failed).kind).isEqualTo(ParseFailureKind.ARCHIVE_LIMIT)
    }

    @Test
    fun `a broken archive is reported as a corrupt file`() = runTest {
        val result = DocxParser().parse(input("это не zip".toByteArray()))

        assertThat((result as ParseResult.Failed).kind).isEqualTo(ParseFailureKind.CORRUPT_FILE)
    }

    private fun docx(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun input(bytes: ByteArray, fileName: String = "Война и мир.docx") = BookInput(
        fileName = fileName,
        extension = "docx",
        mimeType = null,
        sizeBytes = bytes.size.toLong(),
        openStream = { ByteArrayInputStream(bytes) },
        openSeekable = { null },
        bytes = { offset, length ->
            bytes.copyOfRange(offset.toInt(), (offset + length).toInt().coerceAtMost(bytes.size))
        },
    )

    private companion object {
        val CORE_XML = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties"
                xmlns:dc="http://purl.org/dc/elements/1.1/"
                xmlns:dcterms="http://purl.org/dc/terms/"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
              <dc:title>Война и мир</dc:title>
              <dc:creator>Лев Толстой</dc:creator>
              <dc:language>ru-RU</dc:language>
              <dcterms:created xsi:type="dcterms:W3CDTF">1869-01-01T00:00:00Z</dcterms:created>
            </cp:coreProperties>
        """.trimIndent()
    }
}
