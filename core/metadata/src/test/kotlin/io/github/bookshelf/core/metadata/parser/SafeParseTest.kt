package io.github.bookshelf.core.metadata.parser

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Разбор книги, из которого не выходят исключения (`parseSafely`).
 *
 * Дефект, ради которого это появилось: заглушки форматов (MOBI, DOCX, ODT, CBZ, CBR, RTF) бросали
 * `NotImplementedError`, а `MetadataWorker` вызывал парсер напрямую — книга оставалась без
 * метаданных, а работа падала. Проверяется, что нарушение контракта парсером становится
 * результатом с причиной, а отмена корутины по-прежнему проходит наверх.
 */
class SafeParseTest {

    @Test
    fun `missing parser is unsupported`() = runTest {
        val result = parseSafely(parser = null, input = input())

        assertThat(result).isEqualTo(ParseResult.Unsupported)
    }

    @Test
    fun `throwing parser becomes a failure with its reason`() = runTest {
        val parser = ThrowingParser(NotImplementedError("STUB(Sprint 6): разобрать MOBI"))

        val result = parseSafely(parser, input())

        // Именно Error, а не Exception: заглушка бросает NotImplementedError, и раньше он выходил наружу.
        val failure = result as? ParseResult.Failed
        assertThat(failure).isNotNull()
        assertThat(failure?.reason).contains("mobi")
        assertThat(failure?.reason).contains("STUB")
    }

    @Test
    fun `error without a message still names the parser`() = runTest {
        val result = parseSafely(ThrowingParser(IllegalStateException()), input())

        assertThat((result as ParseResult.Failed).reason).contains("mobi")
    }

    @Test
    fun `cancellation is propagated instead of being reported as a failure`() = runTest {
        val thrown = runCatching {
            parseSafely(ThrowingParser(CancellationException("скан остановлен")), input())
        }.exceptionOrNull()

        assertThat(thrown).isInstanceOf(CancellationException::class.java)
    }

    private fun input() = BookInput(
        fileName = "book.mobi",
        extension = "mobi",
        mimeType = null,
        sizeBytes = 0L,
        openStream = { ByteArrayInputStream(ByteArray(0)) },
        openSeekable = { null },
        bytes = { _, _ -> ByteArray(0) },
    )

    private class ThrowingParser(private val error: Throwable) : BookMetadataParser {

        override val id: String = "mobi"

        override val sprint: Int = 6

        override fun supports(extension: String, mimeType: String?): Boolean = true

        override suspend fun parse(input: BookInput): ParseResult = throw error
    }
}
