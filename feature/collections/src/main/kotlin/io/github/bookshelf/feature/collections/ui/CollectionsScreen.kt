package io.github.bookshelf.feature.collections.ui

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bookshelf.core.designsystem.component.BookCover
import io.github.bookshelf.core.designsystem.component.EinkButton
import io.github.bookshelf.core.designsystem.component.EinkEmptyState
import io.github.bookshelf.core.designsystem.component.EinkErrorBanner
import io.github.bookshelf.core.designsystem.component.EinkListRow
import io.github.bookshelf.core.designsystem.component.EinkScaffold
import io.github.bookshelf.core.designsystem.component.EinkSectionHeader
import io.github.bookshelf.core.designsystem.component.EinkTextField
import io.github.bookshelf.core.designsystem.input.EinkAction
import io.github.bookshelf.core.designsystem.paging.rememberEinkListPageScroller
import io.github.bookshelf.core.designsystem.theme.LocalEinkColors
import io.github.bookshelf.core.designsystem.theme.LocalEinkDimens
import io.github.bookshelf.feature.collections.CollectionUi
import io.github.bookshelf.feature.collections.CollectionsUiState
import io.github.bookshelf.feature.collections.CollectionsViewModel
import io.github.bookshelf.feature.collections.R

/**
 * Ширина кнопки «⋯» относительно строки полки.
 *
 * Кнопка узкая намеренно: главное действие — открыть полку — делает сама строка, а правка
 * спрятана в маленькую кнопку. Раньше было наоборот: широкая «Открыть» под строкой занимала
 * пол-экрана, а касание строки вело в переименование.
 */
private const val ACTIONS_BUTTON_WEIGHT = 0.22f

/** Сколько обложек книг показывать в строке полки: больше в строку не читается. */
private const val PREVIEW_COVERS = 3

/** Ширина мини-обложки в строке полки: высота получается из пропорции обложки. */
private val PREVIEW_COVER_WIDTH = 34.dp

/**
 * Полки: ручные и умные (docs/eink-ux.md §6.3).
 *
 * Строка полки открывает её содержимое; «⋯» — переименование, удаление и правка правил. Ниже —
 * «Доступные автополки» (готовые правила в одно касание) и сохранение текущего фильтра полки
 * как умной полки: набор условий у фильтра и у умной полки один и тот же.
 */
@Composable
fun CollectionsScreen(
    onBack: () -> Unit,
    onOpenCollection: (String) -> Unit,
    onEditRules: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CollectionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = LocalEinkColors.current
    val dimens = LocalEinkDimens.current
    val listState = rememberLazyListState()
    val scrollByPage = rememberEinkListPageScroller(listState)

    EinkScaffold(
        title = stringResource(R.string.collections_title),
        subtitle = stringResource(R.string.collections_subtitle),
        modifier = modifier,
        onBack = onBack,
        actions = {
            EinkButton(
                text = stringResource(R.string.collections_action_create),
                onClick = viewModel::onCreate,
            )
        },
        onKeyAction = { action ->
            when (action) {
                EinkAction.PREVIOUS_PAGE -> scrollByPage(-1)
                EinkAction.NEXT_PAGE -> scrollByPage(1)
                EinkAction.OPEN -> state.selectedId?.let(onOpenCollection)
                EinkAction.MENU -> viewModel.onCreate()
                EinkAction.BACK -> onBack()
                else -> Unit
            }
        },
        footer = {
            Text(
                text = shelfCountLabel(state),
                style = MaterialTheme.typography.labelMedium,
                color = colors.foreground,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.gutter, vertical = dimens.gutterSmall),
            )
        },
    ) {
        val error = state.error
        if (error != null) {
            EinkErrorBanner(
                message = error,
                actionLabel = stringResource(R.string.collections_action_retry),
                onAction = viewModel::onRefresh,
            )
        }

        if (state.collections.isEmpty() && state.presets.isEmpty()) {
            EinkEmptyState(
                title = stringResource(R.string.collections_empty_title),
                message = stringResource(R.string.collections_empty_message),
                actionLabel = stringResource(R.string.collections_action_create_collection),
                onAction = viewModel::onCreate,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(items = state.collections, key = { collection -> collection.id }) { collection ->
                    if (collection.id == state.renamingId) {
                        RenameRow(state = state, viewModel = viewModel)
                    } else {
                        ShelfRow(
                            collection = collection,
                            selected = collection.id == state.selectedId,
                            onOpen = { onOpenCollection(collection.id) },
                            onActions = { viewModel.onSelect(collection.id) },
                        )
                        if (collection.id == state.selectedId) {
                            ShelfActions(
                                collection = collection,
                                viewModel = viewModel,
                                onEditRules = { onEditRules(collection.id) },
                                onClose = { viewModel.onSelect("") },
                            )
                        }
                    }
                }

                item(key = "save-filter") {
                    EinkButton(
                        text = stringResource(R.string.collections_save_filter),
                        onClick = viewModel::onSaveCurrentFilter,
                        enabled = state.canSaveCurrentFilter,
                        modifier = Modifier.padding(
                            horizontal = dimens.gutter,
                            vertical = dimens.gutterSmall,
                        ),
                    )
                }

                if (state.presets.isNotEmpty()) {
                    item(key = "presets-header") {
                        EinkSectionHeader(
                            title = stringResource(R.string.collections_auto_header),
                            count = state.presets.size,
                        )
                    }
                    items(items = state.presets, key = { preset -> "preset:${preset.id}" }) { preset ->
                        EinkListRow(
                            title = preset.title,
                            subtitle = preset.hint,
                            trailing = stringResource(R.string.collections_action_add),
                            onClick = { viewModel.onAddPreset(preset.id) },
                        )
                    }
                }
            }
        }
    }
}

