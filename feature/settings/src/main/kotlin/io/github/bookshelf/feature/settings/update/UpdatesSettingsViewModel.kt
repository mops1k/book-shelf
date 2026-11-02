package io.github.bookshelf.feature.settings.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.bookshelf.core.datastore.SettingsRepository
import io.github.bookshelf.core.update.checker.UpdateCheck
import io.github.bookshelf.core.update.checker.UpdateChecker
import io.github.bookshelf.core.update.model.AppBuildInfo
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Ручная проверка обновлений в «О приложении» (ADR-018).
 *
 * Троттлинг не действует: пользователь нажал кнопку и ждёт ответа, а не «проверяли сутки назад».
 * Отдельный от полки ViewModel, потому что ответ здесь — строка результата, а не диалог.
 */
@HiltViewModel
class UpdatesSettingsViewModel @Inject constructor(
    private val checker: UpdateChecker,
    private val settingsRepository: SettingsRepository,
    private val buildInfo: AppBuildInfo,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UpdatesSettingsUiState())
    val uiState: StateFlow<UpdatesSettingsUiState> = _uiState.asStateFlow()

    /** «Проверить обновления»: всегда обращается к сети и всегда показывает результат. */
    fun onCheck() {
        if (_uiState.value.checking) return
        // Состояние «проверяю» ставим до запуска корутины: иначе второе нажатие до её старта
        // пройдёт мимо защиты и уйдёт вторым запросом (тест second_press_while_checking_is_ignored).
        _uiState.value = UpdatesSettingsUiState(checking = true)
        viewModelScope.launch {
            val version = buildInfo.version
            if (version == null) {
                _uiState.value = UpdatesSettingsUiState(result = UpdateResult.UNAVAILABLE)
                return@launch
            }
            val enabled = settingsRepository.settings.first().advanced.checkUpdates
            val result = checker.check(
                currentVersion = version,
                flavor = buildInfo.flavor,
                enabled = enabled,
                force = true,
            )
            _uiState.value = when (result) {
                is UpdateCheck.Available -> UpdatesSettingsUiState(
                    result = UpdateResult.AVAILABLE,
                    availableVersion = result.release.version.toString(),
                )
                UpdateCheck.None -> UpdatesSettingsUiState(result = UpdateResult.NO_UPDATE)
                is UpdateCheck.Failed -> UpdatesSettingsUiState(result = UpdateResult.FAILED)
                UpdateCheck.Disabled, UpdateCheck.Throttled -> UpdatesSettingsUiState(
                    result = if (result == UpdateCheck.Disabled) UpdateResult.UNAVAILABLE else UpdateResult.NO_UPDATE,
                )
            }
        }
    }
}

/** Состояние ручной проверки: идёт запрос и чем он закончился. */
data class UpdatesSettingsUiState(
    val checking: Boolean = false,
    val result: UpdateResult? = null,
    /** Версия, доступная для установки (при [UpdateResult.AVAILABLE]). */
    val availableVersion: String = "",
)

/** Ответ ручной проверки для строки в «О приложении» (ADR-018). */
enum class UpdateResult {
    /** Новее нет. */
    NO_UPDATE,

    /** Есть версия новее: номер в `availableVersion`. */
    AVAILABLE,

    /** Сеть недоступна или GitHub не ответил. */
    FAILED,

    /** Проверка выключена настройкой или сборка из Google Play. */
    UNAVAILABLE,
}
