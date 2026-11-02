package io.github.bookshelf.feature.launcher.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
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
import io.github.bookshelf.core.designsystem.component.clickableNoIndication
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
    /** Переход в настройки по шестерёнке рядом с поиском. */
    onOpenSettings: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Выбранное долгим нажатием приложение: пока не null, рядом с иконкой висит меню действий.
    var actionApp by remember { mutableStateOf<InstalledApp?>(null) }
    var actionAnchor by remember { mutableStateOf(AppAnchor()) }

    LauncherContent(
        state = state,
        modifier = modifier,
        actionApp = actionApp,
        actionAnchor = actionAnchor,
        onQueryChange = viewModel::onQueryChange,
        onLaunch = { app -> launch(context, viewModel.launchIntent(app)) },
        onLongPress = { app, anchor ->
            actionApp = app
            actionAnchor = anchor
        },
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
        onOpenSettings = onOpenSettings,
    )
}

/** Содержимое экрана без Android-вызовов: запуск, «о приложении» и удаление приходят колбэками. */
@Composable
private fun LauncherContent(
    state: LauncherUiState,
    actionApp: InstalledApp?,
    actionAnchor: AppAnchor,
    onQueryChange: (String) -> Unit,
    onLaunch: (InstalledApp) -> Unit,
    onLongPress: (InstalledApp, AppAnchor) -> Unit,
    onDismissActions: () -> Unit,
    onAbout: (InstalledApp) -> Unit,
    onDelete: (InstalledApp) -> Unit,
    onHide: (InstalledApp) -> Unit,
    onToggleFavorite: (InstalledApp) -> Unit,
    onRetry: () -> Unit,
    onErrorShown: () -> Unit,
    onOpenShelf: () -> Unit,
    onOpenSettings: () -> Unit,
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
                // Открытое меню закрывается первым: BACK не должен уносить на полку.
                EinkAction.BACK -> if (actionApp != null) onDismissActions() else onOpenShelf()
                else -> Unit
            }
        },
        bottomBar = { EinkLauncherTabBar(selectedApps = true, onSelectShelf = onOpenShelf, onSelectApps = {}) },
    ) {
        LauncherSearch(
            query = state.query,
            onQueryChange = onQueryChange,
            onOpenSettings = onOpenSettings,
        )
        StateBanners(state = state, onRetry = onRetry, onErrorShown = onErrorShown)
        if (state.visible.isEmpty()) {
            EmptyList(state = state)
        } else if (isList) {
            AppRows(state = state, listState = listState, onLaunch = onLaunch, onLongPress = onLongPress)
        } else {
            AppGrid(state = state, gridState = gridState, onLaunch = onLaunch, onLongPress = onLongPress)
        }

        // Меню долгого нажатия — карточка у иконки (как в вендорском Launcher3), сетка остаётся
        // за ней: видно, к какому приложению относятся действия.
        if (actionApp != null) {
            AppActionsMenu(
                app = actionApp,
                state = state,
                anchor = actionAnchor,
                onOpen = { onLaunch(actionApp) },
                onAbout = { onAbout(actionApp) },
                onDelete = { onDelete(actionApp) },
                onHide = { onHide(actionApp) },
                onToggleFavorite = { onToggleFavorite(actionApp) },
                onDismiss = onDismissActions,
            )
        }
    }
}

/**
 * Строка поиска с шестерёнкой настроек в одной строке (ADR-015, «E-ink UX» §6.9).
 *
 * Подписи «Поиск» нет: место подсказки занимает сам плейсхолдер «Найти приложение», а шестерёнка
 * ведёт в настройки — отдельный пункт меню на этом экране не нужен.
 */
@Composable
private fun LauncherSearch(query: String, onQueryChange: (String) -> Unit, onOpenSettings: () -> Unit) {
    val dimens = LocalEinkDimens.current
    Row(
        // Высота строки — ровно высота поля ввода: кнопка настроек тянется по ней и остаётся
        // квадратной, иначе рядом с полем торчит плашка другого размера.
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(horizontal = dimens.gutter, vertical = dimens.gutterSmall),
        horizontalArrangement = Arrangement.spacedBy(dimens.gutterSmall),
    ) {
        EinkTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            placeholder = stringResource(R.string.launcher_search_placeholder),
        )
        SettingsButton(onClick = onOpenSettings, modifier = Modifier.fillMaxHeight())
    }
}

