package io.github.bookshelf.feature.library.update

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
 * Проверка обновлений для полки и «О приложении» (ADR-018).
 *
 * Автоматическая проверка запускается при создании: `UpdateChecker` сам решает, положено ли
 * проверять сегодня, поэтому экран не считает сутки. Диалог показывается только когда версия
 * новее; «нет обновлений», «недавно проверяли» и «проверка выключена» молчат.
 *
 * Ручная проверка (`checkManually`) игнорирует троттлинг и всегда даёт ответ в интерфейс.
 */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val checker: UpdateChecker,
    private val settingsRepository: SettingsRepository,
    private val buildInfo: AppBuildInfo,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UpdateUiState())
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    init {
        runCheck(force = false)
    }

    /** Автоматическая проверка при показе полки: раз в сутки, молча, если новой версии нет. */
    fun onScreenShown() {
        if (_uiState.value.visible) return
        runCheck(force = false)
    }

    /** «Проверить обновления» из «О приложении»: троттлинг не действует, ответ обязателен. */
    fun checkManually() {
        runCheck(force = true)
    }

    /** «Позже»: диалог закрывается до следующей автоматической проверки (ADR-018). */
    fun onDismiss() {
        _uiState.value = _uiState.value.copy(visible = false, manualResult = null)
    }

    private fun runCheck(force: Boolean) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(checking = force, manualResult = null)
            val enabled = settingsRepository.settings.first().advanced.checkUpdates
            val version = buildInfo.version
            if (version == null) {
                // Версия сборки не разобралась: проверять не с чем сравнивать.
                _uiState.value = _uiState.value.copy(checking = false, manualResult = ManualCheckResult.UNAVAILABLE)
                return@launch
            }
            val result = checker.check(
                currentVersion = version,
                flavor = buildInfo.flavor,
                enabled = enabled,
                force = force,
            )
            _uiState.value = when (result) {
                is UpdateCheck.Available -> _uiState.value.copy(
                    checking = false,
                    visible = true,
                    versionName = result.release.version.toString(),
                    notes = result.release.notes,
                    pageUrl = result.release.pageUrl,
                )
                UpdateCheck.None -> _uiState.value.copy(
                    checking = false,
                    manualResult = if (force) ManualCheckResult.NO_UPDATE else null,
                )
                is UpdateCheck.Failed -> _uiState.value.copy(
                    checking = false,
                    manualResult = if (force) ManualCheckResult.FAILED else null,
                )
                UpdateCheck.Disabled, UpdateCheck.Throttled -> _uiState.value.copy(
                    checking = false,
                    manualResult = if (force) ManualCheckResult.UNAVAILABLE else null,
                )
            }
        }
    }
}
