package io.github.bookshelf.feature.bookdetails.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bookshelf.core.data.model.ReadingStatus
import io.github.bookshelf.core.designsystem.component.BookCover
import io.github.bookshelf.core.designsystem.component.EinkButton
import io.github.bookshelf.core.designsystem.component.EinkErrorBanner
import io.github.bookshelf.core.designsystem.component.EinkListRow
import io.github.bookshelf.core.designsystem.component.EinkScaffold
import io.github.bookshelf.core.designsystem.component.EinkSectionHeader
import io.github.bookshelf.core.designsystem.input.EinkAction
import io.github.bookshelf.core.designsystem.theme.LocalEinkColors
import io.github.bookshelf.core.designsystem.theme.LocalEinkDimens
import io.github.bookshelf.feature.bookdetails.BookDetailsUiState
import io.github.bookshelf.feature.bookdetails.BookDetailsViewModel

/** Ширина обложки в карточке. */
private val COVER_WIDTH = 160.dp

/**
 * Карточка книги (docs/eink-ux.md §6.4).
 *
 * Скелет этапа каркаса: показаны обложка, все поля метаданных, аннотация, путь и действия.
 * Данные появятся в Sprint 4 (`BookRepository.observe(bookId)`), открытие в читалке — Sprint 5.
 *
 * Вертикальный скролл здесь разрешён осознанно: это единственный экран, где «иначе нельзя»
 * (docs/eink-ux.md §3.1), список книг всегда листается страницами.
 */
