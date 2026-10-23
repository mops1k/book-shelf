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
import org.robolectric.annotation.Config

/**
 * CBZ — ZIP с `ComicInfo.xml` (Anansi v2.0) и страницами-картинками («Форматы» §7).
 *
 * Дефект, ради которого парсер реализован: формат распознавался (`supports`), но разбора не было —
 * книга получала заголовок из имени файла и пометку «формат не поддерживается», хотя метаданные
 * в файле есть. Теперь читаются поля ComicInfo, а обложкой становится первая страница в
 * естественном порядке (`page2` раньше `page10`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CbzParserTest {

    @Test
    fun `comic info gives title, authors, series and description`() = runTest {
        val parser = CbzParser()
        val archive = cbz(
            "ComicInfo.xml" to COMIC_INFO.toByteArray(),
            "page1.png" to PNG_ONE,
            "page2.png" to PNG_TWO,
        )

        val result = parser.parse(input(archive))

        val success = result as ParseResult.Success
        assertThat(success.metadata.title).isEqualTo("Приключения Флинкса")
        assertThat(success.metadata.authors).containsExactly("Алан Дин Фостер")
        assertThat(success.metadata.seriesName).isEqualTo("Приключения Флинкса")
        assertThat(success.metadata.seriesIndex).isEqualTo(3.0)
        assertThat(success.metadata.description).isEqualTo("Первый выпуск")
        assertThat(success.metadata.publisher).isEqualTo("Азбука")
        assertThat(success.metadata.publishedYear).isEqualTo(2024)
        assertThat(success.metadata.language).isEqualTo("ru")
        assertThat(success.metadata.pageCount).isEqualTo(2)
        assertThat(success.metadata.tags).containsExactly("Фантастика")
        assertThat(success.metadata.source).isEqualTo(MetadataOrigin.EMBEDDED)
    }

    @Test
    fun `the first page in natural order becomes the cover`() = runTest {
        // Обычная сортировка поставила бы page10.png раньше page2.png — обложкой стала бы десятая.
        val archive = cbz(
            "page10.png" to PNG_TEN,
            "page2.png" to PNG_TWO,
        )

        val result = CbzParser().parse(input(archive)) as ParseResult.Partial

        assertThat(result.coverBytes()).isEqualTo(PNG_TWO)
    }

    @Test
    fun `without comic info the title comes from the file name`() = runTest {
        val archive = cbz("page1.jpg" to PNG_ONE)

        val result = CbzParser().parse(input(archive, fileName = "Сборник.cbz"))

        // Заголовок из имени файла — это данные, а не текст интерфейса: экран покажет его как есть.
        assertThat(result).isInstanceOf(ParseResult.Partial::class.java)
        val partial = result as ParseResult.Partial
        assertThat(partial.metadata.title).isEqualTo("Сборник")
        assertThat(partial.metadata.source).isEqualTo(MetadataOrigin.FILENAME)
    }

    @Test
    fun `an archive over the limits is refused instead of unpacked`() = runTest {
        val parser = CbzParser(limits = ZipLimits(maxEntries = 2))
        val archive = cbz(
            "page1.png" to PNG_ONE,
            "page2.png" to PNG_TWO,
            "page3.png" to PNG_ONE,
        )

        val result = parser.parse(input(archive))

        assertThat(result).isInstanceOf(ParseResult.Failed::class.java)
        assertThat((result as ParseResult.Failed).kind).isEqualTo(ParseFailureKind.ARCHIVE_LIMIT)
    }

    @Test
    fun `a broken archive is reported as a corrupt file`() = runTest {
        val result = CbzParser().parse(input("это не zip".toByteArray()))

        assertThat(result).isInstanceOf(ParseResult.Failed::class.java)
        assertThat((result as ParseResult.Failed).kind).isEqualTo(ParseFailureKind.CORRUPT_FILE)
    }

    private fun ParseResult.coverBytes(): ByteArray? = when (this) {
        is ParseResult.Success -> cover?.bytes
        is ParseResult.Partial -> cover?.bytes
        else -> null
    }

    private fun cbz(vararg entries: Pair<String, ByteArray>): ByteArray {
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

    private fun input(bytes: ByteArray, fileName: String = "Приключения Флинкса.cbz") = BookInput(
        fileName = fileName,
        extension = "cbz",
        mimeType = null,
        sizeBytes = bytes.size.toLong(),
        openStream = { ByteArrayInputStream(bytes) },
        openSeekable = { null },
        bytes = { offset, length ->
            bytes.copyOfRange(offset.toInt(), (offset + length).toInt().coerceAtMost(bytes.size))
        },
    )

    private companion object {
        val PNG_ONE = byteArrayOf(1, 2, 3)
        val PNG_TWO = byteArrayOf(4, 5, 6)
        val PNG_TEN = byteArrayOf(7, 8, 9)

        val COMIC_INFO = """
            <?xml version="1.0" encoding="utf-8"?>
            <ComicInfo>
              <Title>Приключения Флинкса</Title>
              <Series>Приключения Флинкса</Series>
              <Number>3</Number>
              <Summary>Первый выпуск</Summary>
              <Writer>Алан Дин Фостер</Writer>
              <Publisher>Азбука</Publisher>
              <Year>2024</Year>
              <LanguageISO>ru</LanguageISO>
              <PageCount>2</PageCount>
              <Genre>Фантастика</Genre>
            </ComicInfo>
        """.trimIndent()
    }
}
