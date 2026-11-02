package io.github.bookshelf.feature.settings.ui.category

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import io.github.bookshelf.core.designsystem.component.EinkButton
import io.github.bookshelf.core.designsystem.theme.LocalEinkColors
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
import io.github.bookshelf.core.applist.rule.HomeRoleController
import io.github.bookshelf.core.datastore.launcher.LauncherStore
import io.github.bookshelf.core.datastore.model.AppListView
import io.github.bookshelf.core.datastore.model.HomeScreen
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

    /** Роль домашнего экрана: alias HOME и системный запрос роли (фаза 7). */
    private val homeRole = HomeRoleController(context)

    private val _homeRoleHeld = kotlinx.coroutines.flow.MutableStateFlow(false)

    /** Владеем ли ролью домашнего экрана прямо сейчас. */
    val homeRoleHeld: StateFlow<Boolean> = _homeRoleHeld.asStateFlow()

    private val _state = kotlinx.coroutines.flow.MutableStateFlow(LauncherState())

    /** Текущее состояние лаунчера для экрана настроек. */
    val state: StateFlow<LauncherState> = _state.asStateFlow()

    init {
        // Читаем хранилище целиком: настроек мало, и подписка на поток даёт живые обновления.
        _homeRoleHeld.value = homeRole.isHomeRoleHeld()
        viewModelScope.launch {
            launcherStore.state.collect { stored ->
                _state.value = stored
                // Компонент живёт отдельно от хранилища: приводим его к сохранённому значению,
                // чтобы после переустановки или сброса данных настройка не разошлась с фактом.
                val aliasEnabled = stored.launcherEnabled && stored.appsItemVisible
                if (appsAlias.isEnabled() != aliasEnabled) {
                    appsAlias.setEnabled(aliasEnabled)
                }
                // Выключили режим лаунчера — освобождаем и роль домашнего экрана: система снова
                // спросит, какой лаунчер выбрать (решение Александра 02.11.2026).
                if (!stored.launcherEnabled && homeRole.isAliasEnabled()) {
                    homeRole.setAliasEnabled(false)
                    _homeRoleHeld.value = homeRole.isHomeRoleHeld()
                }
            }
        }
    }

    /** Сетка иконок или список «маленькая иконка + название». */
    fun onSetViewMode(mode: AppListView) {
        viewModelScope.launch { launcherStore.update { current -> current.copy(viewMode = mode) } }
    }

    /** Что открывать по Home: список приложений или полку. */
    fun onSetHomeScreen(screen: HomeScreen) {
        viewModelScope.launch { launcherStore.update { current -> current.copy(homeScreen = screen) } }
    }

    /**
     * Подготовить запрос роли домашнего экрана и вернуть интент для системного диалога.
     *
     * Alias включаем до запроса: `RoleManager` предлагает только те пакеты, у которых объявлен
     * домашний экран. Если человек откажется, alias остаётся включённым — иначе следующий запрос
     * снова начинался бы с нуля, а в системных настройках нас бы не было.
     */
    fun onRequestHomeRole(): android.content.Intent {
        homeRole.setAliasEnabled(true)
        return homeRole.roleRequestIntent()
    }

    /** Диалог роли закрылся: перечитываем, кто теперь домашний экран. */
    fun onHomeRoleRequestFinished() {
        _homeRoleHeld.value = homeRole.isHomeRoleHeld()
    }

    /**
     * Включён ли режим лаунчера (решение 02.11.2026).
     *
     * Выключенный режим — это приложение-полка: ни экрана «Приложения», ни нижней панели, ни роли
     * домашнего экрана, ни пункта для кнопок устройства. Alias «Приложения» гасим сразу, чтобы
     * выключение не оставляло включённый компонент (роль HOME освобождает фаза 7).
     */
    fun onSetLauncherEnabled(enabled: Boolean) {
        if (!enabled) {
            appsAlias.setEnabled(false)
            homeRole.setAliasEnabled(false)
            _homeRoleHeld.value = homeRole.isHomeRoleHeld()
        }
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

/** Категория «Лаунчер»: режим, домашний экран, вид списка и системные приложения. */
@Composable
internal fun LauncherCategory(
    modifier: Modifier = Modifier,
    viewModel: LauncherSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val homeRoleHeld by viewModel.homeRoleHeld.collectAsStateWithLifecycle()
    val dimens = LocalEinkDimens.current
    // Системный диалог роли: после возврата перечитываем, кто теперь домашний экран.
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.onHomeRoleRequestFinished()
    }

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
        homeItems(
            state = state,
            homeRoleHeld = homeRoleHeld,
            onRequestRole = { roleLauncher.launch(viewModel.onRequestHomeRole()) },
            onSetHomeScreen = viewModel::onSetHomeScreen,
        )
        viewItems(state = state, onSetViewMode = viewModel::onSetViewMode)
        appsItemItems(
            state = state,
            onSetAppsItemVisible = viewModel::onSetAppsItemVisible,
        )
        systemItems(state = state, onSetShowSystemApps = viewModel::onSetShowSystemApps)
    }
}

