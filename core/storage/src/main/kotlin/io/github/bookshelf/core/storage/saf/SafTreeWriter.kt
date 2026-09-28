package io.github.bookshelf.core.storage.saf

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.common.error.appRunCatching
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Запись файлов в дерево SAF.
 *
 * Отсутствовал целиком: в проекте был только [SafTreeReader], поэтому скачанную из каталога
 * книгу было некуда положить — очередь загрузок накапливалась и ничего не происходило.
 *
 * Документ создаётся сразу под финальным именем: переименование через SAF провайдер
 * внешнего хранилища не поддерживает («Update not supported»). Атомарность на стороне
 * вызывающего — он сначала пишет во временный файл приватного хранилища.
 */
interface SafTreeWriter {

    /**
     * Создать (или перезаписать) файл [fileName] в папке [relativeDir] дерева [treeUri].
     *
     * @return `content://`-URI созданного документа.
     */
    suspend fun write(
        treeUri: String,
        relativeDir: String,
        fileName: String,
        bytes: ByteArray,
        mimeType: String = "application/octet-stream",
    ): AppResult<String>

    /**
     * Потоковая запись: [bytes] отдаётся порциями, память под файл целиком не занимается.
     *
     * Нужно для книг на десятки мегабайт: `write` с `ByteArray` такой файл в память втащит.
     */
    suspend fun writeStream(
        treeUri: String,
        relativeDir: String,
        fileName: String,
        mimeType: String = "application/octet-stream",
        onProgress: suspend (bytesWritten: Long, total: Long) -> Unit = { _, _ -> },
        block: suspend (emit: suspend (ByteArray) -> Unit) -> Unit,
    ): AppResult<String>

    /** Удалить документ по URI. */
    suspend fun delete(documentUri: String): AppResult<Unit>
}

@Singleton
class SafTreeWriterImpl @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
) : SafTreeWriter {

    private val resolver: ContentResolver get() = context.contentResolver

    override suspend fun write(
        treeUri: String,
        relativeDir: String,
        fileName: String,
        bytes: ByteArray,
        mimeType: String,
    ): AppResult<String> = writeStream(treeUri, relativeDir, fileName, mimeType) { emit ->
        emit(bytes)
    }

    /**
     * Создать документ сразу под финальным именем и записать в него поток.
     *
     * Схема «создать `.part` и переименовать» на SAF нерабочая: системный провайдер внешнего
     * хранилища отвечает на переименование «Update not supported», и загрузка падала уже
     * после того, как все байты ушли. Атомарность обеспечивает вызывающий — он сначала
     * пишет во временный файл приватного хранилища, а сюда переносит готовый файл.
     *
     * При обрыве записи созданный документ удаляется: полуфайл в папке пользователя хуже,
     * чем его отсутствие.
     */
    override suspend fun writeStream(
        treeUri: String,
        relativeDir: String,
        fileName: String,
        mimeType: String,
        onProgress: suspend (Long, Long) -> Unit,
        block: suspend (emit: suspend (ByteArray) -> Unit) -> Unit,
    ): AppResult<String> = appRunCatching {
        val tree = Uri.parse(treeUri)
        val parentId = resolveDirectory(tree, relativeDir)
        val documentUri = try {
            createDocument(tree, parentId, fileName, mimeType)
        } catch (error: Throwable) {
            // «Update not supported» сам по себе ничего не объясняет; без имени файла и
            // MIME нельзя понять, что именно отверг провайдер.
            throw IllegalStateException(
                "Провайдер не создал '$fileName' с типом '$mimeType': ${error.message}",
                error,
            )
        }

        var written = 0L
        try {
            resolver.openOutputStream(documentUri)?.use { output ->
                block { chunk ->
                    output.write(chunk)
                    written += chunk.size
                    onProgress(written, -1L)
                }
                output.flush()
            } ?: error("Не удалось открыть файл для записи: $fileName")
        } catch (error: Throwable) {
            runCatching { resolver.delete(documentUri, null, null) }
            throw error
        }
        documentUri.toString()
    }

    override suspend fun delete(documentUri: String): AppResult<Unit> = appRunCatching {
        resolver.delete(Uri.parse(documentUri), null, null)
        Unit
    }

    /** Идентификатор папки назначения; пустой `relativeDir` — корень дерева. */
    private fun resolveDirectory(tree: Uri, relativeDir: String): String {
        if (relativeDir.isBlank()) return DocumentsContract.getTreeDocumentId(tree)
        var current = DocumentsContract.getTreeDocumentId(tree)
        relativeDir.split('/').filter { it.isNotBlank() }.forEach { segment ->
            current = findOrCreateDirectory(tree, current, segment)
        }
        return current
    }

    /** Найти папку по имени или создать её: каталог загрузок может быть новым. */
    private fun findOrCreateDirectory(tree: Uri, parentId: String, name: String): String {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        resolver.query(childrenUri, CHILD_COLUMNS, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            while (cursor.moveToNext()) {
                if (cursor.getString(mimeIndex) == DocumentsContract.Document.MIME_TYPE_DIR &&
                    cursor.getString(nameIndex) == name
                ) {
                    return cursor.getString(idIndex)
                }
            }
        }
        val created = DocumentsContract.createDocument(
            resolver,
            DocumentsContract.buildDocumentUriUsingTree(tree, parentId),
            DocumentsContract.Document.MIME_TYPE_DIR,
            name,
        ) ?: error("Не удалось создать папку $name")
        return runCatching { DocumentsContract.getDocumentId(created) }.getOrNull()
            ?: error("Не удалось получить id папки $name")
    }

    private fun createDocument(tree: Uri, parentId: String, name: String, mimeType: String): Uri =
        DocumentsContract.createDocument(
            resolver,
            DocumentsContract.buildDocumentUriUsingTree(tree, parentId),
            mimeType,
            name,
        ) ?: error("Не удалось создать файл $name")

    private companion object {
        val CHILD_COLUMNS = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
    }
}
