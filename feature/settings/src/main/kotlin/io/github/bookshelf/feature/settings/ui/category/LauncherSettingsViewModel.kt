package io.github.bookshelf.feature.settings.ui.category

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import android.content.Context
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.bookshelf.core.applist.rule.AppsAliasController
import io.github.bookshelf.core.datastore.launcher.LauncherStore
import io.github.bookshelf.core.datastore.model.AppListView
import io.github.bookshelf.core.datastore.model.LauncherState
import io.github.bookshelf.core.designsystem.theme.LocalEinkDimens
import io.github.bookshelf.feature.settings.R
import io.github.bookshelf.feature.settings.ui.ChoiceOption
import io.github.bookshelf.feature.settings.ui.ChoiceRow
import io.github.bookshelf.feature.settings.ui.ToggleRow
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope

/**
 * Раздел настроек «Лаунчер» (ADR-015).
 *
 * Состав шире того, что здесь есть сейчас: домашний экран, пункт «Приложения» для кнопок
 * устройства и сворачивание появятся вместе с ролью HOME (фаза 7). Вид списка и системные
 * приложения сделаны сразу: без них список приложений не настроить — системные по умолчанию
 * скрыты, и файлового менеджера с настройками системы в списке не видно.
 *
 * Своя ViewModel, а не общая `SettingsViewModel`: у той уже десять зависимостей, и лаунчерное
 * хранилище — отдельный файл со своим жизненным циклом.
 */
@HiltViewModel
class LauncherSettingsViewModel @Inject constructor(
    private val launcherStore: LauncherStore,
    @ApplicationContext context: Context,
) : ViewModel() {

    /** Включение пункта «Приложения» в списке приложений устройства (фаза 6). */
    private val appsAlias = AppsAliasController(context)

    private val _state = kotlinx.coroutines.flow.MutableStateFlow(LauncherState())

    /** Текущее состояние лаунчера для экрана настроек. */
    val state: StateFlow<LauncherState> = _state.asStateFlow()

    init {
        // Читаем хранилище целиком: настроек мало, и подписка на поток даёт живые обновления.
        viewModelScope.launch {
            launcherStore.state.collect { stored ->
                _state.value = stored
                // Компонент живёт отдельно от хранилища: приводим его к сохранённому значению,
                // чтобы после переустановки или сброса данных настройка не разошлась с фактом.
                val aliasEnabled = stored.launcherEnabled && stored.appsItemVisible
                if (appsAlias.isEnabled() != aliasEnabled) {
                    appsAlias.setEnabled(aliasEnabled)
                }
            }
        }
    }

    /** Сетка иконок или список «маленькая иконка + название». */
    fun onSetViewMode(mode: AppListView) {
        viewModelScope.launch { launcherStore.update { current -> current.copy(viewMode = mode) } }
    }

    /**
     * Включён ли режим лаунчера (решение 02.11.2026).
     *
     * Выключенный режим — это приложение-полка: ни экрана «Приложения», ни нижней панели, ни роли
     * домашнего экрана, ни пункта для кнопок устройства. Alias «Приложения» гасим сразу, чтобы
     * выключение не оставляло включённый компонент (роль HOME освобождает фаза 7).
     */
    fun onSetLauncherEnabled(enabled: Boolean) {
        if (!enabled) appsAlias.setEnabled(false)
        viewModelScope.launch {
            launcherStore.update { current ->
                current.copy(
                    launcherEnabled = enabled,
                    // Пункт для кнопок — часть режима лаунчера: без него он не нужен.
                    appsItemVisible = if (enabled) current.appsItemVisible else false,
                )
            }
        }
    }

    /**
     * Пункт «Приложения» в списке приложений устройства.
     *
     * Нужен, чтобы назначить аппаратную кнопку: вендорский список показывает приложения, а не
     * компоненты (ADR-015). По умолчанию выключен — лишний пункт в чужом списке не нужен.
     */
    fun onSetAppsItemVisible(visible: Boolean) {
        appsAlias.setEnabled(visible)
        viewModelScope.launch { launcherStore.update { current -> current.copy(appsItemVisible = visible) } }
    }

    /** Показывать ли системные приложения (файловый менеджер, настройки, вендорские утилиты). */
    fun onSetShowSystemApps(show: Boolean) {
        viewModelScope.launch { launcherStore.update { current -> current.copy(showSystemApps = show) } }
    }
}

/** Категория «Лаунчер»: то, что уже работает, без пустых строк на будущее. */
@Composable
internal fun LauncherCategory(
    modifier: Modifier = Modifier,
    viewModel: LauncherSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dimens = LocalEinkDimens.current

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.gutter),
    ) {
        item(key = "launcher-enabled") {
            ToggleRow(
                title = stringResource(R.string.settings_launcher_enabled),
                subtitle = stringResource(R.string.settings_launcher_enabled_hint),
                enabled = state.launcherEnabled,
                onToggle = viewModel::onSetLauncherEnabled,
            )
        }
        // Выключенный режим лаунчера — приложение-полка: остальные настройки к нему не относятся.
        if (!state.launcherEnabled) return@LazyColumn
        item(key = "launcher-view") {
            ChoiceRow(
                title = stringResource(R.string.settings_launcher_view),
                options = AppListView.entries.map { mode ->
                    ChoiceOption(
                        id = mode.name,
                        labelRes = when (mode) {
                            AppListView.GRID -> R.string.settings_launcher_view_grid
                            AppListView.LIST -> R.string.settings_launcher_view_list
                        },
                    )
                },
                selected = state.viewMode.name,
                onPick = { id -> viewModel.onSetViewMode(AppListView.valueOf(id)) },
                subtitle = stringResource(R.string.settings_launcher_view_hint),
            )
        }
        item(key = "launcher-apps-item") {
            ToggleRow(
                title = stringResource(R.string.settings_launcher_apps_item),
                subtitle = stringResource(R.string.settings_launcher_apps_item_hint),
                enabled = state.appsItemVisible,
                onToggle = viewModel::onSetAppsItemVisible,
            )
        }
        item(key = "launcher-system-apps") {
            ToggleRow(
                title = stringResource(R.string.settings_launcher_show_system),
                subtitle = stringResource(R.string.settings_launcher_show_system_hint),
                enabled = state.showSystemApps,
                onToggle = viewModel::onSetShowSystemApps,
            )
        }
    }
}
