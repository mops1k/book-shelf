package io.github.bookshelf.core.metadata.parser

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.metadata.model.MetadataOrigin
import io.github.bookshelf.core.metadata.parser.impl.EpubParser
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * EPUB-фикстуры проверяют полный путь ZIP → container.xml → OPF.
 *
 * Все архивы создаются временно через [ZipOutputStream], поэтому тесты не зависят от бинарных
 * файлов в репозитории и явно показывают структуру EPUB 2/3.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class EpubParserTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val parser = EpubParser()

    @Test
    fun supports_epub_but_not_txt() {
        assertThat(parser.supports("epub", null)).isTrue()
        assertThat(parser.supports("txt", null)).isFalse()
    }

    @Test
    fun parses_dublin_core_calibre_series_and_isbn() = runBlocking {
        val archive = createEpub(OPF_CALIBRE)

        val result = parser.parse(input(archive, "epub"))

        val success = result as ParseResult.Success
        assertThat(success.metadata.title).isEqualTo("Книга из EPUB")
        assertThat(success.metadata.authors).isEqualTo(listOf("Алиса", "Борис"))
        assertThat(success.metadata.language).isEqualTo("ru")
        assertThat(success.metadata.description).isEqualTo("Описание EPUB")
        assertThat(success.metadata.publisher).isEqualTo("EPUB Издательство")
        assertThat(success.metadata.publishedYear).isEqualTo(2020)
        assertThat(success.metadata.isbn).isEqualTo("978-5-02")
        assertThat(success.metadata.seriesName).isEqualTo("Мир")
        assertThat(success.metadata.seriesIndex).isEqualTo(2.0)
        assertThat(success.metadata.source).isEqualTo(MetadataOrigin.EMBEDDED)
    }

    @Test
    fun parses_epub3_series_collection() = runBlocking {
        val archive = createEpub(OPF_EPUB3_SERIES)

        val result = parser.parse(input(archive, "epub"))

        val success = result as ParseResult.Success
        assertThat(success.metadata.seriesName).isEqualTo("Большая серия")
        assertThat(success.metadata.seriesIndex).isEqualTo(4.5)
    }

    @Test
    fun parses_meta_form_of_epub3_series_properties() = runBlocking {
        val archive = createEpub(OPF_EPUB3_META_SERIES)

        val result = parser.parse(input(archive, "epub"))

        val success = result as ParseResult.Success
        assertThat(success.metadata.seriesName).isEqualTo("Мета-серия")
        assertThat(success.metadata.seriesIndex).isEqualTo(7.0)
    }

    @Test
    fun extracts_epub2_cover_from_manifest_id() = runBlocking {
        val archive = createEpub(
            OPF_CALIBRE,
            "OEBPS/images/cover.jpg" to COVER_BYTES,
        )

        val result = parser.parse(input(archive, "epub"))

        val success = result as ParseResult.Success
        assertThat(success.cover).isNotNull()
        assertThat(success.cover?.bytes?.size).isEqualTo(COVER_BYTES.size)
        assertThat(success.cover?.mimeType).isEqualTo("image/jpeg")
    }

    @Test
    fun extracts_epub3_cover_image_property() = runBlocking {
        val archive = createEpub(
            OPF_EPUB3_SERIES,
            "OEBPS/images/cover3.png" to COVER_BYTES,
        )

        val result = parser.parse(input(archive, "epub"))

        val success = result as ParseResult.Success
        assertThat(success.cover?.bytes?.size).isEqualTo(COVER_BYTES.size)
        assertThat(success.cover?.mimeType).isEqualTo("image/png")
    }

    @Test
    fun archive_without_container_is_failed() = runBlocking {
        val archive = createZip("content.opf" to OPF_CALIBRE.toByteArray(Charsets.UTF_8))

        val result = parser.parse(input(archive, "epub"))

        val failed = result as ParseResult.Failed
        assertThat(failed.reason).contains("container.xml")
    }

    @Test
    fun broken_opf_namespace_does_not_escape_as_exception() = runBlocking {
        val archive = createEpub(OPF_WRONG_NAMESPACE)

        val result = parser.parse(input(archive, "epub"))

        assertThat(result is ParseResult.Failed || result is ParseResult.Partial).isTrue()
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

    private fun createEpub(opf: String, vararg extraEntries: Pair<String, ByteArray>): File = createZip(
        "mimetype" to "application/epub+zip".toByteArray(Charsets.US_ASCII),
        "META-INF/container.xml" to CONTAINER.toByteArray(Charsets.UTF_8),
        "OEBPS/content.opf" to opf.toByteArray(Charsets.UTF_8),
        *extraEntries,
    )

    private fun createZip(vararg entries: Pair<String, ByteArray>): File {
        val file = temporaryFolder.newFile("book-${entries.size}.epub")
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
        val COVER_BYTES = byteArrayOf(1, 2, 3, 4, 5)

        const val CONTAINER =
            """<?xml version="1.0" encoding="UTF-8"?>
                <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                  </rootfiles>
                </container>"""

        const val OPF_CALIBRE =
            """<?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf"
                    xmlns:dc="http://purl.org/dc/elements/1.1/"
                    xmlns:opf="http://www.idpf.org/2007/opf">
                  <metadata>
                    <dc:title>Книга из EPUB</dc:title>
                    <dc:creator>Алиса</dc:creator>
                    <dc:creator>Борис</dc:creator>
                    <dc:language>ru</dc:language>
                    <dc:description>Описание EPUB</dc:description>
                    <dc:publisher>EPUB Издательство</dc:publisher>
                    <dc:identifier opf:scheme="ISBN">978-5-02</dc:identifier>
                    <dc:date>2020-03-04</dc:date>
                    <meta name="calibre:series" content="Мир"/>
                    <meta name="calibre:series_index" content="2"/>
                    <meta name="cover" content="cover"/>
                  </metadata>
                  <manifest>
                    <item id="cover" href="images/cover.jpg" media-type="image/jpeg"/>
                    <item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                </package>"""

        const val OPF_EPUB3_SERIES =
            """<?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf"
                    xmlns:dc="http://purl.org/dc/elements/1.1/">
                  <metadata>
                    <dc:title>Книга EPUB 3</dc:title>
                    <belongs-to-collection collection-type="set">Все книги</belongs-to-collection>
                    <belongs-to-collection collection-type="series">Большая серия</belongs-to-collection>
                    <group-position>4.5</group-position>
                    <meta property="belongs-to-collection" refines="#series">Другая</meta>
                  </metadata>
                  <manifest>
                    <item id="cover3" href="images/cover3.png" media-type="image/png" properties="cover-image"/>
                  </manifest>
                </package>"""

        const val OPF_EPUB3_META_SERIES =
            """<?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf"
                    xmlns:dc="http://purl.org/dc/elements/1.1/">
                  <metadata>
                    <dc:title>Книга с meta-серией</dc:title>
                    <meta property="belongs-to-collection" id="series">Мета-серия</meta>
                    <meta refines="#series" property="group-position">7</meta>
                  </metadata>
                </package>"""

        const val OPF_WRONG_NAMESPACE =
            """<?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="urn:not-an-opf">
                  <metadata><title>Неизвестный namespace</title></metadata>
                </package>"""
    }
}
