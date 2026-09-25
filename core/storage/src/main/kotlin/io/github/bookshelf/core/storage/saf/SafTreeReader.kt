package io.github.bookshelf.core.storage.saf

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import io.github.bookshelf.core.common.error.AppResult

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
        // STUB(Sprint 2): query(childrenUri, COLUMNS, null, null, null) и сборка DocumentEntry.
        TODO("STUB(Sprint 2): cursor-обход через DocumentsContract.buildChildDocumentsUriUsingTree с отменой")
    }

    override suspend fun takePersistablePermission(treeUri: String): AppResult<Unit> {
        // STUB(Sprint 2): resolver.takePersistableUriPermission(...) + SecurityException → AppError.
        TODO("STUB(Sprint 2): взять persistable-разрешение и обернуть SecurityException в AppError")
    }

    override fun hasPersistedPermission(treeUri: String): Boolean =
        contentResolver.persistedUriPermissions.any { it.uri.toString() == treeUri && it.isReadPermission }

    override fun documentUri(treeUri: String, documentId: String): String =
        DocumentsContract.buildDocumentUriUsingTree(Uri.parse(treeUri), documentId).toString()
}
