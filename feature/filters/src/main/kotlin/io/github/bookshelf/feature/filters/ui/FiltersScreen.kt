package io.github.bookshelf.feature.filters.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bookshelf.core.designsystem.component.EinkButton
import io.github.bookshelf.core.designsystem.component.EinkChip
import io.github.bookshelf.core.designsystem.component.EinkEmptyState
import io.github.bookshelf.core.designsystem.component.EinkErrorBanner
import io.github.bookshelf.core.designsystem.component.EinkScaffold
import io.github.bookshelf.core.designsystem.component.EinkSectionHeader
import io.github.bookshelf.core.designsystem.input.EinkAction
import io.github.bookshelf.core.designsystem.paging.rememberEinkPagerState
import io.github.bookshelf.core.designsystem.paging.statusLabel
import io.github.bookshelf.core.designsystem.theme.LocalEinkColors
import io.github.bookshelf.core.designsystem.theme.LocalEinkDimens
import io.github.bookshelf.feature.filters.FacetChipUi
import io.github.bookshelf.feature.filters.FacetKind
import io.github.bookshelf.feature.filters.FacetSectionUi
import io.github.bookshelf.feature.filters.FiltersViewModel
import io.github.bookshelf.feature.filters.FilterSelectionUi
import io.github.bookshelf.feature.filters.activeFilterCount
import io.github.bookshelf.feature.filters.hasActiveFilters
import io.github.bookshelf.feature.filters.toSectionsUi

/** Сколько секций фасетов показывается на «страницу». */
private const val FACET_PAGE_SIZE = 3

/** Сколько чипов помещается в одну строку. */
private const val CHIPS_PER_ROW = 2

/**
 * Фасетные фильтры (docs/architecture.md §3.5).
 *
 * Множественный выбор: выбранный чип инвертируется, число рядом показывает, сколько книг
 * останется. Выбор и сброс работают сразу, подсчёт чисел появится в Sprint 4.
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
    val pager = rememberEinkPagerState(pageSize = FACET_PAGE_SIZE, totalItems = sections.size)

    LaunchedEffect(sections.size) { pager.updateTotalItems(sections.size) }

    EinkScaffold(
        title = "Фильтры",
        subtitle = "ИЛИ внутри фасета, И между фасетами",
        modifier = modifier,
        onBack = onBack,
        onKeyAction = { action ->
            when (action) {
                EinkAction.PREVIOUS_PAGE -> pager.previous()
                EinkAction.NEXT_PAGE -> pager.next()
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
                        text = pager.statusLabel("разделов"),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.foreground,
                    )
                    Text(
                        text = "Активно: ${activeFilterCount(state.selected)} · " +
                            "Найдено: ${state.resultCount}",
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

        Box(
                modifier = Modifier.weight(1f),
            ) {
            if (sections.isEmpty()) {
                EinkEmptyState(
                    title = "Фильтры появятся, когда в библиотеке будут книги",
                    message = "Здесь появятся фасеты: авторы, серии, жанры и теги, форматы и статус — " +
                        "чипами с числом книг, множественным выбором и кнопками «Сбросить» и «Применить».",
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                FacetPages(
                    sections = sections.drop(pager.offset).take(pager.pageSize),
                    selected = state.selected,
                    onToggle = viewModel::onToggleFacet,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** Страница секций: в каждой строке — до [CHIPS_PER_ROW] чипов. */
@Composable
private fun FacetPages(
    sections: List<FacetSectionUi>,
    selected: FilterSelectionUi,
    onToggle: (FacetKind, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalEinkDimens.current

    Column(modifier = modifier.padding(horizontal = dimens.gutter)) {
        sections.forEach { section ->
            EinkSectionHeader(title = section.title, count = section.items.size)
            section.items.chunked(CHIPS_PER_ROW).forEach { rowItems ->
                Row(
                    modifier = Modifier.padding(vertical = dimens.gutterSmall / 2),
                    horizontalArrangement = Arrangement.spacedBy(dimens.gutterSmall),
                ) {
                    rowItems.forEach { chip ->
                        FacetChip(
                            chip = chip,
                            selected = isFacetSelected(selected, section.kind, chip.id),
                            onClick = { onToggle(section.kind, chip.id) },
                        )
                    }
                }
            }
        }
    }
}

/** Один чип фасета с числом книг. */
@Composable
private fun FacetChip(
    chip: FacetChipUi,
    selected: Boolean,
    onClick: () -> Unit,
) {
    EinkChip(
        label = chip.label,
        count = chip.count,
        selected = selected,
        onClick = onClick,
    )
}

/** Выбран ли чип в своём фасете. */
private fun isFacetSelected(
    selection: FilterSelectionUi,
    kind: FacetKind,
    facetId: String,
): Boolean = when (kind) {
    FacetKind.AUTHORS -> facetId in selection.authors
    FacetKind.SERIES -> facetId in selection.series
    FacetKind.TAGS -> facetId in selection.tags
    FacetKind.FORMATS -> facetId in selection.formats
    FacetKind.STATUSES -> facetId in selection.statuses
}
