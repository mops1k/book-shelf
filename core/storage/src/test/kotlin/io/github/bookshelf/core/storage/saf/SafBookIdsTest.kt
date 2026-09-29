package io.github.bookshelf.core.storage.saf

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Идентификатор книги по документу SAF.
 *
 * Главное свойство — **один файл даёт один id**, как бы его ни получили: сканер знает
 * `documentId`, OPDS-загрузка — готовый `content://`-адрес. Раньше эти пути хэшировали разное
 * (`documentId` и полный URI), и одна и та же книга могла попасть в библиотеку дважды.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class SafBookIdsTest {

    private val tree = "content://com.android.externalstorage.documents/tree/primary%3ABooks"

    @Test
    fun `document id and its uri give the same book id`() {
        val documentId = "primary:Books/Грей Денис/kniga.epub"
        val encodedFolder = "primary%3ABooks%2F%D0%93%D1%80%D0%B5%D0%B9%20%D0%94%D0%B5%D0%BD%D0%B8%D1%81"
        val documentUri = Uri.parse("$tree/document/$encodedFolder%2Fkniga.epub")

        val fromId = bookIdOfDocumentId(documentId)
        val fromUri = bookIdOfDocumentUri(documentUri.toString())

        assertThat(fromUri).isEqualTo(fromId)
    }

    @Test
    fun `different documents give different ids`() {
        assertThat(bookIdOfDocumentId("primary:Books/a.fb2"))
            .isNotEqualTo(bookIdOfDocumentId("primary:Books/b.fb2"))
    }

    @Test
    fun `id is safe for the navigation route`() {
        val id = bookIdOfDocumentId("primary:Books/Грей Денис/kniga.epub")

        // Слэши в id разорвали бы маршрут bookdetails/{id} на два сегмента.
        assertThat(id).startsWith("doc:")
        assertThat(id).doesNotContain("/")
        assertThat(id.length).isEqualTo("doc:".length + 64)
    }

    @Test
    fun `not a saf document gives nothing`() {
        assertThat(bookIdOfDocumentUri("file:///sdcard/Books/kniga.epub")).isNull()
    }
}
