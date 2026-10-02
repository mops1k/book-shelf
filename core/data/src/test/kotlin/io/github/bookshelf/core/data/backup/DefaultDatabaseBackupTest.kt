package io.github.bookshelf.core.data.backup

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.common.error.errorOrNull
import io.github.bookshelf.core.common.error.getOrNull
import io.github.bookshelf.core.common.testing.FakeAppClock
import io.github.bookshelf.core.data.repository.SourceRepository
import io.github.bookshelf.core.data.model.ScanProgress
import io.github.bookshelf.core.data.model.ScanReport
import io.github.bookshelf.core.data.model.Source
import io.github.bookshelf.core.data.model.SourceKind
import io.github.bookshelf.core.datastore.SettingsRepository
import io.github.bookshelf.core.datastore.model.AdvancedSettings
import io.github.bookshelf.core.datastore.model.AppSettings
import io.github.bookshelf.core.storage.files.BookContentOpener
import io.github.bookshelf.core.storage.saf.DocumentEntry
import io.github.bookshelf.core.storage.saf.SafTreeReader
import io.github.bookshelf.core.storage.saf.SafTreeWriter
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Оркестрация копии базы и восстановления (`DefaultDatabaseBackup`).
 *
 * Проверяются решения, которые нельзя увидеть на устройстве: копия не делается при выключенной
 * настройке, при отсутствии папки библиотеки сообщается причина (а не «не удалось»), лишние копии
 * удаляются, а восстановление отказывается работать с битым файлом. Ошибка здесь стоит человеку
 * библиотеки, поэтому каждый отказ должен быть объяснён.
 */
class DefaultDatabaseBackupTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val sources = FakeSourceRepository()
    private val settings = FakeSettingsRepository()
    private val snapshot = FakeSnapshot()
    private val store = FakeBackupStore()
    private val clock = FakeAppClock()

    @Test
    fun `automatic backup respects the setting`() = runBlocking {
        settings.backupEnabled = false
        val backup = backup()

        val result = backup.backupNow(automatic = true)

        assertThat(result.getOrNull()).isNull()
        assertThat(result.errorOrNull()?.message).contains("выключен")
        assertThat(snapshot.calls).isEqualTo(0)
        assertThat(store.saved).isEmpty()
    }

    @Test
    fun `manual backup works even when the schedule is off`() = runBlocking {
        settings.backupEnabled = false
        val backup = backup()

        // Кнопку человек нажал осознанно: запрещать ему копию из-за выключенного расписания нельзя.
        val result = backup.backupNow()

        assertThat(result.getOrNull()).isNotNull()
        assertThat(store.saved).hasSize(1)
    }

    @Test
    fun `backup without a library folder reports the reason`() = runBlocking {
        settings.backupEnabled = true
        sources.sourceList = emptyList()
        val backup = backup()

        val result = backup.backupNow()

        assertThat(result.errorOrNull()?.message).contains("не выбрана")
        assertThat(store.saved).isEmpty()
    }

    @Test
    fun `backup saves a copy into the library folder and reports its size`() = runBlocking {
        settings.backupEnabled = true
        snapshot.bytes = ByteArray(2_048) { 7 }
        val backup = backup()

        val result = backup.backupNow().getOrNull()

        assertThat(result?.sizeBytes).isEqualTo(2_048L)
        // Префикс имени — по нему копии отличаются от книг в папке библиотеки.
        assertThat(result?.fileName).startsWith("bookshelf-backup-")
        assertThat(store.saved).hasSize(1)
        assertThat(store.saved.single().folder).isEqualTo(BACKUP_FOLDER)
    }

    @Test
    fun `five copies are kept and the sixth removes the oldest`() = runBlocking<Unit> {
        settings.backupEnabled = true
        // Пять копий уже есть: новая — шестая, и уходит ровно самая старая.
        store.existing = (1..5).map { index -> entry("копия-$index.db", createdAt = index.toLong()) }
        val backup = backup()

        val result = backup.backupNow().getOrNull()

        assertThat(result?.removed).isEqualTo(1)
        assertThat(store.removed.map { it.fileName }).containsExactly("копия-1.db")
    }

    @Test
    fun `the fifth copy evicts nothing`() = runBlocking {
        settings.backupEnabled = true
        store.existing = (1..4).map { index -> entry("копия-$index.db", createdAt = index.toLong()) }
        val backup = backup()

        val result = backup.backupNow().getOrNull()

        // Предел — пять копий: пятая ещё входит в него, и удалять ничего не нужно.
        assertThat(result?.removed).isEqualTo(0)
        assertThat(store.removed).isEmpty()
    }

    @Test
    fun `deleting a copy removes exactly that file`() = runBlocking<Unit> {
        store.existing = listOf(
            entry("лишняя.db", createdAt = 3L),
            entry("нужная.db", createdAt = 4L),
        )
        val backup = backup()

        val result = backup.remove(store.existing.first())

        assertThat(result.getOrNull()).isNotNull()
        assertThat(store.removed.map { it.fileName }).containsExactly("лишняя.db")
        // Удалённая копия исчезает и из списка: строка на экране не должна остаться «мёртвой».
        assertThat(backup.availableBackups().getOrNull()?.map { it.fileName })
            .containsExactly("нужная.db")
    }

    @Test
    fun `failed deletion is reported with its cause`() = runBlocking {
        store.removeFailure = AppError.Io(message = "Нет доступа к папке")
        val backup = backup()

        val result = backup.remove(entry("лишняя.db", createdAt = 3L))

        assertThat(result.getOrNull()).isNull()
        assertThat(result.errorOrNull()?.message).isEqualTo("Нет доступа к папке")
    }

    @Test
    fun `available backups come newest first`() = runBlocking {
        store.existing = listOf(
            entry("старая.db", createdAt = 10L),
            entry("новая.db", createdAt = 20L),
        )
        val backup = backup()

        val found = backup.availableBackups().getOrNull()

        assertThat(found?.map { it.fileName }).containsExactly("новая.db", "старая.db").inOrder()
    }

    @Test
    fun `restore refuses a damaged copy`() = runBlocking {
        snapshot.integrityMessage = "database disk image is malformed"
        val backup = backup()

        val result = backup.restore(entry("битая.db", createdAt = 1L))

        assertThat(result.getOrNull()).isNull()
        assertThat(result.errorOrNull()?.message).contains("поврежд")
        // База не подменялась: испорченная копия не должна затирать рабочую.
        assertThat(store.replaced).isFalse()
    }

    @Test
    fun `restore keeps the current database as a safety copy`() = runBlocking {
        val backup = backup()

        val result = backup.restore(entry("хорошая.db", createdAt = 1L))

        assertThat(result.getOrNull()).isNotNull()
        // Текущая база сохранена до подмены: ошибка выбора должна быть обратимой.
        assertThat(store.saved).hasSize(1)
        assertThat(store.replaced).isTrue()
    }

    private fun backup() = DefaultDatabaseBackup(
        sources = sources,
        settings = settings,
        snapshot = snapshot,
        store = store,
        tempDir = temporaryFolder.newFolder("temp"),
        clock = clock,
    )

    private fun entry(fileName: String, createdAt: Long) = BackupEntry(
        documentUri = "content://tree/document/$fileName",
        fileName = fileName,
        createdAt = createdAt,
        sizeBytes = 1_024L,
    )

    private class FakeSnapshot : DatabaseSnapshot {

        var bytes: ByteArray = ByteArray(0)
        var calls: Int = 0
        var integrityMessage: String? = null

        override fun snapshotTo(file: File): File {
            calls++
            file.writeBytes(bytes)
            return file
        }

        override fun integrityMessage(file: File): String? = integrityMessage
    }

    private class FakeBackupStore : BackupStore {

        data class Saved(val folder: String, val fileName: String, val bytes: Int)

        var existing: List<BackupEntry> = emptyList()
        var replaced: Boolean = false
        var removeFailure: AppError? = null
        val saved = mutableListOf<Saved>()
        val removed = mutableListOf<BackupEntry>()

        override suspend fun list(treeUri: String): AppResult<List<BackupEntry>> =
            AppResult.success(existing)

        override suspend fun save(
            treeUri: String,
            folder: String,
            fileName: String,
            bytes: ByteArray,
        ): AppResult<Unit> {
            saved += Saved(folder = folder, fileName = fileName, bytes = bytes.size)
            // Записанная копия сразу видна в списке — как в настоящем дереве документов.
            existing = existing + BackupEntry(
                documentUri = "content://tree/document/$fileName",
                fileName = fileName,
                createdAt = Long.MAX_VALUE,
                sizeBytes = bytes.size.toLong(),
            )
            return AppResult.success(Unit)
        }

        override suspend fun read(entry: BackupEntry): AppResult<File> {
            val file = File.createTempFile("restore", ".db")
            file.writeBytes(ByteArray(16))
            return AppResult.success(file)
        }

        override suspend fun remove(entry: BackupEntry): AppResult<Unit> {
            removeFailure?.let { failure -> return AppResult.failure(failure) }
            removed += entry
            // Удалённая копия исчезает из списка — как в настоящем дереве документов.
            existing = existing.filterNot { item -> item.documentUri == entry.documentUri }
            return AppResult.success(Unit)
        }

        override fun replaceDatabase(file: File): AppResult<Unit> {
            replaced = true
            return AppResult.success(Unit)
        }
    }

    private class FakeSourceRepository : SourceRepository {

        var sourceList: List<Source> = listOf(
            Source(
                id = "source-1",
                uri = TREE_URI,
                displayName = "Books",
                kind = SourceKind.SAF_TREE,
            ),
        )

        override fun observeSources(): Flow<List<Source>> = flowOf(sourceList)

        override suspend fun addSafSource(uri: String, displayName: String): AppResult<Source> =
            AppResult.failure(AppError.Unknown(message = "не используется"))

        override suspend fun addDirectPathSource(path: String, displayName: String): AppResult<Source> =
            AppResult.failure(AppError.Unknown(message = "в этом тесте не используется"))

        override suspend fun addGrantedSource(uri: String, displayName: String): AppResult<Source> =
                AppResult.failure(AppError.Unknown(message = "в этом тесте не используется"))

        override suspend fun removeSource(sourceId: String): AppResult<Unit> =
            AppResult.failure(AppError.Unknown(message = "не используется"))

        override suspend fun rescan(sourceId: String): AppResult<Unit> =
            AppResult.failure(AppError.Unknown(message = "не используется"))

        override suspend fun hasWriteAccess(sourceId: String): Boolean = true
    }

    private class FakeSettingsRepository : SettingsRepository {

        var backupEnabled: Boolean = true

        override val settings: Flow<AppSettings> = flowOf(AppSettings())

        override suspend fun current(): AppSettings = AppSettings(
            advanced = AdvancedSettings(dbBackupEnabled = backupEnabled),
        )

        override suspend fun update(transform: (AppSettings) -> AppSettings) = Unit

        override suspend fun resetToDefaults() = Unit
    }

    private companion object {
        const val TREE_URI = "content://com.android.externalstorage.documents/tree/primary%3ABooks"
        const val BACKUP_FOLDER = ".bookshelf"
    }
}
