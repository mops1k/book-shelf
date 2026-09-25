package io.github.bookshelf.feature.search.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bookshelf.core.designsystem.component.EinkButton
import io.github.bookshelf.core.designsystem.component.EinkEmptyState
import io.github.bookshelf.core.designsystem.component.EinkErrorBanner
import io.github.bookshelf.core.designsystem.component.EinkListRow
import io.github.bookshelf.core.designsystem.component.EinkScaffold
import io.github.bookshelf.core.designsystem.component.EinkSectionHeader
import io.github.bookshelf.core.designsystem.component.EinkTextField
import io.github.bookshelf.core.designsystem.component.TapZoneLayout
import io.github.bookshelf.core.designsystem.input.EinkAction
import io.github.bookshelf.core.designsystem.paging.rememberEinkPagerState
import io.github.bookshelf.core.designsystem.theme.LocalEinkColors
import io.github.bookshelf.core.designsystem.theme.LocalEinkDimens
import io.github.bookshelf.feature.search.SearchViewModel

/** Сколько результатов показывается на «страницу». */
private const val SEARCH_PAGE_SIZE = 20

/**
 * Полноэкранный поиск по библиотеке (docs/eink-ux.md §6.5).
 *
 * Ввод и очистка работают сразу (это UI-состояние), сам FTS-запрос появится в Sprint 4.
 * Аппаратные Page Up/Down листают результаты, MENU очищает историю запросов.
 */
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenBook: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = LocalEinkColors.current
    val dimens = LocalEinkDimens.current
    val pager = rememberEinkPagerState(pageSize = SEARCH_PAGE_SIZE, totalItems = state.total)

    LaunchedEffect(state.total) { pager.updateTotalItems(state.total) }

    EinkScaffold(
        title = "Поиск",
        subtitle = "Название, автор, серия, имя файла, аннотация",
        modifier = modifier,
        onBack = onBack,
        onKeyAction = { action ->
            when (action) {
                EinkAction.PREVIOUS_PAGE -> pager.previous()
                EinkAction.NEXT_PAGE -> pager.next()
                EinkAction.SEARCH -> viewModel.onSearch()
                EinkAction.MENU -> viewModel.onClearHistory()
                EinkAction.BACK -> onBack()
                else -> Unit
            }
        },
        footer = {
            Text(
                text = pager.statusLabel("результатов"),
                style = MaterialTheme.typography.labelMedium,
                color = colors.foreground,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.gutter, vertical = dimens.gutterSmall),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = dimens.gutter),
            verticalArrangement = Arrangement.spacedBy(dimens.gutterSmall),
        ) {
            // STUB(Sprint 4): автофокус в поле. EinkTextField не принимает FocusRequester,
            //  поэтому автофокус появится вместе с доработкой компонента в дизайн-системе.
            EinkTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                label = "Поиск по библиотеке",
                placeholder = "Название, автор, серия, файл",
                imeAction = ImeAction.Search,
                onImeAction = viewModel::onSearch,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dimens.gutterSmall),
            ) {
                EinkButton(
                    text = "Найти",
                    onClick = viewModel::onSearch,
                    primary = true,
                    modifier = Modifier.weight(1f),
                )
                EinkButton(
                    text = "Очистить",
                    onClick = viewModel::onClearQuery,
                    modifier = Modifier.weight(1f),
                )
            }

            val error = state.error
            if (error != null) {
                EinkErrorBanner(
                    message = error,
                    actionLabel = "Повторить",
                    onAction = viewModel::onSearch,
                )
            }

            if (state.isSearching) {
                Text(
                    text = "Идёт поиск…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.foreground,
                )
            }

            if (state.suggestions.isNotEmpty()) {
                EinkSectionHeader(title = "Подсказки")
                Column {
                    state.suggestions.forEach { suggestion ->
                        EinkListRow(
                            title = suggestion,
                            onClick = { viewModel.onSelectSuggestion(suggestion) },
                        )
                    }
                }
            }

            EinkSectionHeader(title = "Результаты", count = state.total)

            TapZoneLayout(
                onPrevious = pager::previous,
                onNext = pager::next,
                modifier = Modifier.weight(1f),
            ) {
                if (state.results.isEmpty()) {
                    EinkEmptyState(
                        title = "Раздел в разработке — Спринт 4",
                        message = "Здесь появятся результаты полнотекстового поиска: совпадение " +
                            "выделяется рамкой, ниже — фильтры «только непрочитанные», " +
                            "«только избранное», формат.",
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize(), userScrollEnabled = false) {
                        items(items = state.results, key = { result -> result.id }) { result ->
                            EinkListRow(
                                title = result.title,
                                subtitle = result.snippet ?: result.authors,
                                trailing = result.matchedField,
                                onClick = { onOpenBook(result.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}
