package io.github.bookshelf.core.readerlink.koreader

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.errorOrNull
import io.github.bookshelf.core.common.error.getOrNull
import io.github.bookshelf.core.common.error.isFailure
import io.github.bookshelf.core.readerlink.progress.ProgressBookRef
import io.github.bookshelf.core.readerlink.progress.ProgressFileReader
import io.github.bookshelf.core.readerlink.progress.ProgressSourceId
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Прогресс книги из sidecar-файла KOReader (`KoReaderProgressSource`).
 *
 * Особенность этого источника: он **файловый** — «что открыто сейчас» он не знает, потому что
 * KOReader ничего такого наружу не отдаёт. Значит, `openedBook()` обязан возвращать `null`, иначе
 * сценарий синхронизации решил бы, что книга открыта, и записал бы чужой прогресс.
 *
 * Проверяется и граница с хранилищем: источник сам не ходит в SAF, он просит файл у порта
 * `ProgressFileReader` и адресует его **относительным путём рядом с книгой** — иначе знание про
 * `DocumentsContract` расползлось бы по адаптерам читалок.
 */
class KoReaderProgressSourceTest {

    @Test
    fun `the source names itself as the koReader channel`() {
        assertThat(source().id).isEqualTo(ProgressSourceId.KOREADER_SIDECAR)
    }

    @Test
    fun `a file source never claims to know what is open`() = runBlocking {
        // KOReader не отдаёт наружу «текущую книгу»: единственная книга, о которой мы можем
        // спросить, — та, которую открыли мы сами.
        assertThat(source().openedBook()).isNull()
    }

    @Test
    fun `the share from the sidecar becomes percent with its position`() = runBlocking {
        val files = FakeFileReader(text = """return { ["percent_finished"] = 0.42, ["last_xpointer"] = "/body/x" }""")

        val snapshot = source(files).progressFor(ourBook()).getOrNull()

        assertThat(snapshot?.percent).isWithin(0.001f).of(42f)
        assertThat(snapshot?.locatorJson).isEqualTo("/body/x")
    }

    @Test
    fun `the sidecar is asked next to the book, by its file name`() = runBlocking {
        val files = FakeFileReader(text = """return { ["percent_finished"] = 0.1 }""")

        source(files).progressFor(ourBook())

        assertThat(files.asked.single().sibling).isEqualTo("Книга.fb2.sdr/metadata.epub.lua")
    }

    @Test
    fun `a missing sidecar is not zero percent`() = runBlocking {
        // Книгу могли открыть не в KOReader вовсе: молчаливый 0 % на полке — это ложь.
        val result = source(FakeFileReader(text = null)).progressFor(ourBook())

        assertThat(result.isFailure).isTrue()
        assertThat(result.errorOrNull()).isInstanceOf(AppError.NotFound::class.java)
    }

    @Test
    fun `a sidecar without the share is not zero percent either`() = runBlocking {
        val result = source(FakeFileReader(text = """return { ["doc_pages"] = 10 }""")).progressFor(ourBook())

        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `there is nothing to connect to, but the source still reports readiness`() = runBlocking {
        // Файлы на карте доступны всегда; «подключение» — это проверка, что есть чем работать.
        val source = source()

        assertThat(source.connect()).isTrue()
        source.disconnect()
    }

    private fun source(files: ProgressFileReader = FakeFileReader(text = null)) =
        KoReaderProgressSource(files = files)

    private fun ourBook() = ProgressBookRef(
        bookId = "book-1",
        documentUri = DOCUMENT_URI,
        readerAddress = DOCUMENT_URI,
        fileName = "Книга.fb2.epub",
        relativePath = "Автор/Книга.fb2.epub",
        sizeBytes = 1_000L,
        librarySourceId = "source-1",
    )

    private inner class FakeFileReader(private val text: String?) : ProgressFileReader {

        val asked: MutableList<AskedFile> = mutableListOf()

        override suspend fun readNearBook(book: ProgressBookRef, sibling: String): String? {
            asked += AskedFile(bookId = book.bookId, sibling = sibling)
            return text
        }
    }

    private data class AskedFile(val bookId: String, val sibling: String)

    private companion object {
        const val DOCUMENT_URI =
            "content://com.android.externalstorage.documents/tree/primary%3ABooks/document/primary%3ABooks%2Fbook.fb2"
    }
}
