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
import io.github.bookshelf.feature.catalogs.EntryDetailsUiState
import io.github.bookshelf.feature.catalogs.EntryDetailsViewModel

/** Подпись, когда каталог не сообщил описание книги. */
private const val NO_SUMMARY_TEXT =
    "В каталоге нет описания этой книги. Всё, что о ней известно, — выше: автор, серия, " +
        "год издания, язык и форматы файлов."

/**
 * Карточка записи каталога: обложка, метаданные, описание и кнопка «Скачать».
 *
 * Именно то, чего не хватало: по нажатию на книгу в листинге показывается всё, что знает о ней
 * фид, и только потом можно качать. Раньше нажатие сразу ставило загрузку в очередь, и посмотреть
 * описание, форматы и размер было негде.
 *
 * @param onOpenFeed вернуться в браузер каталога: карточка просит открыть фид автора или серии,
 *   а навигацию выполняет вызывающий код.
 */
@Composable
fun EntryDetailsScreen(
    onBack: () -> Unit,
    onOpenFeed: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EntryDetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
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
                // Пока загрузка идёт, кнопка занята её состоянием: «Загрузка началась: в очереди»,
                // «Загрузка идёт: 45%». Так видно, что нажатие сработало, и нельзя поставить
                // в очередь второй экземпляр той же книги.
                EinkButton(
                    text = state.download?.label ?: "Скачать",
                    onClick = viewModel::onDownload,
                    primary = state.download == null,
                    enabled = state.canStartDownload,
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
            EntryCardBody(state = state, viewModel = viewModel, onOpenFeed = onOpenFeed)
        }
    }
}

/**
 * Содержимое карточки: шапка с обложкой, переходы, описание и выбор папки.
 *
 * Отдельной функцией, а не телом `EinkScaffold`: карточка переросла предел длины метода, а
 * разрезать её по смыслу проще всего именно здесь — навигационные колбэки и экран.
 */
@Composable
private fun EntryCardBody(
    state: EntryDetailsUiState,
    viewModel: EntryDetailsViewModel,
    onOpenFeed: () -> Unit,
) {
    val colors = LocalEinkColors.current
    val dimens = LocalEinkDimens.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(dimens.gutter),
        verticalArrangement = Arrangement.spacedBy(dimens.gutter),
    ) {
        // Обложка и короткие факты рядом: на e-ink это одна «шапка» карточки, дальше
        // идут строки во всю ширину — по ним проще попасть пальцем.
        Row(horizontalArrangement = Arrangement.spacedBy(dimens.gutter)) {
            BookCover(
                coverPath = null,
                coverUrl = state.coverUrl,
                title = state.title,
                modifier = Modifier.weight(1f),
                contentDescription = "Обложка: ${state.title}",
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(dimens.gutterSmall),
            ) {
                if (state.formatsLabel.isNotBlank()) {
                    DetailLine("Форматы", state.formatsLabel)
                }
                state.sizeLabel?.let { size -> DetailLine("Размер", size) }
                state.issued?.let { year -> DetailLine("Год", year) }
                state.language?.let { language -> DetailLine("Язык", language) }
            }
        }

        // Автор и серия — переходы в фиды каталога. Строка есть всегда, когда каталог сообщил
        // автора или серию; подсказка появляется только там, где фид объявлен.
        if (state.authors.isNotBlank()) {
            FeedLinkRow(
                label = "Автор",
                value = state.authors,
                hint = "его книги и серии",
                onClick = if (state.authorUrl != null) {
                    {
                        viewModel.onOpenAuthor()
                        onOpenFeed()
                    }
                } else {
                    null
                },
            )
        }
        if (state.seriesName != null || state.seriesUrl != null) {
            FeedLinkRow(
                label = "Серия",
                value = state.seriesName ?: "серия каталога",
                hint = "книги этой серии",
                onClick = if (state.seriesUrl != null) {
                    {
                        viewModel.onOpenSeries()
                        onOpenFeed()
                    }
                } else {
                    null
                },
            )
        }

        state.genresLabel?.let { genres -> DetailLine("Жанры", genres) }
        state.updated?.let { updated -> DetailLine("Обновлено", updated) }

        EinkSectionHeader(title = "Описание")
        Text(
            text = state.summary ?: NO_SUMMARY_TEXT,
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

/** Строка «подпись — значение». */
@Composable
private fun DetailLine(label: String, value: String) {
    EinkListRow(title = label, trailing = value)
}

/**
 * Строка перехода в фид каталога: автор или серия.
 *
 * Отдельно от [DetailLine], потому что у неё есть действие: касание открывает фид автора или
 * серии. Там, где каталог фида не объявил, строка остаётся справочной — обещать переход, которого
 * не будет, хуже, чем показать одно значение.
 */
@Composable
private fun FeedLinkRow(
    label: String,
    value: String,
    hint: String,
    onClick: (() -> Unit)?,
) {
    EinkListRow(
        title = "$label: $value",
        subtitle = if (onClick != null) "Нажмите, чтобы открыть $hint" else null,
        trailing = if (onClick != null) "→" else null,
        onClick = onClick,
    )
}
