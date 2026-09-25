package io.github.bookshelf.feature.collections.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bookshelf.core.designsystem.component.EinkButton
import io.github.bookshelf.core.designsystem.component.EinkEmptyState
import io.github.bookshelf.core.designsystem.component.EinkErrorBanner
import io.github.bookshelf.core.designsystem.component.EinkListRow
import io.github.bookshelf.core.designsystem.component.EinkScaffold
import io.github.bookshelf.core.designsystem.component.TapZoneLayout
import io.github.bookshelf.core.designsystem.input.EinkAction
import io.github.bookshelf.core.designsystem.paging.rememberEinkPagerState
import io.github.bookshelf.core.designsystem.theme.LocalEinkColors
import io.github.bookshelf.core.designsystem.theme.LocalEinkDimens
import io.github.bookshelf.feature.collections.CollectionUi
import io.github.bookshelf.feature.collections.CollectionsViewModel

/** Сколько полок помещается на «страницу» списка. */
private const val COLLECTIONS_PAGE_SIZE = 12

/**
 * Коллекции и умные полки (docs/eink-ux.md §6.3).
 *
 * Список листается страницами: аппаратные Page Up/Down и боковые тап-зоны меняют страницу,
 * центральная кнопка/D-pad открывают выделенную полку. Данные появятся в Sprint 7.
 */
@Composable
fun CollectionsScreen(
    onBack: () -> Unit,
    onOpenCollection: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CollectionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dimens = LocalEinkDimens.current
    val pager = rememberEinkPagerState(
        pageSize = COLLECTIONS_PAGE_SIZE,
        totalItems = state.collections.size,
    )

    LaunchedEffect(state.collections.size) { pager.updateTotalItems(state.collections.size) }

    EinkScaffold(
        title = "Коллекции",
        subtitle = "Ручные полки и умные подборки",
        modifier = modifier,
        onBack = onBack,
        actions = { EinkButton(text = "Создать", onClick = viewModel::onCreate) },
        onKeyAction = { action ->
            when (action) {
                EinkAction.PREVIOUS_PAGE -> pager.previous()
                EinkAction.NEXT_PAGE -> pager.next()
                EinkAction.OPEN -> state.selectedId?.let(onOpenCollection)
                EinkAction.MENU -> viewModel.onCreate()
                EinkAction.BACK -> onBack()
                else -> Unit
            }
        },
        footer = {
            Text(
                text = pager.statusLabel("полок"),
                style = MaterialTheme.typography.labelMedium,
                color = LocalEinkColors.current.foreground,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.gutter, vertical = dimens.gutterSmall),
            )
        },
    ) {
        val error = state.error
        if (error != null) {
            EinkErrorBanner(message = error, actionLabel = "Повторить", onAction = viewModel::onRefresh)
        }

        TapZoneLayout(
            onPrevious = pager::previous,
            onNext = pager::next,
            modifier = Modifier.weight(1f),
        ) {
            if (state.collections.isEmpty()) {
                EinkEmptyState(
                    title = "Раздел в разработке — Спринт 7",
                    message = "Здесь появятся ручные полки и умные подборки на тех же правилах, " +
                        "что и фильтры: статус, автор, серия, тег, год, формат.",
                    actionLabel = "Создать полку",
                    onAction = viewModel::onCreate,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                val visible = state.collections.drop(pager.offset).take(pager.pageSize)
                LazyColumn(modifier = Modifier.fillMaxSize(), userScrollEnabled = false) {
                    items(items = visible, key = { collection -> collection.id }) { collection ->
                        EinkListRow(
                            title = collection.name,
                            subtitle = collectionSubtitle(collection),
                            trailing = collection.bookCount.toString(),
                            selected = collection.id == state.selectedId,
                            onClick = { viewModel.onSelect(collection.id) },
                        )
                    }
                }
            }
        }
    }
}

/** Подпись под названием полки: ручная она или умная и по каким правилам. */
private fun collectionSubtitle(collection: CollectionUi): String = when {
    collection.isSmart && collection.rulesSummary != null ->
        "Умная полка · ${collection.rulesSummary}"

    collection.isSmart -> "Умная полка"
    else -> "Ручная полка"
}
