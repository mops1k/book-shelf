package io.github.bookshelf.feature.collections.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bookshelf.core.designsystem.component.EinkListRow
import io.github.bookshelf.core.designsystem.component.EinkScaffold
import io.github.bookshelf.core.designsystem.theme.LocalEinkDimens
import io.github.bookshelf.feature.collections.R
import io.github.bookshelf.feature.collections.VolumeShelfViewModel

/**
 * Полка съёмного носителя: книги одного тома.
 *
 * Экран отдельный и с кнопкой «назад» (правило Александра 02.10.2026): фильтр основной библиотеки
 * для этого не годился — из полки было не вернуться к остальным книгам. Состав — только книги своего
 * источника; поиска и сортировки здесь нет намеренно, это список «что лежит на носителе».
 */
@Composable
fun VolumeShelfScreen(
    onBack: () -> Unit,
    onOpenBook: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VolumeShelfViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dimens = LocalEinkDimens.current

    EinkScaffold(
        title = state.title.ifBlank { stringResource(R.string.collections_volume_title) },
        modifier = modifier,
        onBack = onBack,
    ) {
        if (state.books.isEmpty()) {
            Text(
                text = stringResource(R.string.collections_volume_empty),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(dimens.gutter),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(items = state.books, key = { book -> book.id }) { book ->
                    EinkListRow(
                        title = book.title,
                        subtitle = book.authors,
                        onClick = { onOpenBook(book.id) },
                    )
                }
            }
        }
    }
}
