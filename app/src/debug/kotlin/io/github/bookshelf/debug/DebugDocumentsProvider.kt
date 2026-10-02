package io.github.bookshelf.debug

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import java.io.File
import java.io.FileNotFoundException

/**
 * Провайдер документов для инструментальных тестов обхода SAF (только debug-сборка).
 *
 * ПОЧЕМУ `ContentProvider`, А НЕ `DocumentsProvider`: Android разрешает приложению держать
 * `DocumentsProvider` только системной подписью — при инициализации он требует
 * `MANAGE_DOCUMENTS` («Provider must be protected by MANAGE_DOCUMENTS»), и запрос из теста
 * получал бы `SecurityException` (проверено на устройстве 01.10.2026). Поэтому провайдер
 * отвечает на ТЕ ЖЕ URI, что строит `DocumentsContract` (`tree/<id>/document/<id>/children`),
 * и отдаёт те же колонки (`DOCUMENT_ID`, `DISPLAY_NAME`, `MIME_TYPE`, `SIZE`, `LAST_MODIFIED`):
 * обходчик не знает, кто отвечает ему на запрос, и проверяется ровно его работа с URI и курсором.
 *
 * Дерево: `root` → `book1.fb2`, `book2.epub`, папка `sub` → `nested.fb2`.
 */
class DebugDocumentsProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    /** Отвечает и на детей папки, и на один документ — по форме URI, как её строит система. */
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val cursor = MatrixCursor(projection ?: DOCUMENT_COLUMNS)
        val segments = uri.pathSegments
        val childrenAt = segments.indexOf(LAST_SEGMENT_CHILDREN)
        if (childrenAt > 0) {
            childrenOf(segments[childrenAt - 1]).forEach { addDocument(cursor, it) }
        } else {
            addDocument(cursor, segments.last())
        }
        return cursor
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val file = fileOf(uri.pathSegments.last())
        if (file == null || !file.exists()) throw FileNotFoundException(uri.toString())
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = mimeOf(uri.pathSegments.last())

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    private fun addDocument(cursor: MatrixCursor, documentId: String) {
        val isDirectory = documentId == ROOT_ID || documentId == SUB_ID
        cursor.newRow().apply {
            add(Document.COLUMN_DOCUMENT_ID, documentId)
            add(Document.COLUMN_DISPLAY_NAME, displayNameOf(documentId))
            add(Document.COLUMN_MIME_TYPE, if (isDirectory) MIME_DIR else mimeOf(documentId))
            add(Document.COLUMN_SIZE, if (isDirectory) 0L else sizeOf(documentId))
            add(Document.COLUMN_LAST_MODIFIED, LAST_MODIFIED)
            add(Document.COLUMN_FLAGS, 0)
        }
    }

    private fun childrenOf(parentDocumentId: String): List<String> = when (parentDocumentId) {
        ROOT_ID -> listOf(BOOK_FB2, BOOK_EPUB, SUB_ID)
        SUB_ID -> listOf(NESTED_FB2)
        else -> emptyList()
    }

    private fun displayNameOf(documentId: String): String = when (documentId) {
        ROOT_ID -> "root"
        SUB_ID -> "sub"
        BOOK_FB2 -> "book1.fb2"
        BOOK_EPUB -> "book2.epub"
        NESTED_FB2 -> "nested.fb2"
        else -> documentId
    }

    private fun mimeOf(documentId: String): String =
        if (documentId.endsWith(".epub")) "application/epub+zip" else "application/fb2"

    private fun sizeOf(documentId: String): Long = fileOf(documentId)?.length() ?: 0L

    /** Файлы создаются при первом обращении: тест читает настоящие байты, а не пустышку. */
    private fun fileOf(documentId: String): File? {
        val name = displayNameOf(documentId)
        if (!name.contains('.')) return null
        val dir = File(requireNotNull(context).cacheDir, STORAGE_DIR).apply { mkdirs() }
        val file = File(dir, name)
        if (!file.exists()) file.writeBytes("содержимое $name".toByteArray())
        return file
    }

    private companion object {
        const val ROOT_ID = "root"
        const val SUB_ID = "sub"
        const val BOOK_FB2 = "book1"
        const val BOOK_EPUB = "book2"
        const val NESTED_FB2 = "nested"
        const val STORAGE_DIR = "saf-test"
        const val MIME_DIR = Document.MIME_TYPE_DIR
        const val LAST_MODIFIED = 1_700_000_000_000L
        const val LAST_SEGMENT_CHILDREN = "children"

        val DOCUMENT_COLUMNS = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_FLAGS,
        )
    }
}
