package io.github.bookshelf.core.data.reader

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.testing.TestAppDispatchers
import io.github.bookshelf.core.readerlink.progress.ProgressBookRef
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Чтение файла рядом с книгой (`BookSidecarFileReader`).
 *
 * Один и тот же относительный путь вроде `Книга.fb2.sdr/metadata.epub.lua` означает разное для
 * разных источников библиотеки: у книги из папки SAF это документ внутри дерева, у книги со съёмного
 * носителя — файл рядом с реальным путём. Здесь проверяются оба случая, потому что ошибка в любом из
 * них выглядит одинаково тихо — «прогресса нет».
 *
 * Отдельно проверяется предел чтения: sidecar — маленький файл, но на карте его может подменить
 * кто угодно, и читать в память гигабайт текста нельзя.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class BookSidecarFileReaderTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val reader = BookSidecarFileReader(context, TestAppDispatchers())

    @Test
    fun `a book with a real path finds its sidecar next to the file`() = runBlocking {
        val dir = folder.newFolder("Книги")
        val book = fileAt(dir, "Книга.fb2.epub")
        fileAt(dir, "Книга.fb2.sdr/metadata.epub.lua", SIDECAR)

        val text = reader.readNearBook(bookAt(book.absolutePath), "Книга.fb2.sdr/metadata.epub.lua")

        assertThat(text).isEqualTo(SIDECAR)
    }

    @Test
    fun `a missing sidecar is null, not an error`() = runBlocking {
        val dir = folder.newFolder("Книги")
        val book = fileAt(dir, "Книга.fb2.epub")

        val text = reader.readNearBook(bookAt(book.absolutePath), "Книга.fb2.sdr/metadata.epub.lua")

        assertThat(text).isNull()
    }

    @Test
    fun `a book from a sa f tree asks the provider for the sibling document`() = runBlocking {
        // Адрес книги — настоящий SAF-адрес из журнала устройства: дерево `primary:Books`, документ
        // внутри папки. Sidecar лежит рядом с книгой, то есть документом в том же дереве.
        val documentUri = "content://com.android.externalstorage.documents/tree/primary%3ABooks/document/" +
            "primary%3ABooks%2F%D0%9B%D0%B5%D0%BC%2F%D0%A1%D0%BE%D0%BB%D1%8F%D1%80%D0%B8%D1%81.fb2.epub"
        val expected = Uri.parse(
            "content://com.android.externalstorage.documents/tree/primary%3ABooks/document/" +
                "primary%3ABooks%2F%D0%9B%D0%B5%D0%BC%2F%D0%A1%D0%BE%D0%BB%D1%8F%D1%80%D0%B8%D1%81.fb2.sdr" +
                "%2Fmetadata.epub.lua",
        )
        shadowOf(context.contentResolver).registerInputStream(
            expected,
            ByteArrayInputStream(SIDECAR.toByteArray(StandardCharsets.UTF_8)),
        )

        val text = reader.readNearBook(
            bookAt(documentUri, relativePath = "Лем/Солярис.fb2.epub"),
            "Солярис.fb2.sdr/metadata.epub.lua",
        )

        // Читаем именно тот документ, который ожидали: подмена пути сломала бы этот тест.
        assertThat(text).isEqualTo(SIDECAR)
    }

    @Test
    fun `a book from a sa f tree without a sidecar is null`() = runBlocking {
        val documentUri = "content://com.android.externalstorage.documents/tree/primary%3ABooks/document/" +
            "primary%3ABooks%2Fbook.fb2.epub"

        val text = reader.readNearBook(bookAt(documentUri), "book.fb2.sdr/metadata.epub.lua")

        assertThat(text).isNull()
    }

    @Test
    fun `an empty path is not read at all`() = runBlocking {
        assertThat(reader.readNearBook(bookAt(""), "book.fb2.sdr/metadata.epub.lua")).isNull()
    }

    @Test
    fun `a sidecar larger than the limit is not read whole`() = runBlocking {
        val dir = folder.newFolder("Большая")
        val book = fileAt(dir, "Книга.epub")
        // Заполнитель длиннее предела, а нужное поле — в самом конце: если файл читается целиком,
        // это заметно по длине результата, а на устройстве кончилась бы память.
        fileAt(dir, "Книга.sdr/metadata.epub.lua", "-- " + "x".repeat(LIMIT + 1024) + "\n" + SIDECAR)

        val text = reader.readNearBook(bookAt(book.absolutePath), "Книга.sdr/metadata.epub.lua")

        assertThat(text).isNotNull()
        assertThat(text!!.length).isAtMost(LIMIT)
        assertThat(text).doesNotContain("percent_finished")
    }

    /** Файл внутри временной папки; папки в пути создаются, как их создал бы читалка. */
    private fun fileAt(dir: File, relative: String, content: String = ""): File =
        File(dir, relative).apply {
            parentFile?.mkdirs()
            writeText(content)
        }

    private fun bookAt(documentUri: String, relativePath: String = "Автор/Книга.fb2.epub") = ProgressBookRef(
        bookId = "book-1",
        documentUri = documentUri,
        readerAddress = documentUri,
        fileName = relativePath.substringAfterLast('/'),
        relativePath = relativePath,
        sizeBytes = 1_000L,
        librarySourceId = "source-1",
    )

    private companion object {
        const val SIDECAR = """return { ["percent_finished"] = 0.42 }"""

        /** Тот же предел, что и в реализации: он часть поведения, а не деталь. */
        const val LIMIT = 256 * 1024
    }
}