/** Кнопка с шестерёнкой: та же тап-зона, что у прочих действий, без ripple. */
@Composable
private fun SettingsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalEinkColors.current
    val dimens = LocalEinkDimens.current
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .background(colors.background)
            .border(width = dimens.borderAccent, color = colors.divider)
            .clickableNoIndication(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_launcher_settings),
            contentDescription = stringResource(R.string.launcher_open_settings),
            tint = colors.foreground,
            modifier = Modifier.size(dimens.touchMin / 2),
        )
    }
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
    onLongPress: (InstalledApp, AppAnchor) -> Unit,
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
                onLongClick = { anchor -> onLongPress(app, anchor) },
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
    onLongPress: (InstalledApp, AppAnchor) -> Unit,
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
            var anchor by remember { mutableStateOf(AppAnchor()) }
            EinkListRow(
                title = labelWithFavorite(app, state.favorites),
                leadingContent = { AppIcon(app = app, size = dimens.appListIcon) },
                onClick = { onLaunch(app) },
                onLongClick = { onLongPress(app, anchor) },
                modifier = Modifier.anchoredTo { anchor = it },
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
    onLongClick: (AppAnchor) -> Unit,
) {
    val colors = LocalEinkColors.current
    val dimens = LocalEinkDimens.current
    var anchor by remember { mutableStateOf(AppAnchor()) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .anchoredTo { anchor = it }
            .combinedClickableNoIndication(onClick = onClick, onLongClick = { onLongClick(anchor) }),
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

/**
 * Меню долгого нажатия — карточка у иконки (решение 02.11.2026).
 *
 * Раньше действия занимали весь экран вместо сетки; на e-ink это выглядело как отдельный экран и
 * терялась связь с приложением. Теперь карточка висит рядом с плиткой (как меню рабочего стола
 * Launcher3), закрывается касанием мимо и BACK. Позицию считает [MenuPlacement], размер карточки
 * уточняет система при измерении.
 */
@Composable
private fun AppActionsMenu(
    app: InstalledApp,
    state: LauncherUiState,
    anchor: AppAnchor,
    onOpen: () -> Unit,
    onAbout: () -> Unit,
    onDelete: () -> Unit,
    onHide: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dimens = LocalEinkDimens.current
    val density = LocalDensity.current
    val margin = with(density) { dimens.gutter.toPx().roundToInt() }
    val provider = remember(anchor, margin) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset = MenuPlacement.place(
                anchor = anchor.position,
                anchorSize = anchor.size,
                menuSize = popupContentSize,
                screenSize = windowSize,
                margin = margin,
            )
        }
    }
    Popup(
        popupPositionProvider = provider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = true,
            dismissOnClickOutside = true,
            dismissOnBackPress = true,
        ),
    ) {
        AppActionsCard(
            app = app,
            state = state,
            onOpen = onOpen,
            onAbout = onAbout,
            onDelete = onDelete,
            onHide = onHide,
            onToggleFavorite = onToggleFavorite,
        )
    }
}

/** Карточка меню: рамка, строки с разделителями — как меню рабочего стола на этом устройстве. */
@Composable
private fun AppActionsCard(
    app: InstalledApp,
    state: LauncherUiState,
    onOpen: () -> Unit,
    onAbout: () -> Unit,
    onDelete: () -> Unit,
    onHide: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val colors = LocalEinkColors.current
    val dimens = LocalEinkDimens.current
    Column(
        modifier = Modifier
            .width(dimens.menuWidth)
            .background(colors.background)
            .border(width = dimens.borderAccent, color = colors.divider),
    ) {
        MenuRow(text = stringResource(R.string.launcher_action_open), onClick = onOpen)
        MenuDivider()
        MenuRow(text = stringResource(R.string.launcher_action_about), onClick = onAbout)
        // Удаление системного приложения система всё равно не даст: пункт не показываем.
        if (!app.isSystem) {
            MenuDivider()
            MenuRow(text = stringResource(R.string.launcher_action_delete), onClick = onDelete)
        }
        MenuDivider()
        MenuRow(text = stringResource(R.string.launcher_action_hide), onClick = onHide)
        MenuDivider()
        MenuRow(
            text = if (app.packageName in state.favorites) {
                stringResource(R.string.launcher_action_unfavorite)
            } else {
                stringResource(R.string.launcher_action_favorite)
            },
            onClick = onToggleFavorite,
        )
    }
}

/** Строка меню: во всю ширину карточки, с комфортной тап-зоной и без «волны». */
@Composable
private fun MenuRow(text: String, onClick: () -> Unit) {
    val colors = LocalEinkColors.current
    val dimens = LocalEinkDimens.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(dimens.menuRowHeight)
            .clickableNoIndication(onClick = onClick)
            .padding(horizontal = dimens.gutter),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyLarge, color = colors.foreground)
    }
}

/** Разделитель строк меню: тонкая линия, как в вендорском меню. */
@Composable
private fun MenuDivider() {
    val colors = LocalEinkColors.current
    val dimens = LocalEinkDimens.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(dimens.border)
            .background(colors.divider),
    )
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
