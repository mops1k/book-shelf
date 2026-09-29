package io.github.bookshelf.core.storage.saf

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Адрес папки книги для команды «Показать в папке».
 *
 * Проверяется чистая функция: система открывает папку по её адресу, и ошибиться здесь легко —
 * например, отдать адрес самой книги (тогда файловый менеджер попытается открыть книгу) или адрес
 * дерева вместо вложенной папки.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class SafFolderUrisTest {

    private val tree = "content://com.android.externalstorage.documents/tree/primary%3ABooks"

    @Test
    fun `nested document points to its own folder`() {
        val document = Uri.parse("$tree/document/primary%3ABooks%2F%D0%93%D1%80%D0%B5%D0%B9%2Fkniga.epub")

        val folder = parentFolderUri(document)

        assertThat(folder).isNotNull()
        // Адрес папки — папка «Грей», а не сам файл и не корень дерева.
        assertThat(folder.toString()).contains("%2F%D0%93%D1%80%D0%B5%D0%B9")
        assertThat(folder.toString()).doesNotContain("kniga.epub")
    }

    @Test
    fun `document in the root of the tree points to the tree root`() {
        val document = Uri.parse("$tree/document/primary%3ABooks%2Fkniga.epub")

        val folder = parentFolderUri(document)

        assertThat(folder).isNotNull()
        assertThat(folder.toString()).doesNotContain("kniga.epub")
        assertThat(folder.toString()).contains("document/primary%3ABooks")
    }

    @Test
    fun `not a saf document gives nothing instead of a wrong address`() {
        val notSaf = Uri.parse("file:///sdcard/Books/kniga.epub")

        assertThat(parentFolderUri(notSaf)).isNull()
    }
}
