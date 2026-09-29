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
 * @param isWorking идёт операция: копирование, список, восстановление или удаление.
 * @param backups найденные копии, новые первыми.
 * @param selected копия, выбранная касанием: под её строкой раскрыты «Восстановить», «Удалить»
 *   и «Отмена». Одна копия — одно состояние, поэтому выбор общий для обоих действий.
 * @param message итог последней операции для показа человеку.
 */
data class DatabaseBackupUiState(
    val isAutoEnabled: Boolean = true,
    val isWorking: Boolean = false,
    val backups: List<BackupEntry> = emptyList(),
    val selected: BackupEntry? = null,
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

    /**
     * Выбрать копию: под её строкой раскрываются действия «Восстановить» / «Удалить» / «Отмена».
     *
     * Одно касание по списку ничего не делает с файлами — ни восстановления, ни удаления. Так и
     * промах пальцем, и осознанный выбор требуют второго нажатия, но лишних строк на экране нет:
     * у каждой копии одна строка, а не две.
     */
    fun onSelect(entry: BackupEntry) {
        _uiState.update { state -> state.copy(selected = entry, message = null) }
    }

    /** Закрыть действия выбранной копии, ничего с ней не делая. */
    fun onCancelSelection() {
        _uiState.update { state -> state.copy(selected = null) }
    }

    /**
     * Подтвердить восстановление выбранной копии.
     *
     * После успеха приложение перезапускается: файл базы подменён под работающим процессом, и
     * открытые соединения держат прежние данные.
     */
    fun onConfirmRestore() {
        val entry = _uiState.value.selected ?: return
        viewModelScope.launch {
            _uiState.update { state -> state.copy(isWorking = true, selected = null) }
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

    /**
     * Удалить выбранную копию.
     *
     * Отдельного вопроса «точно удалить?» нет: действия появились только после касания по этой
     * копии, и «Удалить» нажато уже осознанно. Дальше строка честно сообщает, что именно удалено.
     */
    fun onDeleteSelected() {
        val entry = _uiState.value.selected ?: return
        viewModelScope.launch {
            _uiState.update { state -> state.copy(isWorking = true, selected = null) }
            val result = backup.remove(entry)
            _uiState.update { state ->
                state.copy(
                    isWorking = false,
                    message = if (result.getOrNull() != null) {
                        "Копия удалена: ${entry.fileName}"
                    } else {
                        result.errorOrNull()?.message ?: "Копию удалить не удалось"
                    },
                )
            }
            // Список перечитывается: удалённой копии не должно остаться строкой на экране.
            onLoadBackups()
        }
    }
}

