package io.github.bookshelf.core.metadata.parser.impl

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.metadata.parser.BookInput
import io.github.bookshelf.core.metadata.parser.ParseFailureKind
import io.github.bookshelf.core.metadata.parser.ParseResult
import java.io.ByteArrayInputStream
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Проверяет деградацию PDF, когда провайдер не дал seekable-канал.
 * Положительный путь PdfRenderer требует настоящего PDF и проверяется на устройстве.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class PdfParserTest {

    private val parser = PdfParser()

    @Test
    fun supports_pdf_by_extension_and_not_txt() {
        assertThat(parser.supports("pdf", null)).isTrue()
        assertThat(parser.supports("txt", null)).isFalse()
    }

    @Test
    fun missing_seekable_channel_is_reported_without_throwing() = runBlocking {
        val result = parser.parse(inputWithoutSeekable())

        val reason = when (result) {
            is ParseResult.Failed -> result.reason
            is ParseResult.Partial -> result.reason
            else -> ""
        }
        assertThat(reason).isNotEmpty()
    }

    @Test
    fun failing_stream_after_missing_channel_is_failed_without_throwing() = runBlocking {
        val result = parser.parse(input(
            openStream = { throw IOException("поток недоступен") },
        ))

        assertThat(result).isInstanceOf(ParseResult.Failed::class.java)
        assertThat((result as ParseResult.Failed).reason).isNotEmpty()
    }

    @Test
    fun `a document bigger than the copy limit is rejected before it fills storage`() = runBlocking<Unit> {
        // Дефект аудита 28.09.2026: PDF копировался целиком (канал или поток) без контроля размера.
        val parser = PdfParser(maxCopyBytes = 4)

        val result = parser.parse(inputWithoutSeekable())

        assertThat(result).isInstanceOf(ParseResult.Failed::class.java)
        assertThat((result as ParseResult.Failed).kind).isEqualTo(ParseFailureKind.ARCHIVE_LIMIT)
    }

    private fun inputWithoutSeekable(): BookInput = input(
        openStream = { ByteArrayInputStream("%PDF-1.4\n% тестовый документ".toByteArray()) },
    )

    private fun input(openStream: () -> java.io.InputStream): BookInput = BookInput(
        fileName = "book.pdf",
        extension = "pdf",
        mimeType = "application/pdf",
        sizeBytes = 27,
        openStream = openStream,
        openSeekable = { null },
        bytes = { _, _ -> "%PDF-1.4".toByteArray(Charsets.US_ASCII) },
    )
}
