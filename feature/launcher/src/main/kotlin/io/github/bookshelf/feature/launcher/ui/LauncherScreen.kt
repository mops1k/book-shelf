package io.github.bookshelf.feature.launcher.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.bookshelf.core.applist.icon.AppIconKey
import io.github.bookshelf.core.applist.model.InstalledApp
import io.github.bookshelf.core.datastore.model.AppListView
import io.github.bookshelf.core.designsystem.component.EinkButton
import io.github.bookshelf.core.designsystem.component.EinkEmptyState
import io.github.bookshelf.core.designsystem.component.EinkErrorBanner
import io.github.bookshelf.core.designsystem.component.EinkScaffold
import io.github.bookshelf.core.designsystem.component.EinkLauncherTabBar
import io.github.bookshelf.core.designsystem.component.EinkListRow
import io.github.bookshelf.core.designsystem.component.EinkSectionHeader
import io.github.bookshelf.core.designsystem.component.EinkTextField
import io.github.bookshelf.core.designsystem.component.combinedClickableNoIndication
import io.github.bookshelf.core.designsystem.input.EinkAction
import io.github.bookshelf.core.designsystem.paging.rememberEinkGridPageScroller
import io.github.bookshelf.core.designsystem.paging.rememberEinkListPageScroller
import io.github.bookshelf.core.designsystem.theme.LocalEinkColors
import io.github.bookshelf.core.designsystem.theme.LocalEinkDimens
import io.github.bookshelf.feature.launcher.LauncherError
import io.github.bookshelf.feature.launcher.LauncherUiState
import io.github.bookshelf.feature.launcher.LauncherViewModel
import io.github.bookshelf.feature.launcher.R

/**
 * Экран «Приложения»: сетка установленных приложений (ADR-015).
 *
 * Прогресс и меню живут не в диалогах: на e-ink диалог перерисовывает кадр целиком, а список
 * действий у выбранного приложения показывается на месте сетки — как «сортировка» на полке.
 * Долгое нажатие выбирает приложение, касание по иконке — запускает.
 */
@Composable
fun LauncherScreen(
    viewModel: LauncherViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
    /** Переход на полку нижней панелью (ADR-015); в отдельном запуске не используется. */
    onOpenShelf: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Выбранное долгим нажатием приложение: пока не null, вместо сетки — список действий.
    var actionApp by remember { mutableStateOf<InstalledApp?>(null) }

    LauncherContent(
        state = state,
        modifier = modifier,
        actionApp = actionApp,
        onQueryChange = viewModel::onQueryChange,
        onLaunch = { app -> launch(context, viewModel.launchIntent(app)) },
        onLongPress = { app -> actionApp = app },
        onDismissActions = { actionApp = null },
        onAbout = { app ->
            open(context, appDetailsIntent(app))
            actionApp = null
        },
        onDelete = { app ->
            open(context, Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}")))
            actionApp = null
        },
        onHide = { app ->
            viewModel.onHide(app)
            actionApp = null
        },
        onToggleFavorite = viewModel::onToggleFavorite,
        onRetry = viewModel::onRetry,
        onErrorShown = viewModel::onErrorShown,
        onOpenShelf = onOpenShelf,
    )
}

