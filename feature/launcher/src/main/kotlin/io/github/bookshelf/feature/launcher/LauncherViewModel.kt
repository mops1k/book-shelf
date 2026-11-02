package io.github.bookshelf.feature.launcher

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.bookshelf.core.applist.model.AppCatalog
import io.github.bookshelf.core.applist.model.InstalledApp
import io.github.bookshelf.core.datastore.launcher.LauncherStore
import io.github.bookshelf.core.datastore.model.AppListView
import io.github.bookshelf.core.datastore.model.AppSortMode
import io.github.bookshelf.core.datastore.model.LauncherState
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Экран «Приложения»: список, поиск, избранное, скрытие и запуск (ADR-015).
 *
 * Состояние собирается из двух источников: каталог (`PackageManager`) даёт приложения, хранилище
 * лаунчера — предпочтения. Экран не знает ни о `PackageManager`, ни о DataStore, поэтому
 * проверяется на фейках без Android (ADR-012).
 *
 * Запуск приложения не выполняется здесь: ViewModel возвращает `Intent`, а стартует его экран.
 * Так решение «что показывать» и «что делать с окном» остаются раздельными, а счётчик запусков
 * обновляется ровно в момент, когда пользователь нажал на иконку.
 */
@HiltViewModel
class LauncherViewModel @Inject constructor(
    private val catalog: AppCatalog,
    private val launcherStore: LauncherStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LauncherUiState(loading = true))
    val uiState: StateFlow<LauncherUiState> = _uiState.asStateFlow()

    init {
        load()
        observeLauncherState()
    }

    /** Перечитать список приложений: нужен после установки или удаления приложения. */
    fun onRetry() = load()

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    fun onToggleFavorite(app: InstalledApp) {
        updateLauncherState { state ->
            val favorites = if (app.packageName in state.favoritePackages) {
                state.favoritePackages - app.packageName
            } else {
                state.favoritePackages + app.packageName
            }
            state.copy(favoritePackages = favorites)
        }
    }

    fun onHide(app: InstalledApp) {
        updateLauncherState { state -> state.copy(hiddenPackages = state.hiddenPackages + app.packageName) }
    }

    fun onUnhide(packageName: String) {
        updateLauncherState { state -> state.copy(hiddenPackages = state.hiddenPackages - packageName) }
    }

    fun onSetSortMode(mode: AppSortMode) {
        updateLauncherState { state -> state.copy(sortMode = mode) }
    }

    /** Сетка иконок или список «маленькая иконка + название» (требование 02.11.2026). */
    fun onSetViewMode(mode: AppListView) {
        updateLauncherState { state -> state.copy(viewMode = mode) }
    }

    fun onSetShowSystemApps(show: Boolean) {
        updateLauncherState { state -> state.copy(showSystemApps = show) }
    }

    /**
     * Интент запуска приложения; `null` — запустить нельзя, и экран покажет баннер.
     *
     * Счётчик увеличивается до старта: если пользователь передумает и вернётся, запуск всё равно
     * состоялся, и «по частоте» он должен учитываться.
     */
    fun launchIntent(app: InstalledApp): Intent? {
        val intent = catalog.launchIntent(app)
        if (intent == null) {
            _uiState.update { it.copy(error = LauncherError.LAUNCH_FAILED) }
            return null
        }
        viewModelScope.launch {
            launcherStore.update { state -> state.withLaunch(app.packageName) }
        }
        return intent
    }

    /** Пользователь увидел сообщение об ошибке — убираем баннер из состояния. */
    fun onErrorShown() {
        _uiState.update { it.copy(error = null) }
    }

    private fun load() {
        _uiState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { catalog.apps() }
                .onSuccess { apps ->
                    _uiState.update { it.copy(apps = apps, ownPackage = catalog.ownPackage, loading = false) }
                }
                .onFailure {
                    _uiState.update { state -> state.copy(loading = false, error = LauncherError.LOAD_FAILED) }
                }
        }
    }

    private fun observeLauncherState() {
        viewModelScope.launch {
            launcherStore.state.collect { state ->
                _uiState.update { current ->
                    current.copy(
                        sortMode = state.sortMode,
                        viewMode = state.viewMode,
                        showSystemApps = state.showSystemApps,
                        launcherEnabled = state.launcherEnabled,
                        hideTabBar = state.hideTabBar,
                        favorites = state.favoritePackages,
                        hidden = state.hiddenPackages,
                        launchCounts = state.launchCounts,
                    )
                }
            }
        }
    }

    private fun updateLauncherState(transform: (LauncherState) -> LauncherState) {
        viewModelScope.launch { launcherStore.update(transform) }
    }
}
