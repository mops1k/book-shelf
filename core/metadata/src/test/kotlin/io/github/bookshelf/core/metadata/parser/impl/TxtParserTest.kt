package io.github.bookshelf.core.metadata.parser.impl

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.metadata.model.MetadataOrigin
import io.github.bookshelf.core.metadata.parser.BookInput
import io.github.bookshelf.core.metadata.parser.ParseResult
import java.io.File
import java.io.FileInputStream
import java.nio.charset.Charset
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Проверяет потоковый разбор TXT, кодировки и осторожную эвристику заголовка. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class TxtParserTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val parser = TxtParser()

    @Test
    fun reads_utf8_title_and_leaves_authors_empty() = runBlocking {
        val title = "Прекрасный заголовок книги"
        val result = parser.parse(input(
            text = "$title\n\nПервый абзац текста.",
            fileName = "book.txt",
        ))

        val success = result as ParseResult.Success
        assertThat(success.metadata.title).isEqualTo(title)
        assertThat(success.metadata.authors).isEmpty()
        assertThat(success.metadata.description).isNull()
        assertThat(success.metadata.source).isEqualTo(MetadataOrigin.EMBEDDED)
    }

    @Test
    fun reads_windows_1251_title() = runBlocking {
        val title = "Заголовок книги на русском языке"
        val bytes = "$title\nТекст книги".toByteArray(WINDOWS_1251)
        val result = parser.parse(input(bytes, "book.txt", "text/plain; charset=windows-1251"))

        assertThat((result as ParseResult.Success).metadata.title).isEqualTo(title)
    }

    @Test
    fun reads_koi8_r_title_from_declared_mime() = runBlocking {
        val title = "Заголовок книги на русском языке"
        val bytes = "$title\nТекст книги".toByteArray(KOI8_R)
        val result = parser.parse(input(bytes, "book.txt", "text/plain; charset=koi8-r"))

        assertThat((result as ParseResult.Success).metadata.title).isEqualTo(title)
    }

    @Test
    fun uses_filename_when_no_title_candidate_exists() = runBlocking {
        val result = parser.parse(input(
            text = "Обычный текст.\nЕщё одна строка.",
            fileName = "Название книги.txt",
        ))

        val success = result as ParseResult.Success
        assertThat(success.metadata.title).isEqualTo("Название книги")
        assertThat(success.metadata.source).isEqualTo(MetadataOrigin.FILENAME)
    }

    @Test
    fun empty_file_is_failed_without_throwing() = runBlocking {
        val result = parser.parse(input(ByteArray(0), "empty.txt"))

        assertThat(result).isInstanceOf(ParseResult.Failed::class.java)
        assertThat((result as ParseResult.Failed).reason).isNotEmpty()
    }

    @Test
    fun whitespace_only_file_is_failed_without_throwing() = runBlocking {
        val result = parser.parse(input(" \n\t\r\n   ".toByteArray(), "spaces.txt"))

        assertThat(result).isInstanceOf(ParseResult.Failed::class.java)
        assertThat((result as ParseResult.Failed).reason).isNotEmpty()
    }

    @Test
    fun uses_title_from_first_line() = runBlocking {
        val title = "Заголовок книги в первой строке"
        val result = parser.parse(input("$title\nПервый абзац", "first-line.txt"))

        assertThat((result as ParseResult.Success).metadata.title).isEqualTo(title)
    }

    @Test
    fun uses_title_from_fifth_non_empty_line() = runBlocking {
        val title = "Заголовок книги на пятой строке"
        val text = "Первая строка.\nВторая строка.\nТретья строка.\nЧетвёртая строка.\n$title\n" +
            "Первый абзац"
        val result = parser.parse(input(text, "fifth-line.txt"))

        assertThat((result as ParseResult.Success).metadata.title).isEqualTo(title)
    }

    @Test
    fun broken_bytes_are_failed_instead_of_escaping_as_exception() = runBlocking {
        val broken = ByteArray(32) { index -> if (index % 2 == 0) 0 else 0xFF.toByte() }
        val result = parser.parse(input(broken, "broken.txt"))

        assertThat(result).isInstanceOf(ParseResult.Failed::class.java)
        assertThat((result as ParseResult.Failed).reason).isNotEmpty()
    }

    private fun input(
        text: String,
        fileName: String,
        mimeType: String? = "text/plain",
    ): BookInput = input(text.toByteArray(Charsets.UTF_8), fileName, mimeType)

    private fun input(bytes: ByteArray, fileName: String, mimeType: String? = null): BookInput {
        val file: File = temporaryFolder.newFile(fileName.substringAfterLast('/'))
        file.writeBytes(bytes)
        return BookInput(
            fileName = fileName,
            extension = fileName.substringAfterLast('.', "").lowercase(),
            mimeType = mimeType,
            sizeBytes = bytes.size.toLong(),
            openStream = { file.inputStream() },
            openSeekable = { FileInputStream(file).channel },
            bytes = { _, _ -> bytes },
        )
    }

    private companion object {
        val WINDOWS_1251: Charset = Charset.forName("windows-1251")
        val KOI8_R: Charset = Charset.forName("KOI8-R")
    }
}