/** Содержимое экрана без Android-вызовов: запуск, «о приложении» и удаление приходят колбэками. */
@Composable
private fun LauncherContent(
    state: LauncherUiState,
    actionApp: InstalledApp?,
    onQueryChange: (String) -> Unit,
    onLaunch: (InstalledApp) -> Unit,
    onLongPress: (InstalledApp) -> Unit,
    onDismissActions: () -> Unit,
    onAbout: (InstalledApp) -> Unit,
    onDelete: (InstalledApp) -> Unit,
    onHide: (InstalledApp) -> Unit,
    onToggleFavorite: (InstalledApp) -> Unit,
    onRetry: () -> Unit,
    onErrorShown: () -> Unit,
    onOpenShelf: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    val scrollGrid = rememberEinkGridPageScroller(gridState)
    val scrollList = rememberEinkListPageScroller(listState)
    val isList = state.viewMode == AppListView.LIST

    EinkScaffold(
        // Шапки нет: вкладку «Приложения» видно на нижней панели, а строка отнимала бы место.
        title = null,
        modifier = modifier,
        onKeyAction = { action ->
            when (action) {
                EinkAction.NEXT_PAGE -> if (isList) scrollList(1) else scrollGrid(1)
                EinkAction.PREVIOUS_PAGE -> if (isList) scrollList(-1) else scrollGrid(-1)
                // Аппаратный и системный BACK: список приложений не корень — возвращаемся
                // на полку (ADR-015). Без этой ветки клавиша поглощалась и ничего не делала.
                EinkAction.BACK -> onOpenShelf()
                else -> Unit
            }
        },
        bottomBar = { EinkLauncherTabBar(selectedApps = true, onSelectShelf = onOpenShelf, onSelectApps = {}) },
    ) {
        LauncherSearch(query = state.query, onQueryChange = onQueryChange)
        StateBanners(state = state, onRetry = onRetry, onErrorShown = onErrorShown)
        if (actionApp != null) {
            ActionsPanel(
                app = actionApp,
                state = state,
                onAbout = { onAbout(actionApp) },
                onDelete = { onDelete(actionApp) },
                onHide = { onHide(actionApp) },
                onToggleFavorite = { onToggleFavorite(actionApp) },
                onDismiss = onDismissActions,
            )
        } else if (state.visible.isEmpty()) {
            EmptyList(state = state)
        } else if (isList) {
            AppRows(state = state, listState = listState, onLaunch = onLaunch, onLongPress = onLongPress)
        } else {
            AppGrid(state = state, gridState = gridState, onLaunch = onLaunch, onLongPress = onLongPress)
        }
    }
}

/** Строка поиска: всегда на экране, без скрытия и анимаций (ADR-015, «E-ink UX» §6.9). */
@Composable
private fun LauncherSearch(query: String, onQueryChange: (String) -> Unit) {
    val dimens = LocalEinkDimens.current
    EinkTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.gutter, vertical = dimens.gutterSmall),
        label = stringResource(R.string.launcher_search_label),
        placeholder = stringResource(R.string.launcher_search_placeholder),
    )
}

/** Баннеры ошибок и загрузки — над содержимым, а не поверх него. */
@Composable
private fun StateBanners(state: LauncherUiState, onRetry: () -> Unit, onErrorShown: () -> Unit) {
    val dimens = LocalEinkDimens.current
    when (state.error) {
        LauncherError.LOAD_FAILED -> EinkErrorBanner(
            message = stringResource(R.string.launcher_error_load),
            actionLabel = stringResource(R.string.launcher_action_retry),
            onAction = onRetry,
            modifier = Modifier.padding(horizontal = dimens.gutter),
        )

        LauncherError.LAUNCH_FAILED -> EinkErrorBanner(
            message = stringResource(R.string.launcher_error_launch),
            actionLabel = stringResource(R.string.launcher_action_close),
            onAction = onErrorShown,
            modifier = Modifier.padding(horizontal = dimens.gutter),
        )

        null -> Unit
    }
}

/** Сетка иконок с подписями; долгое нажатие выбирает приложение для меню действий. */
@Composable
private fun AppGrid(
    state: LauncherUiState,
    gridState: LazyGridState,
    onLaunch: (InstalledApp) -> Unit,
    onLongPress: (InstalledApp) -> Unit,
) {
    val dimens = LocalEinkDimens.current
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = dimens.appTileMinWidth),
        state = gridState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = dimens.gutter,
            vertical = dimens.gutterSmall,
        ),
        horizontalArrangement = Arrangement.spacedBy(dimens.gutterSmall),
        verticalArrangement = Arrangement.spacedBy(dimens.gutterSmall),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(items = state.visible, key = { app -> app.packageName }) { app ->
            AppTile(
                app = app,
                isFavorite = app.packageName in state.favorites,
                onClick = { onLaunch(app) },
                onLongClick = { onLongPress(app) },
            )
        }
    }
}

/**
 * Режим списка: маленькая иконка и название строкой (требование 02.11.2026).
 *
 * Строка выше комфортной тап-зоны, а иконка — 32 dp: список читается по названиям, как список
 * приложений в системных настройках, и на экран влезает больше, чем в сетке.
 */
