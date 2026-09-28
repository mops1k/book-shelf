package io.github.bookshelf.feature.collections.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bookshelf.core.designsystem.component.EinkEmptyState
import io.github.bookshelf.core.designsystem.component.EinkErrorBanner
import io.github.bookshelf.core.designsystem.component.EinkListRow
import io.github.bookshelf.core.designsystem.component.EinkScaffold
import io.github.bookshelf.core.designsystem.theme.LocalEinkColors
import io.github.bookshelf.core.designsystem.theme.LocalEinkDimens
import io.github.bookshelf.feature.collections.CollectionContentViewModel

/**
 * Содержимое полки: какие книги на ней лежат.
 *
 * Экран отдельный, а не раздел списка полок, потому что у полки есть свой навигационный
 * шаг: из неё открывается карточка книги, и «Назад» должен вернуть на перечень полок.
 *
 * Для ручной полки список — это то, что положил пользователь; для умной — то, что подошло
 * под правила прямо сейчас, и она меняется сама при следующем скане.
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

    EinkScaffold(
        title = state.name.ifEmpty { "Полка" },
        subtitle = state.subtitle,
        modifier = modifier,
        onBack = onBack,
        footer = {
            Text(
                text = bookCountLabel(state.books.size),
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
                actionLabel = "Повторить",
                onAction = viewModel::onRefresh,
            )
        }

        if (state.books.isEmpty() && !state.isLoading) {
            EinkEmptyState(
                title = "Полка пуста",
                message = if (state.isSmart) {
                    "Под правила этой полки пока не подошла ни одна книга. " +
                        "Измените правила или добавьте книги в библиотеку."
                } else {
                    "Добавьте книги через «В полку» в карточке книги."
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(items = state.books, key = { book -> book.id }) { book ->
                    EinkListRow(
                        title = book.title,
                        subtitle = book.authors.ifBlank { book.extension },
                        onClick = { onOpenBook(book.id) },
                    )
                }
            }
        }
    }
}

/** Подпись числа книг в подвале. */
private fun bookCountLabel(count: Int): String = when (count % DECIMAL_BASE) {
    1 -> "$count книга"
    in FEW_FORM_MIN..FEW_FORM_MAX -> "$count книги"
    else -> "$count книг"
}

/** База десятичной системы: остаток от деления даёт последнюю цифру числа. */
private const val DECIMAL_BASE = 10

/** Форма «книги» — для чисел, оканчивающихся на 2–4. */
private const val FEW_FORM_MIN = 2
private const val FEW_FORM_MAX = 4
