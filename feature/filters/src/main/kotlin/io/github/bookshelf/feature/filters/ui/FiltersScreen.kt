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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
import io.github.bookshelf.feature.filters.FiltersViewModel
import io.github.bookshelf.feature.filters.activeFilterCount
import io.github.bookshelf.feature.filters.hasActiveFilters
import io.github.bookshelf.feature.filters.toSectionsUi

/**
 * Фасетные фильтры (docs/architecture.md §3.5).
 *
 * Множественный выбор: выбранный чип инвертируется, число рядом показывает, сколько книг
 * останется. Выбор и сброс применяются сразу, на каждый тап: отдельного «черновика» у экрана нет.
 *
 * Сам список фасетов живёт в [FacetList] — его же использует редактор правил умной полки.
 */
@Composable
fun FiltersScreen(
    onBack: () -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FiltersViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = LocalEinkColors.current
    val dimens = LocalEinkDimens.current
    val sections = remember(state.facets) { state.facets?.toSectionsUi().orEmpty() }
    // Прокрутка обычная: разделы больше не нарезаются «страницами» — страницы переключались
    // иначе как аппаратными клавишами, а всё, что не влезло на экран, было недостижимо.
    val listState = rememberLazyListState()
    val scrollByPage = rememberEinkListPageScroller(listState)

    EinkScaffold(
        title = "Фильтры",
        // Было «ИЛИ внутри фасета, И между фасетами» — слова «фасет» человек не знает, а «ИЛИ/И»
        // читались как часть названия (замечание Александра, 29.09.2026).
        subtitle = "Можно выбрать несколько значений",
        modifier = modifier,
        onBack = onBack,
        onKeyAction = { action ->
            when (action) {
                EinkAction.PREVIOUS_PAGE -> scrollByPage(-1)
                EinkAction.NEXT_PAGE -> scrollByPage(1)
                EinkAction.OPEN -> onApply()
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
                        text = "Активно фильтров: ${activeFilterCount(state.selected)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.foreground,
                    )
                    Text(
                        text = "Найдено: ${state.resultCount}",
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
                        text = "Сбросить",
                        onClick = viewModel::onReset,
                        enabled = hasActiveFilters(state.selected),
                        modifier = Modifier.weight(1f),
                    )
                    EinkButton(
                        text = "Применить",
                        onClick = onApply,
                        primary = true,
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
                actionLabel = "Обновить",
                onAction = viewModel::onLoadFacets,
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            if (sections.isEmpty()) {
                EinkEmptyState(
                    title = "Фильтры появятся, когда в библиотеке будут книги",
                    message = "Здесь появятся авторы, серии, жанры и теги, форматы и статус чтения — " +
                        "с числом книг и выбором нескольких значений сразу.",
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
