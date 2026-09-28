package io.github.bookshelf.feature.catalogs.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bookshelf.core.designsystem.component.BookCover
import io.github.bookshelf.core.designsystem.component.EinkButton
import io.github.bookshelf.core.designsystem.component.EinkEmptyState
import io.github.bookshelf.core.designsystem.component.EinkErrorBanner
import io.github.bookshelf.core.designsystem.component.EinkListRow
import io.github.bookshelf.core.designsystem.component.EinkScaffold
import io.github.bookshelf.core.designsystem.component.EinkSectionHeader
import io.github.bookshelf.core.designsystem.theme.LocalEinkColors
import io.github.bookshelf.core.designsystem.theme.LocalEinkDimens
import io.github.bookshelf.feature.catalogs.EntryDetailsViewModel

/**
 * Карточка записи каталога: описание книги и кнопка «Скачать».
 *
 * Именно то, чего не хватало: по нажатию на книгу в листинге показывается всё, что знает
 * о ней фид, и только потом можно качать. Раньше нажатие сразу ставило загрузку в очередь,
 * и посмотреть описание, форматы и размер было негде.
 */
@Composable
fun EntryDetailsScreen(
    onBack: () -> Unit,
    onDownloaded: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EntryDetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = LocalEinkColors.current
    val dimens = LocalEinkDimens.current

    EinkScaffold(
        title = state.title.ifEmpty { "Запись каталога" },
        subtitle = state.authors.takeIf { it.isNotBlank() },
        modifier = modifier,
        onBack = onBack,
        footer = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(dimens.gutter),
                horizontalArrangement = Arrangement.spacedBy(dimens.gutterSmall),
            ) {
                EinkButton(
                    text = "Скачать",
                    onClick = { viewModel.onDownload(onDownloaded) },
                    primary = true,
                    enabled = state.canDownload,
                    modifier = Modifier.weight(1f),
                )
                EinkButton(text = "Назад", onClick = onBack, modifier = Modifier.weight(1f))
            }
        },
    ) {
        val error = state.error
        if (error != null) {
            EinkErrorBanner(message = error, actionLabel = "Повторить", onAction = viewModel::onRefresh)
        }

        if (state.title.isEmpty()) {
            EinkEmptyState(
                title = "Запись не найдена",
                message = "Вернитесь к каталогу и откройте книгу заново — фид обновился.",
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(dimens.gutter),
                verticalArrangement = Arrangement.spacedBy(dimens.gutter),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(dimens.gutter)) {
                    BookCover(
                        coverPath = null,
                        coverUrl = state.coverUrl,
                        title = state.title,
                        modifier = Modifier.weight(1f),
                        contentDescription = "Обложка: ${state.title}",
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(dimens.gutterSmall)) {
                        if (state.authors.isNotBlank()) {
                            DetailLine("Авторы", state.authors)
                        }
                        DetailLine("Форматы", state.formatsLabel)
                        state.sizeLabel?.let { size -> DetailLine("Размер", size) }
                        state.language?.let { language -> DetailLine("Язык", language) }
                        state.updated?.let { updated -> DetailLine("Обновлено", updated) }
                    }
                }

                EinkSectionHeader(title = "Описание")
                Text(
                    text = state.summary ?: "В каталоге нет описания этой книги.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.foreground,
                )

                if (state.folders.size > 1) {
                    EinkSectionHeader(title = "Куда сохранить", count = state.folders.size)
                    state.folders.forEach { folder ->
                        EinkListRow(
                            title = folder.name,
                            trailing = if (folder.id == state.selectedFolderId) "выбрана" else "выбрать",
                            onClick = { viewModel.onSelectFolder(folder.id) },
                        )
                    }
                } else if (state.folders.isEmpty()) {
                    EinkListRow(
                        title = "Папка не выбрана",
                        subtitle = "Добавьте папку в приложении: настройки → «Где искать книги».",
                    )
                }

                if (!state.canDownload) {
                    EinkListRow(
                        title = "Скачивание недоступно",
                        subtitle = "У записи нет прямой ссылки на файл — каталог предлагает " +
                            "только покупку или образец.",
                    )
                }
            }
        }
    }
}

/** Строка «подпись — значение». */
@Composable
private fun DetailLine(label: String, value: String) {
    EinkListRow(title = label, trailing = value)
}