/** Строка полки: открывается касанием, правка — маленькой кнопкой рядом. */
@Composable
private fun ShelfRow(
    collection: CollectionUi,
    selected: Boolean,
    onOpen: () -> Unit,
    onActions: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (collection.previewCovers.isNotEmpty()) {
            ShelfPreview(covers = collection.previewCovers)
        }
        EinkListRow(
            title = collection.name,
            subtitle = collectionSubtitle(collection),
            trailing = collection.bookCount.toString(),
            selected = selected,
            onClick = onOpen,
            modifier = Modifier.weight(1f - ACTIONS_BUTTON_WEIGHT),
        )
        EinkButton(
            text = "⋯",
            onClick = onActions,
            modifier = Modifier
                .weight(ACTIONS_BUTTON_WEIGHT)
                .padding(end = LocalEinkDimens.current.gutterSmall),
        )
    }
}

/**
 * Обложки первых книг полки.
 *
 * Появились потому, что строка полки была неотличима от строки настроек: название, «ручная
 * полка» и число. Обложка отвечает на вопрос «что внутри» раньше, чем число.
 */
@Composable
private fun ShelfPreview(covers: List<String>) {
    val dimens = LocalEinkDimens.current

    Row(
        // Отступ слева — тот же, что у текста строки: без него мини-обложка прилипала к краю
        // экрана и висела вне самой строки (проверено на устройстве 28.09.2026).
        modifier = Modifier.padding(start = dimens.gutter, end = dimens.gutterSmall),
        horizontalArrangement = Arrangement.spacedBy(dimens.gutterSmall / 2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        covers.take(PREVIEW_COVERS).forEach { cover ->
            BookCover(
                coverPath = cover,
                title = "",
                contentDescription = null,
                modifier = Modifier.width(PREVIEW_COVER_WIDTH),
            )
        }
    }
}

/**
 * Действия над выбранной полкой.
 *
 * Подтверждения удаления нет намеренно: полка удаляется из базы, книги остаются в библиотеке.
 */
@Composable
private fun ShelfActions(
    collection: CollectionUi,
    viewModel: CollectionsViewModel,
    onEditRules: () -> Unit,
    onClose: () -> Unit,
) {
    val dimens = LocalEinkDimens.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.gutter, vertical = dimens.gutterSmall / 2),
        horizontalArrangement = Arrangement.spacedBy(dimens.gutterSmall),
    ) {
        EinkButton(
            text = stringResource(R.string.collections_action_rename),
            onClick = { viewModel.onStartRename(collection) },
        )
        // Правила есть только у умной полки: у ручной состав задают добавленные книги, а не условия.
        if (collection.isSmart) {
            EinkButton(text = stringResource(R.string.collections_action_rules), onClick = onEditRules)
        }
        EinkButton(
            text = stringResource(R.string.collections_action_delete),
            onClick = { viewModel.onDelete(collection.id) },
        )
        EinkButton(text = stringResource(R.string.collections_action_close), onClick = onClose)
    }
}

/** Поле переименования на месте полки: «Сохранить» пишет, «Отмена» возвращает как было. */
@Composable
private fun RenameRow(
    state: CollectionsUiState,
    viewModel: CollectionsViewModel,
) {
    val dimens = LocalEinkDimens.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.gutter, vertical = dimens.gutterSmall),
        verticalArrangement = Arrangement.spacedBy(dimens.gutterSmall),
    ) {
        EinkTextField(
            value = state.renameDraft,
            onValueChange = viewModel::onRenameDraftChanged,
            label = stringResource(R.string.collections_rename_title),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(dimens.gutterSmall)) {
            EinkButton(
                text = stringResource(R.string.collections_action_save),
                onClick = viewModel::onCommitRename,
                primary = true,
            )
            EinkButton(text = stringResource(R.string.collections_action_cancel), onClick = viewModel::onCancelRename)
        }
    }
}


/** Подпись под названием полки: ручная она или умная и по каким правилам. */
@Composable
private fun collectionSubtitle(collection: CollectionUi): String = when {
    collection.isSmart && collection.rulesSummary != null ->
        stringResource(R.string.collections_kind_smart, collection.rulesSummary)

    collection.isSmart -> stringResource(R.string.collections_kind_smart_empty)
    else -> stringResource(R.string.collections_kind_manual)
}

/** Подвал: сколько всего полок и сколько из них умных. */
@Composable
private fun shelfCountLabel(state: CollectionsUiState): String {
    val total = state.collections.size
    val smart = state.collections.count { collection -> collection.isSmart }
    return stringResource(R.string.collections_count, total, smart)
}
