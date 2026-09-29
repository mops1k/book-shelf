package io.github.bookshelf.core.metadata.writer

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.metadata.parser.BookInput
import io.github.bookshelf.core.metadata.parser.ParseResult
import io.github.bookshelf.core.metadata.parser.impl.EpubParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Запись правки метаданных в сам EPUB-файл.
 *
 * Фикстура — настоящая книга с Project Gutenberg («Pride and Prejudice», 1342, public domain):
 * синтетический EPUB совпадал бы с моим же пониманием формата. В этой книге, кстати, нет ни серии,
 * ни описания — то есть проверяется ровно случай «пользователь ставит серию там, где её не было».
 *
 * Свойства, без которых правка EPUB опасна: архив остаётся валидным (`mimetype` первым и без сжатия,
 * как требует спецификация), остальные записи не меняются, а серия пишется в двух видах — так её
 * видят и другие читалки, и наш парсер.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class EpubMetadataWriterTest {

    private val writer = EpubMetadataWriter()
    private val parser = EpubParser()

    @Test
    fun `edit lands in the OPF and is read back by the parser`() = runTest {
        val original = realBook()

        val rewritten = rewritten(
            original,
            MetadataEdit(
                title = "Гордость и предубеждение",
                authors = listOf("Джейн Остин", "Новый Автор"),
                seriesName = "Классика",
                seriesIndex = 3.0,
                description = "Правка пользователя",
            ),
        )

        val metadata = parse(rewritten)
        assertThat(metadata?.title).isEqualTo("Гордость и предубеждение")
        assertThat(metadata?.authors).containsExactly("Джейн Остин", "Новый Автор").inOrder()
        assertThat(metadata?.seriesName).isEqualTo("Классика")
        assertThat(metadata?.seriesIndex).isEqualTo(3.0)
        assertThat(metadata?.description).isEqualTo("Правка пользователя")
    }

    @Test
    fun `archive stays valid and every other entry keeps its content`() = runTest {
        val original = realBook()

        val rewritten = rewritten(original, MetadataEdit(seriesName = "Классика"))

        val entries = readEntries(rewritten)
        // Спецификация EPUB: `mimetype` — первая запись и без сжатия, иначе читалки архив отвергают.
        assertThat(entries.first().name).isEqualTo("mimetype")
        assertThat(entries.first().method).isEqualTo(ZipEntry.STORED)
        assertThat(String(entries.first().bytes)).isEqualTo("application/epub+zip")
        // Всё, кроме OPF, обязано остаться тем же содержимым: правка метаданных не трогает текст книги.
        val before = readEntries(original).associate { entry -> entry.name to entry.bytes }
        entries.filter { it.name != OPF_PATH && it.name != "mimetype" }.forEach { entry ->
            assertThat(entry.bytes).isEqualTo(before.getValue(entry.name))
        }
    }

    @Test
    fun `fields the user did not touch keep the values from the file`() = runTest {
        val original = realBook()

        val rewritten = rewritten(original, MetadataEdit(seriesName = "Классика"))

        val metadata = parse(rewritten)
        assertThat(metadata?.title).isEqualTo("Pride and Prejudice")
        assertThat(metadata?.authors).containsExactly("Jane Austen")
        assertThat(metadata?.language).isEqualTo("en")
    }

    @Test
    fun `series is written for both calibre and the EPUB3 way`() = runTest {
        val original = realBook()

        val rewritten = rewritten(
            original,
            MetadataEdit(seriesName = "Классика", seriesIndex = 3.0),
        )

        // Наш парсер читает оба вида, но сторонние читалки знают только свой: пишем и calibre-теги,
        // и EPUB3 `belongs-to-collection`/`group-position`.
        val opf = String(readEntries(rewritten).single { it.name == OPF_PATH }.bytes)
        assertThat(opf).contains("calibre:series")
        assertThat(opf).contains("Классика")
        assertThat(opf).contains("belongs-to-collection")
        assertThat(opf).contains("group-position")
    }

    @Test
    fun `empty series and description remove them from the OPF`() = runTest {
        val original = realBook()

        val rewritten = rewritten(original, MetadataEdit(seriesName = "", description = ""))

        val opf = String(readEntries(rewritten).single { it.name == OPF_PATH }.bytes)
        assertThat(opf).doesNotContain("calibre:series")
        assertThat(opf).doesNotContain("belongs-to-collection")
        assertThat(opf).doesNotContain("group-position")
        assertThat(opf).doesNotContain("<dc:description")
        // Название и автор остались: убирали только серию и описание.
        val metadata = parse(rewritten)
        assertThat(metadata?.title).isEqualTo("Pride and Prejudice")
        assertThat(metadata?.seriesName).isNull()
    }

    @Test
    fun `clearing only the index keeps the series name`() = runTest {
        val withSeries = rewritten(realBook(), MetadataEdit(seriesName = "Классика", seriesIndex = 3.0))

        val withoutNumber = rewritten(withSeries, MetadataEdit(clearSeriesIndex = true))

        val opf = String(readEntries(withoutNumber).single { it.name == OPF_PATH }.bytes)
        assertThat(opf).contains("calibre:series")
        assertThat(opf).contains("Классика")
        assertThat(opf).doesNotContain("series_index")
        assertThat(opf).doesNotContain("group-position")
    }

    @Test
    fun `not an epub is reported instead of being damaged`() = runTest {
        val result = writer.rewrite(
            bytes = "не архив".toByteArray(),
            edit = MetadataEdit(seriesName = "Классика"),
        )

        assertThat(result).isInstanceOf(MetadataWriteResult.NotSupported::class.java)
    }

    private fun rewritten(original: ByteArray, edit: MetadataEdit): ByteArray {
        val result = writer.rewrite(original, edit)
        assertThat(result).isInstanceOf(MetadataWriteResult.Rewritten::class.java)
        return (result as MetadataWriteResult.Rewritten).bytes
    }

    private suspend fun parse(bytes: ByteArray) =
        (parser.parse(inputOf(bytes)) as? ParseResult.Success)?.metadata

    private fun inputOf(bytes: ByteArray) = BookInput(
        fileName = "pride.epub",
        extension = "epub",
        mimeType = "application/epub+zip",
        sizeBytes = bytes.size.toLong(),
        openStream = { ByteArrayInputStream(bytes) },
        openSeekable = { null },
        bytes = { offset, length ->
            bytes.copyOfRange(offset.toInt(), (offset + length).toInt().coerceAtMost(bytes.size))
        },
    )

    private data class Entry(val name: String, val method: Int, val bytes: ByteArray)

    private fun readEntries(archive: ByteArray): List<Entry> {
        val entries = mutableListOf<Entry>()
        ZipInputStream(ByteArrayInputStream(archive)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    entries += Entry(entry.name, entry.method, zip.readBytes())
                }
                entry = zip.nextEntry
            }
        }
        return entries
    }

    private fun realBook(): ByteArray = checkNotNull(
        javaClass.classLoader?.getResourceAsStream("gutenberg-1342.epub"),
    ) { "фикстура gutenberg-1342.epub не найдена" }.readBytes()

    private companion object {
        const val OPF_PATH = "OEBPS/content.opf"
    }
}
