package io.github.bookshelf.core.data.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.bookshelf.core.common.di.TempDir
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.common.error.appRunCatching
import io.github.bookshelf.core.common.error.errorOrNull
import io.github.bookshelf.core.common.error.getOrNull
import io.github.bookshelf.core.common.error.isSuccess
import io.github.bookshelf.core.database.BookshelfDatabase
import io.github.bookshelf.core.storage.files.BookContentOpener
import io.github.bookshelf.core.storage.saf.SafTreeReader
import io.github.bookshelf.core.storage.saf.SafTreeWriter
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Копии базы в папке библиотеки через SAF.
 *
 * Адаптер намеренно тонкий: все решения (сколько копий хранить, что делать с битой копией) приняты
 * в `DefaultDatabaseBackup`, здесь только работа с деревом документов. Копии лежат в скрытой папке
 * `.bookshelf`, поэтому не попадают в библиотеку (сканер индексирует книги по расширениям) и не
 * мешают человеку среди его файлов.
 */
@Singleton
class SafBackupStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reader: SafTreeReader,
    private val writer: SafTreeWriter,
    private val opener: BookContentOpener,
    private val database: BookshelfDatabase,
    @TempDir private val tempDir: File,
) : BackupStore {

    override suspend fun list(treeUri: String): AppResult<List<BackupEntry>> {
        val folder = folderDocumentId(treeUri)
        if (!folder.isSuccess) {
            return AppResult.failure(
                folder.errorOrNull() ?: AppError.Io(message = "Папка библиотеки не читается"),
            )
        }
        // Папки копий может не быть вовсе: это не ошибка, а «копий пока нет».
        val folderId = folder.getOrNull() ?: return AppResult.success(emptyList())
        val children = reader.children(treeUri, folderId)
        val entries = children.getOrNull()
            ?: return AppResult.failure(
                children.errorOrNull() ?: AppError.Io(message = "Список копий не получен"),
            )
        return AppResult.success(
            entries
                .filter { entry -> !entry.isDirectory && BackupNames.isBackup(entry.displayName) }
                .mapNotNull { entry -> entry.toBackupEntry(treeUri) },
        )
    }

    override suspend fun save(
        treeUri: String,
        folder: String,
        fileName: String,
        bytes: ByteArray,
    ): AppResult<Unit> = writer
        .write(treeUri = treeUri, relativeDir = folder, fileName = fileName, bytes = bytes)
        .let { written ->
            if (written.getOrNull() != null) {
                AppResult.success(Unit)
            } else {
                AppResult.failure(
                    written.errorOrNull() ?: AppError.Io(message = "Копию не удалось записать"),
                )
            }
        }

    override suspend fun read(entry: BackupEntry): AppResult<File> = appRunCatching {
        val stream = opener.openStream(entry.documentUri)
            ?: error("Провайдер не отдал копию: ${entry.fileName}")
        val target = File(tempDir, entry.fileName)
        stream.use { input -> target.outputStream().use { output -> input.copyTo(output) } }
        target
    }

    override suspend fun remove(entry: BackupEntry): AppResult<Unit> = writer.delete(entry.documentUri)

    /**
     * Подменить базу приложением файлом копии.
     *
     * Порядок обязателен: сначала закрывается работающая база, затем копируется файл, затем
     * удаляется её журнал (`-wal`/`-shm`). Если оставить журнал, SQLite при следующем запуске
     * докатит на него старые данные и «восстановление» вернёт прежнюю базу.
     */
    override fun replaceDatabase(file: File): AppResult<Unit> = appRunCatching {
        database.close()
        val target = context.getDatabasePath(BookshelfDatabase.NAME)
        file.copyTo(target, overwrite = true)
        File("${target.path}-wal").delete()
        File("${target.path}-shm").delete()
    }

    /** Идентификатор папки копий; `null` — папки нет (это не ошибка). */
    private suspend fun folderDocumentId(treeUri: String): AppResult<String?> {
        val rootId = DocumentsContract.getTreeDocumentId(Uri.parse(treeUri))
        val children = reader.children(treeUri, rootId)
        val entries = children.getOrNull()
            ?: return AppResult.failure(
                children.errorOrNull() ?: AppError.Io(message = "Папка библиотеки не читается"),
            )
        return AppResult.success(
            entries.firstOrNull { entry ->
                entry.isDirectory && entry.displayName == BackupNames.FOLDER
            }?.documentId,
        )
    }

    private fun io.github.bookshelf.core.storage.saf.DocumentEntry.toBackupEntry(
        treeUri: String,
    ): BackupEntry? {
        val createdAt = BackupNames.createdAtOf(displayName) ?: return null
        return BackupEntry(
            documentUri = reader.documentUri(treeUri, documentId),
            fileName = displayName,
            createdAt = createdAt,
            sizeBytes = sizeBytes,
        )
    }
}
