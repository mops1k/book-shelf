package io.github.bookshelf.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.bookshelf.core.common.app.AppRestarter
import io.github.bookshelf.core.common.error.errorOrNull
import io.github.bookshelf.core.common.error.getOrNull
import io.github.bookshelf.core.data.backup.BackupEntry
import io.github.bookshelf.core.data.backup.DatabaseBackup
import io.github.bookshelf.core.data.backup.DatabaseBackupSchedule
import io.github.bookshelf.core.datastore.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Состояние блока «Бэкап базы».
 *
 * @param isAutoEnabled включён ли ежедневный бэкап (настройка `advanced.dbBackupEnabled`).
 * @param isWorking идёт операция: копирование, список или восстановление.
 * @param backups найденные копии, новые первыми.
 * @param pendingRestore копия, выбранная для восстановления и ждущая подтверждения.
 * @param message итог последней операции для показа человеку.
 */
data class DatabaseBackupUiState(
    val isAutoEnabled: Boolean = true,
    val isWorking: Boolean = false,
    val backups: List<BackupEntry> = emptyList(),
    val pendingRestore: BackupEntry? = null,
    val message: String? = null,
)

/**
 * Копии базы: создание, список и восстановление.
 *
 * Отдельная ViewModel, а не часть общей: у общей уже 29 методов при пороге detekt 30, и складывать
 * туда ещё и восстановление с подтверждением было бы тесно. Здесь же видно всё, что относится к
 * копиям, в одном месте.
 */
@HiltViewModel
class DatabaseBackupViewModel @Inject constructor(
    private val backup: DatabaseBackup,
    private val settingsRepository: SettingsRepository,
    private val schedule: DatabaseBackupSchedule,
    private val restarter: AppRestarter,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DatabaseBackupUiState())
    val uiState: StateFlow<DatabaseBackupUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update { state ->
                    state.copy(isAutoEnabled = settings.advanced.dbBackupEnabled)
                }
            }
        }
        onLoadBackups()
    }

    /** Включить или выключить ежедневную копию. */
    fun onSetAutoEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.update { settings ->
                settings.copy(advanced = settings.advanced.copy(dbBackupEnabled = enabled))
            }
            // Расписание пересматривается сразу: выключенная настройка должна снять уже
            // поставленную работу, а не дождаться следующего запуска приложения.
            schedule.review()
            _uiState.update { state ->
                state.copy(
                    message = if (enabled) {
                        "Ежедневный бэкап включён"
                    } else {
                        "Ежедневный бэкап выключен"
                    },
                )
            }
        }
    }

    /** Сделать копию сейчас. */
    fun onCreateBackup() {
        if (_uiState.value.isWorking) return
        viewModelScope.launch {
            _uiState.update { state -> state.copy(isWorking = true, message = null) }
            val result = backup.backupNow()
            val created = result.getOrNull()
            _uiState.update { state ->
                state.copy(
                    isWorking = false,
                    message = if (created != null) {
                        "Копия сделана: ${created.fileName}" +
                            if (created.removed > 0) ", удалено старых: ${created.removed}" else ""
                    } else {
                        result.errorOrNull()?.message ?: "Копию не удалось сделать"
                    },
                )
            }
            onLoadBackups()
        }
    }

    /** Перечитать список копий. */
    fun onLoadBackups() {
        viewModelScope.launch {
            val listed = backup.availableBackups()
            _uiState.update { state ->
                state.copy(
                    backups = listed.getOrNull().orEmpty(),
                    message = state.message
                        ?: listed.errorOrNull()?.let { error -> error.message },
                )
            }
        }
    }

    /** Выбрать копию: восстановление требует подтверждения, поэтому сначала только вопрос. */
    fun onAskRestore(entry: BackupEntry) {
        _uiState.update { state -> state.copy(pendingRestore = entry, message = null) }
    }

    /** Отказаться от восстановления. */
    fun onCancelRestore() {
        _uiState.update { state -> state.copy(pendingRestore = null) }
    }

    /**
     * Подтвердить восстановление.
     *
     * После успеха приложение перезапускается: файл базы подменён под работающим процессом, и
     * открытые соединения держат прежние данные.
     */
    fun onConfirmRestore() {
        val entry = _uiState.value.pendingRestore ?: return
        viewModelScope.launch {
            _uiState.update { state -> state.copy(isWorking = true, pendingRestore = null) }
            val result = backup.restore(entry)
            if (result.getOrNull() != null) {
                _uiState.update { state ->
                    state.copy(isWorking = false, message = "База восстановлена, перезапускаем…")
                }
                restarter.restart()
            } else {
                _uiState.update { state ->
                    state.copy(
                        isWorking = false,
                        message = result.errorOrNull()?.message ?: "Восстановление не удалось",
                    )
                }
            }
        }
    }

    /** Убрать сообщение. */
    fun onDismissMessage() {
        _uiState.update { state -> state.copy(message = null) }
    }
}