@Composable
private fun AppRows(
    state: LauncherUiState,
    listState: LazyListState,
    onLaunch: (InstalledApp) -> Unit,
    onLongPress: (InstalledApp) -> Unit,
) {
    val dimens = LocalEinkDimens.current
    LazyColumn(
        state = listState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = dimens.gutter,
            vertical = dimens.gutterSmall,
        ),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(items = state.visible, key = { app -> app.packageName }) { app ->
            EinkListRow(
                title = labelWithFavorite(app, state.favorites),
                leadingContent = { AppIcon(app = app, size = dimens.appListIcon) },
                onClick = { onLaunch(app) },
                onLongClick = { onLongPress(app) },
            )
        }
    }
}

/** Иконка приложения из кэша Coil; размер задаёт вызывающий экран. */
@Composable
private fun AppIcon(app: InstalledApp, size: androidx.compose.ui.unit.Dp) {
    AsyncImage(
        model = AppIconKey(packageName = app.packageName, versionCode = app.versionCode),
        contentDescription = app.label,
        contentScale = ContentScale.Fit,
        modifier = Modifier.size(size),
    )
}

/** Подпись с отметкой избранного: звёздочка вместо цвета — на e-ink цвет не признак. */
private fun labelWithFavorite(app: InstalledApp, favorites: Set<String>): String =
    if (app.packageName in favorites) "★ ${app.label}" else app.label

/** Плитка: иконка и название (до двух строк) — решение 02.11.2026. */
@Composable
private fun AppTile(
    app: InstalledApp,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colors = LocalEinkColors.current
    val dimens = LocalEinkDimens.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickableNoIndication(onClick = onClick, onLongClick = onLongClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimens.gutterSmall),
    ) {
        Box(
            modifier = Modifier.size(dimens.appIcon),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(app = app, size = dimens.appIcon)
        }
        Text(
            text = labelWithFavorite(app, if (isFavorite) setOf(app.packageName) else emptySet()),
            style = MaterialTheme.typography.labelMedium,
            color = colors.foreground,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Список действий долгого нажатия: показывается на месте сетки, без диалога. */
@Composable
private fun ActionsPanel(
    app: InstalledApp,
    state: LauncherUiState,
    onAbout: () -> Unit,
    onDelete: () -> Unit,
    onHide: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dimens = LocalEinkDimens.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.gutter),
        verticalArrangement = Arrangement.spacedBy(dimens.gutterSmall),
    ) {
        EinkSectionHeader(title = app.label)
        EinkButton(text = stringResource(R.string.launcher_action_open), onClick = onDismiss)
        EinkButton(text = stringResource(R.string.launcher_action_about), onClick = onAbout)
        // Удаление системного приложения система всё равно не даст: пункт не показываем.
        if (!app.isSystem) {
            EinkButton(text = stringResource(R.string.launcher_action_delete), onClick = onDelete)
        }
        EinkButton(text = stringResource(R.string.launcher_action_hide), onClick = onHide)
        EinkButton(
            text = if (app.packageName in state.favorites) {
                stringResource(R.string.launcher_action_unfavorite)
            } else {
                stringResource(R.string.launcher_action_favorite)
            },
            onClick = onToggleFavorite,
        )
        EinkButton(text = stringResource(R.string.launcher_action_close), onClick = onDismiss)
    }
}

/** Пустой результат поиска и пустой список — разные сообщения, чтобы не гадать, что случилось. */
@Composable
private fun EmptyList(state: LauncherUiState) {
    EinkEmptyState(
        title = if (state.query.isBlank()) {
            stringResource(R.string.launcher_empty)
        } else {
            stringResource(R.string.launcher_empty_search)
        },
        message = stringResource(R.string.launcher_empty_hint),
    )
}

/** Запуск приложения; неудачный запуск уже отметил ViewModel — здесь только не падаем. */
private fun launch(context: Context, intent: Intent?) {
    if (intent != null) open(context, intent)
}

/** Открыть системный интент: `ActivityNotFoundException` — баннер через ViewModel, не падение. */
private fun open(context: Context, intent: Intent) {
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { error -> if (error is ActivityNotFoundException) return }
}

/** Экран «О приложении» в системных настройках. */
private fun appDetailsIntent(app: InstalledApp): Intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
    .setData(Uri.parse("package:${app.packageName}"))
