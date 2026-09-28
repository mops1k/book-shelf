package io.github.bookshelf.core.metadata.parser.impl

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.metadata.parser.ParseResult
import java.io.ByteArrayInputStream
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * FB2-парсер на настоящей книге из Flibusta (Бакстер, «Кольцо», 654 КБ, FB2 в ZIP).
 *
 * Смысл проверки: синтетический fb2 пишется по моему же пониманию формата, поэтому ошибки в нём
 * не видны — он всегда совпадает с парсером. Настоящая книга содержит то, чего в моих фикстурах
 * не было: вложенное имя файла `Bakster_Ksili_4_Kolco.7Hz46A.890606.fb2`, отсутствие
 * `publish-info` с издателем и годом, реальное оформление серии.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class Fb2RealBookTest {

    private val parser = Fb2Parser()

    @Test
    fun parses_real_flb2_zip_book() = runTest {
        val bytes = checkNotNull(
            javaClass.classLoader?.getResourceAsStream("flibusta-890606.fb2.zip"),
        ) { "фикстура flibusta-890606.fb2.zip не найдена" }.readBytes()

        val result = parser.parse(inputOf(bytes, "Bakster_Ksili_4_Kolco.fb2"))

        val metadata = (result as? ParseResult.Success)?.metadata
        assertThat(metadata).isNotNull()
        assertThat(metadata?.title).isEqualTo("Кольцо")
        assertThat(metadata?.authors?.joinToString(" ")).contains("Бакстер")
        assertThat(metadata?.language).isEqualTo("ru")
        assertThat(metadata?.seriesName).isEqualTo("Ксили")
        // Обложка в настоящей книге есть: проверяем, что она извлекается, а не теряется.
        assertThat((result as ParseResult.Success).cover).isNotNull()
    }

    @Test
    fun missing_publish_info_does_not_break_parsing() = runTest {
        val bytes = checkNotNull(
            javaClass.classLoader?.getResourceAsStream("flibusta-890606.fb2.zip"),
        ).readBytes()

        val result = parser.parse(inputOf(bytes, "book.fb2"))

        // У книги нет ни издателя, ни года: разбор обязан дать метаданные, а не упасть.
        assertThat(result).isInstanceOf(ParseResult.Success::class.java)
        assertThat((result as ParseResult.Success).metadata.title).isNotEmpty()
    }

    private fun inputOf(bytes: ByteArray, name: String) = io.github.bookshelf.core.metadata.parser.BookInput(
        fileName = name,
        extension = "fb2",
        mimeType = "application/octet-stream",
        sizeBytes = bytes.size.toLong(),
        openStream = { ByteArrayInputStream(bytes) },
        openSeekable = { null },
        bytes = { offset, length -> bytes.copyOfRange(offset.toInt(), (offset + length).toInt()) },
    )
}
