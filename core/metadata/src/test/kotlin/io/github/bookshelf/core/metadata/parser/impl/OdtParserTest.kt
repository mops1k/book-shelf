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
 * ODT — ZIP с `meta.xml` («Форматы» §7).
 *
 * Дефект тот же, что у DOCX: формат распознавался, но не разбирался. Из ODF читаются
 * `dc:title`, `dc:creator` (или `meta:initial-creator`), `dc:language` и число страниц из
 * `meta:document-statistic`. Обложки в ODT нет.
 */
@RunWith(RobolectricTestRunner::class)
class OdtParserTest {

    @Test
    fun `meta xml gives title, author, language and page count`() = runTest {
        val archive = odt("meta.xml" to META_XML.toByteArray())

        val result = OdtParser().parse(input(archive))

        val success = result as ParseResult.Success
        assertThat(success.metadata.title).isEqualTo("Преступление и наказание")
        // `dc:creator` важнее `meta:initial-creator`: второе — тот, кто создал файл, а не автор.
        assertThat(success.metadata.authors).containsExactly("Фёдор Достоевский")
        assertThat(success.metadata.language).isEqualTo("ru")
        assertThat(success.metadata.pageCount).isEqualTo(671)
        assertThat(success.metadata.source).isEqualTo(MetadataOrigin.EMBEDDED)
        assertThat(success.cover).isNull()
    }

    @Test
    fun `without a creator the initial creator becomes the author`() = runTest {
        val archive = odt(
            "meta.xml" to """
                <?xml version="1.0" encoding="UTF-8"?>
                <office:document-meta
                    xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
                    xmlns:meta="urn:oasis:names:tc:opendocument:xmlns:meta:1.0"
                    xmlns:dc="http://purl.org/dc/elements/1.1/">
                  <office:meta>
                    <dc:title>Заметки</dc:title>
                    <meta:initial-creator>Ф. М. Достоевский</meta:initial-creator>
                  </office:meta>
                </office:document-meta>
            """.trimIndent().toByteArray(),
        )

        val result = OdtParser().parse(input(archive)) as ParseResult.Success

        assertThat(result.metadata.authors).containsExactly("Ф. М. Достоевский")
    }

    @Test
    fun `a document without meta xml keeps the file name as the title`() = runTest {
        val archive = odt("content.xml" to "<office:document-content/>".toByteArray())

        val result = OdtParser().parse(input(archive, fileName = "Заметки.odt"))

        assertThat(result).isInstanceOf(ParseResult.Partial::class.java)
        assertThat((result as ParseResult.Partial).metadata.title).isEqualTo("Заметки")
    }

    @Test
    fun `an archive over the limits is refused`() = runTest {
        val parser = OdtParser(limits = ZipLimits(maxEntries = 1))
        val archive = odt(
            "meta.xml" to META_XML.toByteArray(),
            "content.xml" to "<office:document-content/>".toByteArray(),
        )

        val result = parser.parse(input(archive))

        assertThat((result as ParseResult.Failed).kind).isEqualTo(ParseFailureKind.ARCHIVE_LIMIT)
    }

    private fun odt(vararg entries: Pair<String, ByteArray>): ByteArray {
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

    private fun input(bytes: ByteArray, fileName: String = "Преступление и наказание.odt") = BookInput(
        fileName = fileName,
        extension = "odt",
        mimeType = null,
        sizeBytes = bytes.size.toLong(),
        openStream = { ByteArrayInputStream(bytes) },
        openSeekable = { null },
        bytes = { offset, length ->
            bytes.copyOfRange(offset.toInt(), (offset + length).toInt().coerceAtMost(bytes.size))
        },
    )

    private companion object {
        val META_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <office:document-meta
                xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
                xmlns:meta="urn:oasis:names:tc:opendocument:xmlns:meta:1.0"
                xmlns:dc="http://purl.org/dc/elements/1.1/">
              <office:meta>
                <dc:title>Преступление и наказание</dc:title>
                <dc:creator>Фёдор Достоевский</dc:creator>
                <meta:initial-creator>Ф. М. Достоевский</meta:initial-creator>
                <dc:language>ru</dc:language>
                <meta:document-statistic meta:page-count="671"/>
              </office:meta>
            </office:document-meta>
        """.trimIndent()
    }
}
