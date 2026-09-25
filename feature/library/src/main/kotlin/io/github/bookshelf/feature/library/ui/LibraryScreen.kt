package io.github.bookshelf.feature.library.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bookshelf.core.data.model.Grouping
import io.github.bookshelf.core.data.model.ReadingStatus
import io.github.bookshelf.core.data.model.SortOrder
import io.github.bookshelf.core.designsystem.component.BookCover
import io.github.bookshelf.core.designsystem.component.EinkButton
import io.github.bookshelf.core.designsystem.component.EinkChip
import io.github.bookshelf.core.designsystem.component.EinkEmptyState
import io.github.bookshelf.core.designsystem.component.EinkErrorBanner
import io.github.bookshelf.core.designsystem.component.EinkListRow
import io.github.bookshelf.core.designsystem.component.EinkScaffold
import io.github.bookshelf.core.designsystem.component.TapZoneLayout
import io.github.bookshelf.core.designsystem.component.clickableNoIndication
import io.github.bookshelf.core.designsystem.input.EinkAction
import io.github.bookshelf.core.designsystem.paging.rememberEinkPagerState
import io.github.bookshelf.core.designsystem.theme.LocalEinkColors
import io.github.bookshelf.core.designsystem.theme.LocalEinkDimens
import io.github.bookshelf.feature.library.BookCardUi
import io.github.bookshelf.feature.library.LibraryUiState
import io.github.bookshelf.feature.library.LibraryViewModel
import io.github.bookshelf.feature.library.model.ViewMode

/**
 * Полка — главный экран (docs/eink-ux.md §6.2).
 *
 * Скелет этапа каркаса:
 * * [TapZoneLayout] листает страницы боковыми зонами, центральная — по элементу;
 * * сетка/список показывают ровно страницу элементов (`pageSize`), скролл выключен:
 *   на e-ink скролл даёт «призраков» (docs/eink-ux.md §3.3);
 * * подпись «Стр. N/M · K книг» в нижней строке формирует `EinkPagerState`.
 *
 * Данных пока нет: заполнение идёт из `BookRepository` в Sprint 4.
 */
@Composable
fun LibraryScreen(
    onOpenBook: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenFilters: () -> Unit,
    onOpenCollections: () -> Unit,
    onOpenScan: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dimens = LocalEinkDimens.current
    val pager = rememberEinkPagerState(pageSize = state.pageSize, totalItems = state.total)

    // Номером страницы владеет ViewModel, листание — её чистая арифметика. Pager держим
    // в синхроне только ради подписи «Стр. N/M · K книг».
    LaunchedEffect(state.page, state.total, state.pageSize) {
        pager.updateTotalItems(state.total)
        pager.first()
        repeat(state.page) { if (pager.hasNext) pager.next() }
    }

    EinkScaffold(
        title = "Библиотека",
        modifier = modifier,
        actions = {
            EinkButton(text = "Поиск", onClick = onOpenSearch)
            EinkButton(text = "Фильтры (${state.activeFilters.activeCount})", onClick = onOpenFilters)
            EinkButton(text = "Полки", onClick = onOpenCollections)
        },
        onKeyAction = { action ->
            when (action) {
                EinkAction.PREVIOUS_PAGE -> viewModel.onPreviousPage()
                EinkAction.NEXT_PAGE -> viewModel.onNextPage()
                EinkAction.SEARCH -> onOpenSearch()
                EinkAction.MENU -> onOpenFilters()
                // Корень графа: аппаратный BACK выходит из приложения (или сворачивает его).
                EinkAction.BACK -> onExit()
                else -> Unit
            }
        },
        footer = { LibraryFooter(state = state, statusLabel = pager.statusLabel("книг")) },
    ) {
        val error = state.error
        if (error != null) {
            EinkErrorBanner(
                message = error,
                actionLabel = "Повторить",
                onAction = viewModel::onRefresh,
            )
        }

        ModeSwitcher(mode = state.mode, onModeChange = viewModel::onSetMode)

        TapZoneLayout(
            onPrevious = viewModel::onPreviousPage,
            onNext = viewModel::onNextPage,
            modifier = Modifier.weight(1f),
        ) {
            if (state.books.isEmpty()) {
                EinkEmptyState(
                    title = "Раздел в разработке — Спринт 4",
                    message = "Здесь появится полка: сетка и список, сортировка, группировка, " +
                        "фильтры и постраничное листание без скролла.",
                    actionLabel = "Сканировать",
                    onAction = onOpenScan,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                BookPage(
                    state = state,
                    columns = dimens.gridColumns,
                    onOpenBook = onOpenBook,
                )
            }
        }
    }
}

/** Переключатель «Сетка / Список / Компактно» — чистое UI-состояние, применяется сразу. */
@Composable
private fun ModeSwitcher(
    mode: ViewMode,
    onModeChange: (ViewMode) -> Unit,
) {
    val dimens = LocalEinkDimens.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.gutter, vertical = dimens.gutterSmall),
        horizontalArrangement = Arrangement.spacedBy(dimens.gutterSmall),
    ) {
        ViewMode.entries.forEach { candidate ->
            EinkChip(
                label = viewModeLabel(candidate),
                selected = candidate == mode,
                onClick = { onModeChange(candidate) },
            )
        }
    }
}

