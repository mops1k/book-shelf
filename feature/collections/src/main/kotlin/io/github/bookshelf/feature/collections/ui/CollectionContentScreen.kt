package io.github.bookshelf.feature.collections.ui

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bookshelf.core.designsystem.component.BookCover
import io.github.bookshelf.core.designsystem.component.EinkEmptyState
import io.github.bookshelf.core.designsystem.component.EinkErrorBanner
import io.github.bookshelf.core.designsystem.component.EinkScaffold
import io.github.bookshelf.core.designsystem.component.clickableNoIndication
import io.github.bookshelf.core.designsystem.input.EinkAction
import io.github.bookshelf.core.designsystem.paging.rememberEinkGridPageScroller
import io.github.bookshelf.core.designsystem.text.readingStatusBadge
import io.github.bookshelf.core.designsystem.text.resolve
import io.github.bookshelf.core.designsystem.theme.LocalEinkColors
import io.github.bookshelf.core.designsystem.theme.LocalEinkDimens
import io.github.bookshelf.feature.collections.CollectionContentUiState
import io.github.bookshelf.feature.collections.CollectionContentViewModel
import io.github.bookshelf.feature.collections.CollectionItemUi
import io.github.bookshelf.feature.collections.R

/**
 * Содержимое полки: какие книги на ней лежат.
 *
 * Экран отдельный, а не раздел списка полок, потому что у полки есть свой навигационный
 * шаг: из неё открывается карточка книги, и «Назад» должен вернуть на перечень полок.
 *
 * Для ручной полки список — это то, что положил пользователь; для умной — то, что подошло
 * под правила прямо сейчас, и она меняется сама при следующем скане.
 *
 * Книги показываются плитками с обложками — тем же способом, что и на главной полке: список
 * строками читался как ещё один экран метаданных, а полка без обложек не узнаётся.
 */
@Composable
fun CollectionContentScreen(
    collectionId: String,
    onBack: () -> Unit,
    onOpenBook: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CollectionContentViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = LocalEinkColors.current
    val dimens = LocalEinkDimens.current
    val gridState = rememberLazyGridState()
    val scrollByPage = rememberEinkGridPageScroller(gridState)

    EinkScaffold(
        title = state.name.ifEmpty { stringResource(R.string.collections_content_title) },
        subtitle = state.subtitle?.resolve(),
        modifier = modifier,
        onBack = onBack,
        onKeyAction = { action ->
            when (action) {
                EinkAction.PREVIOUS_PAGE -> scrollByPage(-1)
                EinkAction.NEXT_PAGE -> scrollByPage(1)
                EinkAction.BACK -> onBack()
                else -> Unit
            }
        },
        footer = { CollectionCountFooter(count = state.books.size) },
    ) {
        CollectionErrorBanner(state = state, onRetry = viewModel::onRefresh)

        if (state.books.isEmpty() && !state.isLoading) {
            EinkEmptyState(
                title = stringResource(R.string.collections_content_empty_title),
                message = if (state.isSmart) {
                    stringResource(R.string.collections_content_empty_message)
                } else {
                    stringResource(R.string.collections_content_empty_manual)
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = dimens.coverMinWidth),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
            ) {
                gridItems(items = state.books, key = { book -> book.id }) { book ->
                    ShelfBookItem(book = book, onClick = { onOpenBook(book.id) })
                }
            }
        }
    }
}

/** Плитка книги: обложка с бейджами формата и статуса, название, автор — как на главной полке. */
@Composable
private fun ShelfBookItem(
    book: CollectionItemUi,
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
            contentDescription = stringResource(
                R.string.collections_cover_description,
                book.title,
                book.authors,
            ),
            // Формат — в углу обложки, статус — полосой внизу: отдельная строка под плиткой
            // отнимала у полки целый ряд книг (docs/eink-ux.md §6.2).
            formatBadge = book.extension.uppercase().takeIf { extension -> extension.isNotEmpty() },
            statusBadge = readingStatusBadge(book.readingStatus.name, book.progressPercent)
                ?.resolve()
                .orEmpty(),
        )
        Text(
            text = book.title,
            // Подписи под плиткой на шаг меньше текста интерфейса — как на главной полке.
            style = MaterialTheme.typography.bodySmall,
            color = colors.foreground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            // Без автора показывается формат: пустая строка выглядела бы как потерянные данные.
            text = book.authors.ifBlank { book.extension.uppercase() },
            style = MaterialTheme.typography.labelSmall,
            color = colors.foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}


/** Подпись числа книг в подвале: формы слова выбирает система по `<plurals>`. */
@Composable
private fun bookCountLabel(count: Int): String =
    pluralStringResource(R.plurals.collections_book_count, count, count)


/** Подвал содержимого полки: сколько книг. */
@Composable
private fun CollectionCountFooter(count: Int) {
    val colors = LocalEinkColors.current
    val dimens = LocalEinkDimens.current
    Text(
        text = bookCountLabel(count),
        style = MaterialTheme.typography.labelMedium,
        color = colors.foreground,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.gutter, vertical = dimens.gutterSmall),
    )
}

/** Баннер ошибки содержимого полки: перевод текста и кнопка «Повторить». */
@Composable
private fun CollectionErrorBanner(state: CollectionContentUiState, onRetry: () -> Unit) {
    val error = state.error ?: return
    EinkErrorBanner(
        message = error.resolve(),
        actionLabel = stringResource(R.string.collections_action_retry),
        onAction = onRetry,
    )
}
