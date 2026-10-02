package io.github.bookshelf.core.data.metadata

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.common.error.errorOrNull
import io.github.bookshelf.core.data.model.Book
import io.github.bookshelf.core.metadata.parser.impl.Fb2Parser
import io.github.bookshelf.core.metadata.registry.DefaultParserRegistry
import io.github.bookshelf.core.metadata.writer.MetadataEdit
import io.github.bookshelf.core.storage.files.BookContentOpener
import io.github.bookshelf.core.storage.files.DocumentFacts
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import io.github.bookshelf.core.common.io.SeekableFile
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Запись правки в документ через SAF (`DefaultBookMetadataEditor`).
 *
 * Здесь проверяется проводка, а не форматы: правильность FB2 и EPUB проверена на настоящих книгах
 * (`Fb2MetadataWriterTest`, `EpubMetadataWriterTest`). Поэтому книга маленькая и синтетическая —
 * важно, что байты доходят до документа, что неподдержанный формат не трогает файл и что нечитаемый
 * файл даёт понятную причину, а не тихий успех.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class BookMetadataEditorTest {

    private lateinit var context: Context
    private lateinit var editor: BookMetadataEditor
    private val opener = FakeOpener()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        editor = DefaultBookMetadataEditor(
            context = context,
            opener = opener,
            parsers = DefaultParserRegistry(setOf(Fb2Parser())),
        )
    }

    @Test
    fun `edit is written into the document`() = runBlocking {
        opener.bytes = FB2.toByteArray()
        val written = ByteArrayOutputStream()
        shadowOf(context.contentResolver).registerOutputStream(DOCUMENT, written)

        val result = editor.writeToFile(book(extension = "fb2"), MetadataEdit(seriesName = "Новая серия"))

        assertThat(result.errorOrNull()).isNull()
        // Проверяем именно записанные байты: их читает наш же парсер, значит правка попала в файл.
        val text = written.toString("UTF-8")
        assertThat(text).contains("Новая серия")
    }

    @Test
    fun `clearing a value is written instead of being rejected by the check`() = runBlocking {
        opener.bytes = FB2_WITH_SERIES.toByteArray()
        val written = ByteArrayOutputStream()
        shadowOf(context.contentResolver).registerOutputStream(DOCUMENT, written)

        val result = editor.writeToFile(book(extension = "fb2"), MetadataEdit(seriesName = ""))

        assertThat(result.errorOrNull()).isNull()
        // Серия убрана именно в файле, а не только в базе.
        assertThat(written.toString("UTF-8")).doesNotContain("<sequence")
    }

    @Test
    fun `unsupported format leaves the document untouched`() = runBlocking {
        opener.bytes = FB2.toByteArray()
        val written = ByteArrayOutputStream()
        shadowOf(context.contentResolver).registerOutputStream(DOCUMENT, written)

        val result = editor.writeToFile(book(extension = "pdf"), MetadataEdit(seriesName = "Новая серия"))

        assertThat(result.errorOrNull()?.message).contains("FB2 и EPUB")
        assertThat(written.size()).isEqualTo(0)
    }

    @Test
    fun `unreadable file is reported instead of silently succeeding`() = runBlocking {
        opener.bytes = null
        val written = ByteArrayOutputStream()
        shadowOf(context.contentResolver).registerOutputStream(DOCUMENT, written)

        val result = editor.writeToFile(book(extension = "fb2"), MetadataEdit(seriesName = "Новая серия"))

        assertThat(result.errorOrNull()?.message).contains("не открылся")
        assertThat(written.size()).isEqualTo(0)
    }

    @Test
    fun `empty edit does not touch the document`() = runBlocking {
        opener.bytes = FB2.toByteArray()
        val written = ByteArrayOutputStream()
        shadowOf(context.contentResolver).registerOutputStream(DOCUMENT, written)

        editor.writeToFile(book(extension = "fb2"), MetadataEdit())

        assertThat(written.size()).isEqualTo(0)
    }

    private fun book(extension: String) = Book(
        id = "book-1",
        title = "Книга",
        authorsText = "Автор",
        extension = extension,
        documentUri = DOCUMENT.toString(),
        relativePath = "Автор/Книга.$extension",
        sizeBytes = FB2.length.toLong(),
        addedAt = 0L,
    )

    /** Чтение файла подменено: настоящий SAF-провайдер в юнит-тесте недоступен. */
    private class FakeOpener : BookContentOpener {

        var bytes: ByteArray? = null

        override fun openStream(documentUri: String): InputStream? =
            bytes?.let { data -> ByteArrayInputStream(data) }

        override fun openSeekableChannel(documentUri: String): SeekableFile? = null

        override fun sizeOf(documentUri: String): Long? = bytes?.size?.toLong()
        override fun factsOf(documentUri: String): DocumentFacts? = null

        override fun tempCopy(documentUri: String): AppResult<String> = AppResult.success("unused")

        override fun replaceContent(documentUri: String, bytes: ByteArray): AppResult<Unit> =
            AppResult.failure(AppError.Unknown(message = "замена в этом тесте не нужна"))

        override fun exportCopy(documentUri: String, extension: String): AppResult<String> =
            AppResult.success("unused")
    }

    private companion object {
        val DOCUMENT: Uri = Uri.parse("content://test/book.fb2")


        val FB2 = """
            <?xml version="1.0" encoding="UTF-8"?>
            <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
              <description>
                <title-info>
                  <genre>sf</genre>
                  <author>
                    <first-name>Иван</first-name>
                    <last-name>Петров</last-name>
                  </author>
                  <book-title>Книга</book-title>
                </title-info>
              </description>
              <body><section><p>Текст</p></section></body>
            </FictionBook>
        """.trimIndent()

        /** Та же книга, но с серией: на ней проверяется очистка значения. */
        val FB2_WITH_SERIES = FB2.replace(
            "<book-title>Книга</book-title>",
            "<book-title>Книга</book-title><sequence name=\"Серия\" number=\"1\"/>",
        )
    }
}
