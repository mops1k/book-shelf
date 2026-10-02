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
import io.github.bookshelf.core.common.log.APP_LOG_TAG
import io.github.bookshelf.core.common.log.AndroidAppLogger
import io.github.bookshelf.core.common.log.AppLogger
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Чтение дерева SAF.
 *
 * Обход идёт напрямую через [DocumentsContract.buildChildDocumentsUriUsingTree] и `Cursor`
 * (`ContentResolver.query`), по одному запросу на папку. `DocumentFile` сознательно НЕ
 * используется: его `listFiles()` примерно на порядок (≈10×) медленнее на больших деревьях,
 * а скан 2000 файлов должен укладываться в бюджет («Архитектура» §4.2, ADR-003).
 */
/**
 * Права, которые приложение сохраняет у выбранной папки: чтение **и запись**.
 *
 * Запись нужна не сканированию, а скачиванию книг из OPDS в ту же папку (`SafTreeWriter`).
 * Раньше сохранялось только чтение, и запись держалась на системном разрешении «доступ ко всем
 * файлам»: стоило его отозвать, и загрузки падали с `Permission Denial`, а на flavor `play`
 * (там этого разрешения нет вовсе) они не заработали бы никогда.
 */
const val PERSISTABLE_TREE_MODES: Int =
    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

interface SafTreeReader {

    /**
     * Дочерние записи папки [parentDocumentId] внутри дерева [treeUri].
     *
     * Один `query` по children-URI; чтение идёт по колонкам `DOCUMENT_ID`, `DISPLAY_NAME`,
     * `MIME_TYPE`, `SIZE`, `LAST_MODIFIED`.
     */
    suspend fun children(treeUri: String, parentDocumentId: String): AppResult<List<DocumentEntry>>

    /**
     * Забирает persistable-разрешение на дерево, чтобы доступ переживал перезапуск процесса.
     *
     * @param modeFlags какие права сохранить; по умолчанию чтение и запись
     *   ([PERSISTABLE_TREE_MODES]). Если провайдер дал только чтение, сохраняется чтение:
     *   папка хотя бы не «потеряется», а отсутствие записи покажут настройки и ошибка загрузки.
     */
    suspend fun takePersistablePermission(
        treeUri: String,
        modeFlags: Int = PERSISTABLE_TREE_MODES,
    ): AppResult<Unit>

    /** Есть ли уже сохранённое разрешение; иначе источник помечается «доступ потерян». */
    fun hasPersistedPermission(treeUri: String): Boolean

    /** Есть ли сохранённое право записи: без него скачивание книги в папку не пройдёт. */
    fun hasWritePermission(treeUri: String): Boolean

    /** `content://`-URI конкретного документа внутри дерева. */
    fun documentUri(treeUri: String, documentId: String): String

    /**
     * Путь документа относительно корня дерева, без ведущего слэша.
     *
     * Нужен там, где документ показывается человеку или группируется: `displayName` — это только
     * имя файла, и книга из подпапки выглядела бы лежащей в корне.
     */
    fun relativePath(treeUri: String, documentId: String): String
}

/**
 * Реализация поверх `ContentResolver`.
 *
 * @param contentResolver резолвер приложения; время жизни — процесс.
 */
class AndroidSafTreeReader(
    private val contentResolver: ContentResolver,
    /**
     * Журнал обхода.
     *
     * По умолчанию — боевой: класс создаётся и вне DI (инструментальные тесты) и при отладке
     * скана, где подмена логгера не нужна. Явная передача остаётся возможной.
     */
    private val logger: AppLogger = AndroidAppLogger(),
) : SafTreeReader {

    override suspend fun children(treeUri: String, parentDocumentId: String): AppResult<List<DocumentEntry>> {
        val uri = Uri.parse(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri, parentDocumentId)
        val result = appRunCatching {
            contentResolver.query(childrenUri, CHILD_COLUMNS, null, null, null)?.use { cursor ->
                val entries = readEntries(cursor)
                logger.i(
                    APP_LOG_TAG,
                    "SAF $parentDocumentId: провайдер вернул ${entries.size}, " +
                        "из них папок ${entries.count { it.isDirectory }}, " +
                        "файлов ${entries.count { !it.isDirectory }}",
                )
                entries
            }.orEmpty().also { empty ->
                if (empty.isEmpty()) {
                    logger.i(APP_LOG_TAG, "SAF $parentDocumentId: провайдер вернул пусто")
                }
            }
        }
        return when (val error = result.errorOrNull()) {
            is AppError.PermissionDenied -> AppResult.failure(error.copy(uri = treeUri))
            else -> result
        }
    }

    override suspend fun takePersistablePermission(treeUri: String, modeFlags: Int): AppResult<Unit> {
        val uri = Uri.parse(treeUri)
        val withWrite = appRunCatching {
            contentResolver.takePersistableUriPermission(uri, modeFlags)
        }
        if (withWrite is AppResult.Success) {
            logPersistedModes(treeUri)
            return withWrite
        }
        // Провайдер выдал папку только для чтения: сохраняем чтение, иначе после перезапуска
        // источник «потеряется» целиком. Что записи нет, пользователь увидит в настройках,
        // а загрузка объяснит отказ понятным текстом.
        val readOnly = appRunCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        logPersistedModes(treeUri)
        return when (val error = readOnly.errorOrNull()) {
            is AppError.PermissionDenied -> AppResult.failure(error.copy(uri = treeUri))
            else -> readOnly
        }
    }

    override fun hasPersistedPermission(treeUri: String): Boolean =
        contentResolver.persistedUriPermissions.any { it.uri.toString() == treeUri && it.isReadPermission }

    override fun hasWritePermission(treeUri: String): Boolean =
        contentResolver.persistedUriPermissions.any { permission ->
            permission.uri.toString() == treeUri && permission.isWritePermission
        }

    /**
     * Записать в журнал, какие права реально сохранились.
     *
     * Появилось после дефекта 29.09.2026: разница между «только чтение» и «чтение и запись» видна
     * лишь в `dumpsys activity permissions`, и без этой строки причину отказа загрузки приходилось
     * искать вне приложения.
     */
    private fun logPersistedModes(treeUri: String) {
        logger.i(
            APP_LOG_TAG,
            "SAF $treeUri: права папки — чтение=${hasPersistedPermission(treeUri)}, " +
                "запись=${hasWritePermission(treeUri)}",
        )
    }

    override fun documentUri(treeUri: String, documentId: String): String =
        DocumentsContract.buildDocumentUriUsingTree(Uri.parse(treeUri), documentId).toString()

    override fun relativePath(treeUri: String, documentId: String): String =
        relativePathInTree(DocumentsContract.getTreeDocumentId(Uri.parse(treeUri)), documentId)

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