/** Одна страница элементов: сетка обложек или список — в зависимости от режима. */
@Composable
private fun BookPage(
    state: LibraryUiState,
    columns: Int,
    onOpenBook: (String) -> Unit,
) {
    when (state.mode) {
        ViewMode.GRID -> LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = false,
        ) {
            gridItems(items = state.books, key = { book -> book.id }) { book ->
                BookGridItem(book = book, onClick = { onOpenBook(book.id) })
            }
        }

        // STUB(Sprint 4): в режиме «Список» слева появится миниатюра обложки (BookCover).
        ViewMode.LIST -> LazyColumn(modifier = Modifier.fillMaxSize(), userScrollEnabled = false) {
            listItems(items = state.books, key = { book -> book.id }) { book ->
                BookListRow(book = book, withAuthors = true, onClick = { onOpenBook(book.id) })
            }
        }

        ViewMode.COMPACT -> LazyColumn(modifier = Modifier.fillMaxSize(), userScrollEnabled = false) {
            listItems(items = state.books, key = { book -> book.id }) { book ->
                BookListRow(book = book, withAuthors = false, onClick = { onOpenBook(book.id) })
            }
        }
    }
}

/** Элемент сетки: обложка, название, автор, статус чтения. */
@Composable
private fun BookGridItem(
    book: BookCardUi,
    onClick: () -> Unit,
) {
    val colors = LocalEinkColors.current
    val dimens = LocalEinkDimens.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickableNoIndication(onClick = onClick)
            .padding(dimens.gutterSmall),
        verticalArrangement = Arrangement.spacedBy(dimens.gutterSmall / 2),
    ) {
        BookCover(
            coverPath = book.coverPath,
            title = book.title,
            modifier = Modifier.fillMaxWidth(),
            contentDescription = "Обложка: ${book.title}, ${book.authors}",
        )
        Text(
            text = book.title,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.foreground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = book.authors,
            style = MaterialTheme.typography.bodySmall,
            color = colors.foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = readingStatusLabel(book.readingStatus),
            style = MaterialTheme.typography.labelMedium,
            color = colors.foreground,
        )
    }
}

/** Строка списка: высота не меньше `touchComfort`, поэтому попасть пальцем легко. */
@Composable
private fun BookListRow(
    book: BookCardUi,
    withAuthors: Boolean,
    onClick: () -> Unit,
) {
    EinkListRow(
        title = book.title,
        subtitle = if (withAuthors) book.authors else null,
        trailing = readingStatusLabel(book.readingStatus),
        onClick = onClick,
    )
}

/** Нижняя строка полки: подпись страницы и текущие сортировка с группировкой. */
@Composable
private fun LibraryFooter(
    state: LibraryUiState,
    statusLabel: String,
) {
    val colors = LocalEinkColors.current
    val dimens = LocalEinkDimens.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.gutter, vertical = dimens.gutterSmall),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = statusLabel,
            style = MaterialTheme.typography.labelMedium,
            color = colors.foreground,
        )
        Text(
            text = "Сортировка: ${sortLabel(state.sort)} · ${groupingLabel(state.grouping)}",
            style = MaterialTheme.typography.labelMedium,
            color = colors.foreground,
        )
    }
}

/** Подпись статуса чтения. */
private fun readingStatusLabel(status: ReadingStatus): String = when (status) {
    ReadingStatus.UNREAD -> "не прочитано"
    ReadingStatus.READING -> "читаю"
    ReadingStatus.READ -> "прочитано"
}

/** Подпись сортировки для нижней строки. */
private fun sortLabel(sort: SortOrder): String = when (sort) {
    SortOrder.TITLE -> "Название"
    SortOrder.AUTHOR -> "Автор"
    SortOrder.SERIES -> "Серия"
    SortOrder.ADDED_AT -> "Дата добавления"
    SortOrder.LAST_OPENED -> "Дата открытия"
    SortOrder.SIZE -> "Размер"
    SortOrder.PROGRESS -> "Прогресс"
    SortOrder.YEAR -> "Год"
}

/** Подпись группировки для нижней строки. */
private fun groupingLabel(grouping: Grouping): String = when (grouping) {
    Grouping.NONE -> "без группировки"
    Grouping.SERIES -> "по сериям"
    Grouping.AUTHOR -> "по авторам"
    Grouping.FOLDER -> "по папкам"
    Grouping.FORMAT -> "по форматам"
}

/** Подпись режима отображения. */
private fun viewModeLabel(mode: ViewMode): String = when (mode) {
    ViewMode.GRID -> "Сетка"
    ViewMode.LIST -> "Список"
    ViewMode.COMPACT -> "Компактно"
}
