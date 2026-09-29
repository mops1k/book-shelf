package io.github.bookshelf.core.data.backup

import io.github.bookshelf.core.common.di.TempDir
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.common.error.errorOrNull
import io.github.bookshelf.core.common.error.appRunCatching
import io.github.bookshelf.core.common.error.getOrNull
import io.github.bookshelf.core.common.time.AppClock
import io.github.bookshelf.core.data.repository.SourceRepository
import io.github.bookshelf.core.datastore.SettingsRepository
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Копии базы в папке библиотеки.
 *
 * Папка выбрана не случайно: база лежит в приватном каталоге приложения и при удалении приложения
 * пропадает вместе с полками и прогрессом чтения. Копия в папке библиотеки (SAF) переживает
 * переустановку и видна пользователю, поэтому именно она имеет смысл.
 *
 * Снимок снимается средствами SQLite ([DatabaseSnapshot]) — файл, скопированный во время записи,
 * может оказаться битым. Копий хранится немного ([KEEP_BACKUPS]): это не архив, а страховка на
 * случай неудачной переустановки.
 */
class DefaultDatabaseBackup @Inject constructor(
    private val sources: SourceRepository,
    private val settings: SettingsRepository,
    private val snapshot: DatabaseSnapshot,
    private val store: BackupStore,
    @TempDir private val tempDir: File,
    private val clock: AppClock,
) : DatabaseBackup {

    /**
     * Сделать копию.
     *
     * @param automatic запуск по расписанию: подчиняется настройке `advanced.dbBackupEnabled`.
     *   Кнопка в настройках работает всегда — человек нажал её осознанно, и запрещать ему
     *   собственную копию из-за выключенного расписания было бы странно.
     */
    override suspend fun backupNow(automatic: Boolean): AppResult<BackupResult> {
        if (automatic && !settings.current().advanced.dbBackupEnabled) {
            return AppResult.failure(
                AppError.PermissionDenied(
                    message = "Автоматический бэкап выключен в настройках",
                ),
            )
        }
        val treeUri = libraryTreeUri()
            ?: return AppResult.failure(
                AppError.NotFound(
                    what = "library",
                    message = "Папка библиотеки не выбрана: копию некуда положить",
                ),
            )

        val fileName = BackupNames.fileNameFor(clock.nowMillis())
        val target = File(tempDir, fileName)
        return try {
            // Исключения превращаются в результат общим помощником проекта: сбой снимка — это
            // «копия не сделана» с причиной, а не падение приложения.
            val made = appRunCatching { snapshot.snapshotTo(target) }
            made.errorOrNull()?.let { error -> return AppResult.failure(error) }
            val read = appRunCatching { target.readBytes() }
            val bytes = read.getOrNull()
                ?: return AppResult.failure(read.errorOrNull() ?: AppError.Io(message = "Копия не читается"))
            store.save(treeUri = treeUri, folder = BackupNames.FOLDER, fileName = fileName, bytes = bytes)
                .errorOrNull()
                ?.let { error -> return AppResult.failure(error) }
            AppResult.success(
                BackupResult(
                    fileName = fileName,
                    sizeBytes = bytes.size.toLong(),
                    removed = removeOldCopies(treeUri),
                ),
            )
        } finally {
            target.delete()
        }
    }

    override suspend fun availableBackups(): AppResult<List<BackupEntry>> {
        val treeUri = libraryTreeUri()
            ?: return AppResult.failure(
                AppError.NotFound(what = "library", message = "Папка библиотеки не выбрана"),
            )
        val listed = store.list(treeUri)
        val entries = listed.getOrNull()
            ?: return AppResult.failure(
                listed.errorOrNull() ?: AppError.Io(message = "Список копий не получен"),
            )
        return AppResult.success(entries.sortedByDescending { entry -> entry.createdAt })
    }

    override suspend fun restore(entry: BackupEntry): AppResult<Unit> {
        val treeUri = libraryTreeUri()
            ?: return AppResult.failure(
                AppError.NotFound(what = "library", message = "Папка библиотеки не выбрана"),
            )
        val file = store.read(entry).getOrNull()
            ?: return AppResult.failure(
                store.read(entry).errorOrNull()
                    ?: AppError.Io(message = "Копию не удалось прочитать"),
            )
        // Проверка до подмены: испорченная копия не должна затирать рабочую базу.
        val damage = snapshot.integrityMessage(file)
        if (damage != null) {
            file.delete()
            return AppResult.failure(
                AppError.Io(message = "Копия повреждена: $damage"),
            )
        }
        // Текущая база сохраняется отдельно: ошибка выбора должна быть обратимой.
        backupNow(automatic = false).errorOrNull()?.let { error -> return AppResult.failure(error) }
        val replaced = store.replaceDatabase(file)
        file.delete()
        return replaced
    }

    /** Папка первого включённого источника книг: туда и кладём копии. */
    private suspend fun libraryTreeUri(): String? =
        sources.observeSources().first().firstOrNull { source -> source.enabled }?.uri

    /**
     * Оставить [KEEP_BACKUPS] самых свежих копий.
     *
     * Ошибки удаления не срывают бэкап: копия сделана, а лишний файл — не повод сообщать о сбое.
     */
    private suspend fun removeOldCopies(treeUri: String): Int {
        val entries = store.list(treeUri).getOrNull().orEmpty()
            .sortedByDescending { it.createdAt }
        val extra = entries.drop(BackupNames.KEEP)
        extra.forEach { entry -> store.remove(entry) }
        return extra.size
    }

}
