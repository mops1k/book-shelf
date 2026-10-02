package io.github.bookshelf

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.storage.saf.AndroidSafTreeReader
import io.github.bookshelf.core.storage.saf.DocumentEntry
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Обход SAF на живом провайдере (`DebugDocumentsProvider`, только debug).
 *
 * Обход дерева — самая рискованная часть сканирования: `DocumentsContract`, `tree`-URI, курсоры
 * и вложенные папки юнит-тестами не проверяются (нужен настоящий Android и настоящий провайдер).
 * Проверяется то, на чём ломается скан: дети папки, отличие папки от файла, размеры и типы,
 * вложенная папка и чтение содержимого по документ-URI.
 */
@RunWith(AndroidJUnit4::class)
class SafTreeReaderInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val treeUri: String = DocumentsContract.buildTreeDocumentUri(authority(), ROOT_ID).toString()

    @Test
    fun the_reader_lists_the_children_of_the_selected_folder() {
        runBlocking {
            val entries = childrenOf(ROOT_ID)

            assertThat(entries.map { it.displayName })
                .containsExactly("book1.fb2", "book2.epub", "sub")

            // Папка и файл различаются: по этому признаку скан решает, идти ли вглубь.
            assertThat(entries.filter { it.isDirectory }.map { it.displayName })
                .containsExactly("sub")
            assertThat(entries.filterNot { it.isDirectory }.map { it.displayName })
                .containsExactly("book1.fb2", "book2.epub")
        }
    }

    @Test
    fun file_sizes_and_mime_types_come_from_the_provider() {
        runBlocking {
            val book = childrenOf(ROOT_ID).first { it.displayName == "book1.fb2" }

            assertThat(book.sizeBytes).isGreaterThan(0L)
            assertThat(book.mimeType).isEqualTo("application/fb2")
            assertThat(book.lastModified).isNotNull()
        }
    }

    @Test
    fun a_nested_folder_is_walked_through_the_same_tree() {
        runBlocking {
            val sub = childrenOf(ROOT_ID).first { it.displayName == "sub" }

            val nested = childrenOf(sub.documentId)

            assertThat(nested.map { it.displayName }).containsExactly("nested.fb2")
        }
    }

    @Test
    fun the_bytes_of_a_book_are_readable_through_its_document_uri() {
        runBlocking {
            val book = childrenOf(ROOT_ID).first { it.displayName == "book1.fb2" }
            val uri = DocumentsContract.buildDocumentUriUsingTree(Uri.parse(treeUri), book.documentId)

            val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }?.decodeToString()

            assertThat(text).isEqualTo("содержимое book1.fb2")
        }
    }

    private suspend fun childrenOf(parentDocumentId: String): List<DocumentEntry> {
        val result = AndroidSafTreeReader(context.contentResolver)
            .children(treeUri = treeUri, parentDocumentId = parentDocumentId)

        return (result as AppResult.Success).value
    }

    private fun authority(): String = "${context.packageName}.debug.saf"

    private companion object {
        const val ROOT_ID = "root"
    }
}