/** Роль домашнего экрана и выбор экрана, который открывается по Home. */
private fun LazyListScope.homeItems(
    state: LauncherState,
    homeRoleHeld: Boolean,
    onRequestRole: () -> Unit,
    onSetHomeScreen: (HomeScreen) -> Unit,
) {
    item(key = "launcher-role") {
        HomeRoleRow(isHome = homeRoleHeld, onRequestRole = onRequestRole)
    }
    item(key = "launcher-home-screen") {
        ChoiceRow(
            title = stringResource(R.string.settings_launcher_home_screen),
            options = HomeScreen.entries.map { screen ->
                ChoiceOption(
                    id = screen.name,
                    labelRes = when (screen) {
                        HomeScreen.APPS -> R.string.settings_launcher_home_screen_apps
                        HomeScreen.SHELF -> R.string.settings_launcher_home_screen_shelf
                    },
                )
            },
            selected = state.homeScreen.name,
            onPick = { id -> onSetHomeScreen(HomeScreen.valueOf(id)) },
            subtitle = stringResource(R.string.settings_launcher_home_screen_hint),
        )
    }
}

/** Вид списка приложений: сетка или список. */
private fun LazyListScope.viewItems(state: LauncherState, onSetViewMode: (AppListView) -> Unit) {
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
            onPick = { id -> onSetViewMode(AppListView.valueOf(id)) },
            subtitle = stringResource(R.string.settings_launcher_view_hint),
        )
    }
}

/** Пункт «Приложения» в списке приложений устройства (для аппаратной кнопки). */
private fun LazyListScope.appsItemItems(
    state: LauncherState,
    onSetAppsItemVisible: (Boolean) -> Unit,
) {
    item(key = "launcher-apps-item") {
        ToggleRow(
            title = stringResource(R.string.settings_launcher_apps_item),
            subtitle = stringResource(R.string.settings_launcher_apps_item_hint),
            enabled = state.appsItemVisible,
            onToggle = onSetAppsItemVisible,
        )
    }
}

/** Системные приложения в списке приложений. */
private fun LazyListScope.systemItems(
    state: LauncherState,
    onSetShowSystemApps: (Boolean) -> Unit,
) {
    item(key = "launcher-system-apps") {
        ToggleRow(
            title = stringResource(R.string.settings_launcher_show_system),
            subtitle = stringResource(R.string.settings_launcher_show_system_hint),
            enabled = state.showSystemApps,
            onToggle = onSetShowSystemApps,
        )
    }
}

/**
 * Строка роли домашнего экрана: состояние и кнопка запроса.
 *
 * Системная настройка выбора лаунчера спрятана глубоко в настройках (жалоба Александра
 * 02.11.2026), поэтому запрос роли доступен прямо здесь: на Android 10+ откроется системный
 * диалог, ниже — настройки домашнего экрана.
 */
@Composable
private fun HomeRoleRow(isHome: Boolean, onRequestRole: () -> Unit) {
    val dimens = LocalEinkDimens.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(dimens.gutterSmall),
    ) {
        Text(
            text = if (isHome) {
                stringResource(R.string.settings_launcher_home_role_current)
            } else {
                stringResource(R.string.settings_launcher_home_role_not_current)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = LocalEinkColors.current.foreground,
        )
        EinkButton(
            text = stringResource(R.string.settings_launcher_home_role_button),
            onClick = onRequestRole,
        )
    }
}
