package io.github.bookshelf.feature.filters.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bookshelf.core.designsystem.component.EinkButton
import io.github.bookshelf.core.designsystem.component.EinkEmptyState
import io.github.bookshelf.core.designsystem.component.EinkErrorBanner
import io.github.bookshelf.core.designsystem.component.EinkScaffold
import io.github.bookshelf.core.designsystem.input.EinkAction
import io.github.bookshelf.core.designsystem.paging.rememberEinkListPageScroller
import io.github.bookshelf.core.designsystem.theme.LocalEinkColors
import io.github.bookshelf.core.designsystem.theme.LocalEinkDimens
import io.github.bookshelf.feature.filters.ShelfRulesViewModel
import io.github.bookshelf.feature.filters.R
import io.github.bookshelf.feature.filters.activeFilterCount
import io.github.bookshelf.feature.filters.hasActiveFilters
import io.github.bookshelf.feature.filters.toSectionsUi

/**
 * Редактор правил умной полки.
 *
 * Условия те же, что у фасетных фильтров («ИЛИ внутри фасета, И между фасетами», ADR-008), и
 * рисуются тем же [FacetList]: правило по жанру обязано давать ровно тот же состав, что и фильтр
 * по этому жанру. Отличие от экрана фильтров одно: выбор здесь локальный и по умолчанию не
 * применяется, пока не нажато «Сохранить».
 */
@Composable
fun ShelfRulesScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ShelfRulesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = LocalEinkColors.current
    val dimens = LocalEinkDimens.current
    val sections = remember(state.facets) { state.facets?.toSectionsUi().orEmpty() }
    val listState = rememberLazyListState()
    val scrollByPage = rememberEinkListPageScroller(listState)

    // Успешное сохранение закрывает редактор: возвращаться по «Назад» с уже сохранёнными
    // правилами значило бы предлагать сохранить их второй раз.
    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }

    EinkScaffold(
        title = stringResource(R.string.shelf_rules_title),
        subtitle = state.shelfName.ifEmpty { stringResource(R.string.shelf_rules_subtitle) },
        modifier = modifier,
        onBack = onBack,
        onKeyAction = { action ->
            when (action) {
                EinkAction.PREVIOUS_PAGE -> scrollByPage(-1)
                EinkAction.NEXT_PAGE -> scrollByPage(1)
                EinkAction.OPEN -> viewModel.onSave()
                EinkAction.BACK -> onBack()
                else -> Unit
            }
        },
        footer = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = dimens.gutter, vertical = dimens.gutterSmall),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(
                            R.string.shelf_rules_conditions,
                            activeFilterCount(state.selected),
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.foreground,
                    )
                    Text(
                        text = stringResource(R.string.shelf_rules_matching, state.resultCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.foreground,
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = dimens.gutter, vertical = dimens.gutterSmall),
                    horizontalArrangement = Arrangement.spacedBy(dimens.gutterSmall),
                ) {
                    EinkButton(
                        text = stringResource(R.string.filters_action_reset),
                        onClick = viewModel::onReset,
                        enabled = hasActiveFilters(state.selected),
                        modifier = Modifier.weight(1f),
                    )
                    EinkButton(
                        text = stringResource(R.string.shelf_rules_action_save),
                        onClick = viewModel::onSave,
                        primary = true,
                        enabled = hasActiveFilters(state.selected),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
    ) {
        val error = state.error
        if (error != null) {
            EinkErrorBanner(
                message = error,
                actionLabel = stringResource(R.string.shelf_rules_action_retry),
                onAction = viewModel::onLoadFacets,
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            if (sections.isEmpty()) {
                EinkEmptyState(
                    title = stringResource(R.string.shelf_rules_empty_title),
                    message = stringResource(R.string.shelf_rules_empty_message),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                FacetList(
                    sections = sections,
                    selected = state.selected,
                    onToggle = viewModel::onToggleFacet,
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
