package io.github.bookshelf.feature.settings

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.app.AppRestarter
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.data.backup.BackupEntry
import io.github.bookshelf.core.data.backup.BackupResult
import io.github.bookshelf.core.data.backup.DatabaseBackup
import io.github.bookshelf.core.data.backup.DatabaseBackupSchedule
import io.github.bookshelf.core.datastore.SettingsRepository
import io.github.bookshelf.core.datastore.model.AdvancedSettings
import io.github.bookshelf.core.datastore.model.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Копии базы глазами экрана (`DatabaseBackupViewModel`).
 *
 * Проверяются решения, за которые отвечает только экран: восстановление не начинается без
 * подтверждения (иначе одно касание затирает библиотеку), после успеха приложение перезапускается,
 * а выключение ежедневной копии немедленно пересматривает расписание — иначе работа сработала бы
 * после запрета.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DatabaseBackupViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val backup = FakeDatabaseBackup()
    private val settings = FakeSettingsRepository()
    private val schedule = FakeSchedule()
    private val restarter = FakeRestarter()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `auto backup switch writes the setting and reviews the schedule`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onSetAutoEnabled(false)
        advanceUntilIdle()

        assertThat(settings.current().advanced.dbBackupEnabled).isFalse()
        // Расписание пересматривается сразу: выключенная настройка снимает уже поставленную работу.
        assertThat(schedule.reviews).isEqualTo(1)
    }

    @Test
    fun `create backup reports the made copy`() = runTest(dispatcher) {
        backup.result = BackupResult(fileName = "bookshelf-backup-7.db", sizeBytes = 1_024L, removed = 0)
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onCreateBackup()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.message).contains("bookshelf-backup-7.db")
        assertThat(viewModel.uiState.value.isWorking).isFalse()
    }

    @Test
    fun `failed copy is explained to the reader`() = runTest(dispatcher) {
        backup.copyError = AppError.NotFound(
            what = "library",
            message = "Папка библиотеки не выбрана: копию некуда положить",
        )
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onCreateBackup()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.message).contains("некуда положить")
    }

    @Test
    fun `restore waits for confirmation before touching the database`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onAskRestore(entry("копия.db", createdAt = 5L))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.pendingRestore?.fileName).isEqualTo("копия.db")
        // База не тронута: одно касание по списку не должно затирать библиотеку.
        assertThat(backup.restored).isEmpty()
        assertThat(restarter.restarts).isEqualTo(0)
    }

    @Test
    fun `confirmed restore replaces the database and restarts the app`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onAskRestore(entry("копия.db", createdAt = 5L))
        advanceUntilIdle()

        viewModel.onConfirmRestore()
        advanceUntilIdle()

        assertThat(backup.restored).hasSize(1)
        // После подмены файла процесс обязан перезапуститься: открытые соединения держат прежние данные.
        assertThat(restarter.restarts).isEqualTo(1)
    }

    @Test
    fun `cancelled restore clears the choice`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onAskRestore(entry("копия.db", createdAt = 5L))
        advanceUntilIdle()

        viewModel.onCancelRestore()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.pendingRestore).isNull()
        assertThat(backup.restored).isEmpty()
    }

    @Test
    fun `damaged copy is reported instead of a silent restart`() = runTest(dispatcher) {
        backup.restoreError = AppError.Io(message = "Копия повреждена: database disk image is malformed")
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onAskRestore(entry("битая.db", createdAt = 5L))
        advanceUntilIdle()

        viewModel.onConfirmRestore()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.message).contains("повреждена")
        assertThat(restarter.restarts).isEqualTo(0)
    }

    @Test
    fun `available copies are shown on start`() = runTest(dispatcher) {
        backup.available = listOf(entry("новая.db", createdAt = 20L), entry("старая.db", createdAt = 10L))

        val viewModel = viewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.backups.map { it.fileName })
            .containsExactly("новая.db", "старая.db")
    }

    private fun viewModel() = DatabaseBackupViewModel(
        backup = backup,
        settingsRepository = settings,
        schedule = schedule,
        restarter = restarter,
    )

    private fun entry(fileName: String, createdAt: Long) = BackupEntry(
        documentUri = "content://tree/document/$fileName",
        fileName = fileName,
        createdAt = createdAt,
        sizeBytes = 512L,
    )

    private class FakeDatabaseBackup : DatabaseBackup {

        var result: BackupResult = BackupResult(fileName = "копия.db", sizeBytes = 1L, removed = 0)
        var copyError: AppError? = null
        var restoreError: AppError? = null
        var available: List<BackupEntry> = emptyList()
        val restored = mutableListOf<BackupEntry>()

        override suspend fun backupNow(automatic: Boolean): AppResult<BackupResult> {
            val failure = copyError
            return if (failure != null) AppResult.failure(failure) else AppResult.success(result)
        }

        override suspend fun availableBackups(): AppResult<List<BackupEntry>> =
            AppResult.success(available)

        override suspend fun restore(entry: BackupEntry): AppResult<Unit> {
            val failure = restoreError
            if (failure != null) return AppResult.failure(failure)
            restored += entry
            return AppResult.success(Unit)
        }
    }

    private class FakeSchedule : DatabaseBackupSchedule {

        var reviews: Int = 0

        override suspend fun review() {
            reviews++
        }
    }

    private class FakeRestarter : AppRestarter {

        var restarts: Int = 0

        override fun restart() {
            restarts++
        }
    }

    private class FakeSettingsRepository : SettingsRepository {

        private val state = MutableStateFlow(AppSettings())

        override val settings: Flow<AppSettings> = state

        override suspend fun current(): AppSettings = state.value

        override suspend fun update(transform: (AppSettings) -> AppSettings) {
            state.value = transform(state.value)
        }

        override suspend fun resetToDefaults() {
            state.value = AppSettings(advanced = AdvancedSettings())
        }
    }
}
