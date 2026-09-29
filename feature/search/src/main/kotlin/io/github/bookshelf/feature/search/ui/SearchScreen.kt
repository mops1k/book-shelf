package io.github.bookshelf.feature.search.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import io.github.bookshelf.core.designsystem.input.EinkAction
import io.github.bookshelf.core.designsystem.paging.rememberEinkListPageScroller
import io.github.bookshelf.core.designsystem.theme.LocalEinkColors
import io.github.bookshelf.core.designsystem.theme.LocalEinkDimens
import io.github.bookshelf.feature.search.SearchUiState
import io.github.bookshelf.feature.search.SearchViewModel


/**
 * Полноэкранный поиск по библиотеке (docs/eink-ux.md §6.5).
 *
 * Поиск идёт по `books_fts`: название, автор, серия, имя файла и аннотация. Аппаратные
 * Page Up/Down листают результаты (высота страницы берётся из самого списка), MENU очищает
 * историю запросов — как и кнопка «Очистить историю» под списком последних запросов.
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
    // Состояние списка результатов — то же, к которому подключено листание аппаратными
    // клавишами. Раньше пагинатор листал несуществующее состояние, а список результатов был
    // закрыт для прокрутки пальцем: до второй страницы выдачи было не добраться вообще.
    val resultsState = rememberLazyListState()
    val scrollByPage = rememberEinkListPageScroller(resultsState)
    // Клавиатура закрывается, когда поиск запущен: иначе она занимает половину экрана, и у списка
    // результатов не остаётся высоты — найденные книги просто не видны («Найдено: 1» и пустота).
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val startSearch = {
        keyboard?.hide()
        focus.clearFocus()
        viewModel.onSearch()
    }
    val chooseSuggestion: (String) -> Unit = { suggestion ->
        keyboard?.hide()
        focus.clearFocus()
        viewModel.onSelectSuggestion(suggestion)
    }

    EinkScaffold(
        title = "Поиск",
        subtitle = "Название, автор, серия, имя файла, аннотация",
        modifier = modifier,
        onBack = onBack,
        onKeyAction = { action ->
            when (action) {
                EinkAction.PREVIOUS_PAGE -> scrollByPage(-1)
                EinkAction.NEXT_PAGE -> scrollByPage(1)
                EinkAction.SEARCH -> startSearch()
                EinkAction.MENU -> viewModel.onClearHistory()
                EinkAction.BACK -> onBack()
                else -> Unit
            }
        },
        footer = {
            Text(
                // Число берётся из того же состояния, что и список, а не из пагинатора:
                // пагинатор обновлялся отдельной подпиской и мог отставать от списка.
                text = resultCountLabel(state.total),
                style = MaterialTheme.typography.labelMedium,
                color = colors.foreground,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.gutter, vertical = dimens.gutterSmall),
            )
        },
    ) {
        SearchContent(
            state = state,
            viewModel = viewModel,
            onOpenBook = onOpenBook,
            resultsState = resultsState,
            onSearch = startSearch,
            onChooseSuggestion = chooseSuggestion,
        )
    }
}

/**
 * Содержимое экрана: поле ввода, подсказки, последние запросы и результаты.
 *
 * Отдельной функцией: тело экрана переросло предел длины метода, а разрезать его проще всего
 * по смыслу — на «шапку с управлением и списками» и сам каркас с аппаратными клавишами.
 */
@Composable
private fun ColumnScope.SearchContent(
    state: SearchUiState,
    viewModel: SearchViewModel,
    onOpenBook: (String) -> Unit,
    resultsState: LazyListState,
    onSearch: () -> Unit,
    onChooseSuggestion: (String) -> Unit,
) {
    val colors = LocalEinkColors.current
    val dimens = LocalEinkDimens.current

    // Содержимое строится прямо в колонке каркаса, без вложенной Column: вложенная колонка
    // с `fillMaxSize` не растягивалась, `weight(1f)` у списка результатов получал нулевую высоту,
    // и найденные книги не рисовались вовсе — при этом счётчик показывал «Найдено: 1».
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
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
            onImeAction = onSearch,
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
                onAction = onSearch,
            )
        }

        if (state.isSearching) {
            Text(
                text = "Идёт поиск…",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.foreground,
            )
        }

        // Всё, что ниже кнопок, — один прокручиваемый список. Раньше подсказки, история и
        // результаты лежали в некатящемся Column: десять подсказок вытесняли результаты за нижний
        // край экрана, а Page Down прокручивал только список результатов, то есть ничего.
        LazyColumn(
            state = resultsState,
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            if (state.isSearching) {
                item(key = "searching") {
                    Text(
                        text = "Идёт поиск…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.foreground,
                        modifier = Modifier.padding(vertical = dimens.gutterSmall),
                    )
                }
            }

            // Один заголовок на подсказки: раньше их было два подряд (второй со счётчиком),
            // и пустой список подсказок выглядел как отдельный раздел.
            if (state.suggestions.isNotEmpty()) {
                item(key = "suggestions-header") {
                    EinkSectionHeader(title = "Подсказки", count = state.suggestions.size)
                }
                items(items = state.suggestions, key = { suggestion -> "s-$suggestion" }) { suggestion ->
                    EinkListRow(
                        title = suggestion,
                        onClick = { onChooseSuggestion(suggestion) },
                    )
                }
            }

            // История — только пока поле пустое: во время ввода она бы оттесняла результаты.
            if (state.query.isBlank()) {
                recentQueriesSection(
                    recents = state.recentQueries,
                    onChoose = onChooseSuggestion,
                    onClear = viewModel::onClearHistory,
                )
            }

            item(key = "results-header") {
                EinkSectionHeader(title = "Результаты", count = state.total)
            }

            if (state.results.isEmpty()) {
                item(key = "empty") {
                    // Текст не обещает того, чего нет: подсветки совпадения и фильтров по
                    // результатам в приложении пока не существует, а раньше он их обещал.
                    EinkEmptyState(
                        title = if (state.query.isBlank()) "Введите запрос" else "Ничего не найдено",
                        message = if (state.query.isBlank()) {
                            "Поиск идёт по названию, автору, серии, имени файла и аннотации."
                        } else {
                            "По запросу «${state.query.trim()}» совпадений нет. " +
                                "Попробуйте другое слово или часть слова."
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = dimens.gutter),
                    )
                }
            } else {
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

/**
 * Последние запросы: строки и кнопка очистки.
 *
 * Отдельной функцией, а не блоком внутри экрана: тело экрана переросло предел длины метода,
 * а секция цельная — она ничего не знает о состоянии, кроме самого списка.
 */
private fun LazyListScope.recentQueriesSection(
    recents: List<String>,
    onChoose: (String) -> Unit,
    onClear: () -> Unit,
) {
    if (recents.isEmpty()) return
    item(key = "recent-header") {
        EinkSectionHeader(title = "Последние запросы", count = recents.size)
    }
    items(items = recents, key = { recent -> "r-$recent" }) { recent ->
        EinkListRow(title = recent, onClick = { onChoose(recent) })
    }
    item(key = "recent-clear") {
        EinkButton(
            text = "Очистить историю",
            onClick = onClear,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Подпись числа результатов: «Найдено: 7». */
private fun resultCountLabel(total: Int): String = "Найдено: $total"
