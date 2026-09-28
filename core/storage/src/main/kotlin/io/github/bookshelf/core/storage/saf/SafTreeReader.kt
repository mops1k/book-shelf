package io.github.bookshelf.core.storage.saf

import android.content.ContentResolver
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.common.error.appRunCatching
import io.github.bookshelf.core.common.error.errorOrNull
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Чтение дерева SAF.
 *
 * Обход идёт напрямую через [DocumentsContract.buildChildDocumentsUriUsingTree] и `Cursor`
 * (`ContentResolver.query`), по одному запросу на папку. `DocumentFile` сознательно НЕ
 * используется: его `listFiles()` примерно на порядок (≈10×) медленнее на больших деревьях,
 * а скан 2000 файлов должен укладываться в бюджет (docs/architecture.md §4.2, ADR-003).
 */
interface SafTreeReader {

    /**
     * Дочерние записи папки [parentDocumentId] внутри дерева [treeUri].
     *
     * Один `query` по children-URI; чтение идёт по колонкам `DOCUMENT_ID`, `DISPLAY_NAME`,
     * `MIME_TYPE`, `SIZE`, `LAST_MODIFIED`.
     */
    suspend fun children(treeUri: String, parentDocumentId: String): AppResult<List<DocumentEntry>>

    /**
     * Забирает persistable-разрешение на дерево
     * (`FLAG_GRANT_READ_URI_PERMISSION`), чтобы доступ переживал перезапуск процесса.
     */
    suspend fun takePersistablePermission(treeUri: String): AppResult<Unit>

    /** Есть ли уже сохранённое разрешение; иначе источник помечается «доступ потерян». */
    fun hasPersistedPermission(treeUri: String): Boolean

    /** `content://`-URI конкретного документа внутри дерева. */
    fun documentUri(treeUri: String, documentId: String): String
}

/**
 * Реализация поверх `ContentResolver`.
 *
 * @param contentResolver резолвер приложения; время жизни — процесс.
 */
class AndroidSafTreeReader(
    private val contentResolver: ContentResolver,
) : SafTreeReader {

    override suspend fun children(treeUri: String, parentDocumentId: String): AppResult<List<DocumentEntry>> {
        val uri = Uri.parse(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri, parentDocumentId)
        val result = appRunCatching {
            contentResolver.query(childrenUri, CHILD_COLUMNS, null, null, null)?.use { cursor ->
                val entries = readEntries(cursor)
                android.util.Log.i(
                    "BookShelf",
                    "SAF $parentDocumentId: провайдер вернул ${entries.size}, " +
                        "из них папок ${entries.count { it.isDirectory }}, " +
                        "файлов ${entries.count { !it.isDirectory }}",
                )
                entries
            }.orEmpty().also { empty ->
                if (empty.isEmpty()) {
                    android.util.Log.i("BookShelf", "SAF $parentDocumentId: провайдер вернул пусто")
                }
            }
        }
        return when (val error = result.errorOrNull()) {
            is AppError.PermissionDenied -> AppResult.failure(error.copy(uri = treeUri))
            else -> result
        }
    }

    override suspend fun takePersistablePermission(treeUri: String): AppResult<Unit> {
        val uri = Uri.parse(treeUri)
        val result = appRunCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return when (val error = result.errorOrNull()) {
            is AppError.PermissionDenied -> AppResult.failure(error.copy(uri = treeUri))
            else -> result
        }
    }

    override fun hasPersistedPermission(treeUri: String): Boolean =
        contentResolver.persistedUriPermissions.any { it.uri.toString() == treeUri && it.isReadPermission }

    override fun documentUri(treeUri: String, documentId: String): String =
        DocumentsContract.buildDocumentUriUsingTree(Uri.parse(treeUri), documentId).toString()

    private suspend fun readEntries(cursor: Cursor): List<DocumentEntry> {
        currentCoroutineContext().ensureActive()
        if (!cursor.moveToFirst()) return emptyList()

        val documentIdIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
        val displayNameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val mimeTypeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
        val sizeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
        val lastModifiedIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

        val entries = mutableListOf<DocumentEntry>()
        do {
            currentCoroutineContext().ensureActive()
            val mimeType = cursor.getStringOrNull(mimeTypeIndex)
            entries += DocumentEntry(
                documentId = cursor.getString(documentIdIndex),
                displayName = cursor.getString(displayNameIndex),
                isDirectory = mimeType == DocumentsContract.Document.MIME_TYPE_DIR,
                sizeBytes = cursor.getLongOrNull(sizeIndex),
                lastModified = cursor.getLongOrNull(lastModifiedIndex),
                mimeType = mimeType,
            )
        } while (cursor.moveToNext())
        return entries
    }

    private fun Cursor.getStringOrNull(index: Int): String? =
        if (index < 0 || isNull(index)) null else getString(index)

    private fun Cursor.getLongOrNull(index: Int): Long? =
        if (index < 0 || isNull(index)) null else getLong(index)

    private companion object {

        val CHILD_COLUMNS = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
    }
}