@Composable
fun BookDetailsScreen(
    bookId: String,
    onBack: () -> Unit,
    onRead: (String) -> Unit,
    onOpenCollections: (String) -> Unit,
    onEditMetadata: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BookDetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dimens = LocalEinkDimens.current

    EinkScaffold(
        title = state.title.ifEmpty { "Карточка книги" },
        subtitle = state.authors.ifEmpty { null },
        modifier = modifier,
        onBack = onBack,
        onKeyAction = { action ->
            when (action) {
                EinkAction.BACK -> onBack()
                EinkAction.OPEN -> onRead(bookId)
                EinkAction.MENU -> onEditMetadata(bookId)
                else -> Unit
            }
        },
        footer = {
            Row(modifier = Modifier.fillMaxWidth().padding(dimens.gutter)) {
                EinkButton(
                    text = "Читать",
                    onClick = { onRead(bookId) },
                    primary = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(dimens.gutter),
            verticalArrangement = Arrangement.spacedBy(dimens.gutter),
        ) {
            val error = state.error
            if (error != null) {
                EinkErrorBanner(
                    message = error,
                    actionLabel = "Повторить",
                    onAction = viewModel::onRefreshMetadata,
                )
            }

            BookHeader(state = state)

            val parseError = state.parseError
            if (parseError != null) {
                EinkErrorBanner(
                    message = "Не удалось разобрать: $parseError",
                    actionLabel = "Заполнить вручную",
                    onAction = { onEditMetadata(bookId) },
                )
            }

            MetadataRows(state = state)

            DescriptionBlock(description = state.description)

            FileBlock(state = state)

            ActionsBlock(
                state = state,
                onOpenCollections = { onOpenCollections(bookId) },
                onEditMetadata = { onEditMetadata(bookId) },
                viewModel = viewModel,
            )

            StubNotice(sprint = 4)
        }
    }
}

/** Обложка и главные подписи карточки. */
@Composable
private fun BookHeader(state: BookDetailsUiState) {
    val colors = LocalEinkColors.current
    val dimens = LocalEinkDimens.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(dimens.gutter),
    ) {
        BookCover(
            coverPath = state.coverPath,
            title = state.title,
            modifier = Modifier.width(COVER_WIDTH),
            contentDescription = "Обложка: ${state.title}, ${state.authors}",
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(dimens.gutterSmall),
        ) {
            Text(
                text = state.title,
                style = MaterialTheme.typography.titleLarge,
                color = colors.foreground,
            )
            Text(
                text = state.authors,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.foreground,
            )
            Text(
                text = readingStatusLabel(state.readingStatus),
                style = MaterialTheme.typography.labelMedium,
                color = colors.foreground,
            )
            if (state.isFavorite) {
                Text(
                    text = "В избранном",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.foreground,
                )
            }
        }
    }
}

/** Все метаданные, которые есть у книги: пустые поля просто не показываются. */
@Composable
private fun MetadataRows(state: BookDetailsUiState) {
    Column {
        state.series?.let { MetadataRow(label = "Серия", value = it) }
        state.publishedYear?.let { MetadataRow(label = "Год", value = it.toString()) }
        state.language?.let { MetadataRow(label = "Язык", value = it) }
        state.publisher?.let { MetadataRow(label = "Издательство", value = it) }
        state.isbn?.let { MetadataRow(label = "ISBN", value = it) }
        state.pageCount?.let { MetadataRow(label = "Страниц", value = it.toString()) }
        state.sizeLabel?.let { MetadataRow(label = "Размер", value = it) }
        state.addedAtLabel?.let { MetadataRow(label = "Добавлена", value = it) }
    }
}

/** Одна строка метаданных: подпись слева, значение справа. */
@Composable
private fun MetadataRow(label: String, value: String) {
    EinkListRow(title = label, trailing = value)
}

/** Аннотация. Сворачивание появится вместе с реальными данными (Sprint 4). */
@Composable
private fun DescriptionBlock(description: String?) {
    val colors = LocalEinkColors.current
    val dimens = LocalEinkDimens.current

    Column(verticalArrangement = Arrangement.spacedBy(dimens.gutterSmall)) {
        EinkSectionHeader(title = "Аннотация")
        Text(
            text = description ?: "Аннотация не найдена в файле — её можно заполнить вручную.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.foreground,
        )
    }
}

/** Путь к файлу — по нему пользователь понимает, откуда пришла книга. */
@Composable
private fun FileBlock(state: BookDetailsUiState) {
    Column {
        EinkSectionHeader(title = "Файл")
        EinkListRow(title = "Путь", subtitle = state.relativePath)
        EinkListRow(title = "Документ", subtitle = state.documentUri)
    }
}

/** Действия над книгой: избранное, статус, полки, метаданные. */
@Composable
private fun ActionsBlock(
    state: BookDetailsUiState,
    onOpenCollections: () -> Unit,
    onEditMetadata: () -> Unit,
    viewModel: BookDetailsViewModel,
) {
    val dimens = LocalEinkDimens.current

    Column(verticalArrangement = Arrangement.spacedBy(dimens.gutterSmall)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(dimens.gutterSmall),
        ) {
            EinkButton(
                text = if (state.isFavorite) "Убрать из избранного" else "В избранное",
                onClick = viewModel::onToggleFavorite,
                modifier = Modifier.weight(1f),
            )
            EinkButton(
                text = if (state.readingStatus == ReadingStatus.READ) "Снять «прочитано»" else "Прочитано",
                onClick = viewModel::onToggleRead,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(dimens.gutterSmall),
        ) {
            EinkButton(text = "В полку", onClick = onOpenCollections, modifier = Modifier.weight(1f))
            EinkButton(text = "Изменить", onClick = onEditMetadata, modifier = Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(dimens.gutterSmall),
        ) {
            EinkButton(
                text = "Обновить из файла",
                onClick = viewModel::onRefreshMetadata,
                modifier = Modifier.weight(1f),
            )
            EinkButton(
                text = "Показать в папке",
                onClick = viewModel::onShowInFolder,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Подпись статуса чтения. */
private fun readingStatusLabel(status: ReadingStatus): String = when (status) {
    ReadingStatus.UNREAD -> "не прочитано"
    ReadingStatus.READING -> "читаю"
    ReadingStatus.READ -> "прочитано"
}

/** Пометка этапа каркаса: видно, что экран ещё не реализован, и в каком спринте будет. */
@Composable
private fun StubNotice(sprint: Int) {
    val colors = LocalEinkColors.current
    Text(
        text = "Раздел в разработке — Спринт $sprint",
        style = MaterialTheme.typography.labelMedium,
        color = colors.foreground,
    )
}
