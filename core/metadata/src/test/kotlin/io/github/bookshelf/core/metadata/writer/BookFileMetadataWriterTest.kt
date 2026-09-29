package io.github.bookshelf.core.metadata.writer

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.metadata.parser.BookInput
import io.github.bookshelf.core.metadata.parser.ParseResult
import io.github.bookshelf.core.metadata.parser.impl.Fb2Parser
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Выбор способа записи по формату файла.
 *
 * Отдельно проверяется `.fb2.zip`: в библиотеке пользователя книги лежат именно так, и правка
 * обязана попадать внутрь архива, а не «рядом» с ним.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class BookFileMetadataWriterTest {

    private val writer = BookFileMetadataWriter()

    @Test
    fun `fb2 zip gets the edit inside the archive`() = runTest {
        val archive = realArchive()

        val result = writer.rewrite("fb2.zip", archive, MetadataEdit(seriesName = "Серия из архива"))

        val rewritten = (result as MetadataWriteResult.Rewritten).bytes
        val inner = firstEntry(rewritten)
        val metadata = (Fb2Parser().parse(inputOf(inner)) as? ParseResult.Success)?.metadata
        assertThat(metadata?.seriesName).isEqualTo("Серия из архива")
        // Заголовок и авторы книги не тронуты: правился только один элемент.
        assertThat(metadata?.title).isEqualTo("Кольцо")
    }

    @Test
    fun `plain fb2 is rewritten directly`() = runTest {
        val inner = firstEntry(realArchive())

        val result = writer.rewrite("fb2", inner, MetadataEdit(seriesName = "Серия"))

        val metadata = (Fb2Parser().parse(inputOf((result as MetadataWriteResult.Rewritten).bytes)) as
            ParseResult.Success).metadata
        assertThat(metadata.seriesName).isEqualTo("Серия")
    }

    @Test
    fun `unsupported format is reported with the reason`() {
        val result = writer.rewrite("pdf", "не книга".toByteArray(), MetadataEdit(seriesName = "Серия"))

        val unsupported = result as? MetadataWriteResult.NotSupported
        assertThat(unsupported?.reason).contains("FB2 и EPUB")
    }

    private fun firstEntry(archive: ByteArray): ByteArray =
        ZipInputStream(ByteArrayInputStream(archive)).use { zip ->
            checkNotNull(zip.nextEntry) { "в архиве нет записей" }
            zip.readBytes()
        }

    private fun inputOf(bytes: ByteArray) = BookInput(
        fileName = "book.fb2",
        extension = "fb2",
        mimeType = "application/fb2",
        sizeBytes = bytes.size.toLong(),
        openStream = { ByteArrayInputStream(bytes) },
        openSeekable = { null },
        bytes = { offset, length ->
            bytes.copyOfRange(offset.toInt(), (offset + length).toInt().coerceAtMost(bytes.size))
        },
    )

    private fun realArchive(): ByteArray = checkNotNull(
        javaClass.classLoader?.getResourceAsStream("flibusta-890606.fb2.zip"),
    ) { "фикстура flibusta-890606.fb2.zip не найдена" }.readBytes()
}
