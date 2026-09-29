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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bookshelf.core.data.model.ReadingStatus
import io.github.bookshelf.core.data.model.label
import io.github.bookshelf.core.designsystem.component.BookCover
import io.github.bookshelf.core.designsystem.component.EinkButton
import io.github.bookshelf.core.designsystem.component.EinkConfirmRow
import io.github.bookshelf.core.designsystem.component.EinkEmptyState
import io.github.bookshelf.core.designsystem.component.EinkErrorBanner
import io.github.bookshelf.core.designsystem.component.EinkListRow
import io.github.bookshelf.core.designsystem.component.EinkScaffold
import io.github.bookshelf.core.designsystem.component.EinkSectionHeader
import io.github.bookshelf.core.designsystem.input.EinkAction
import io.github.bookshelf.core.designsystem.theme.LocalEinkColors
import io.github.bookshelf.core.designsystem.theme.LocalEinkDimens
import io.github.bookshelf.feature.bookdetails.BookDetailsUiState
import io.github.bookshelf.feature.bookdetails.BookDetailsViewModel
import io.github.bookshelf.feature.bookdetails.filePathLabel

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
    /** Вернуться на полку: карточка кладёт просьбу «покажи автора/серию», а полка её забирает. */
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BookDetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dimens = LocalEinkDimens.current

    // После удаления карточка закрывается: книги и файла больше нет, показывать нечего.
    LaunchedEffect(state.isDeleted) {
        if (state.isDeleted) onBack()
    }

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

            if (state.isPickingCollection) {
                CollectionPicker(
                    state = state,
                    onPick = viewModel::onAddToCollection,
                    onClose = viewModel::onCloseCollectionPicker,
                )
                return@Column
            }

            val parseError = state.parseError
            if (parseError != null) {
                EinkErrorBanner(
                    message = "Не удалось разобрать: $parseError",
                    actionLabel = "Заполнить вручную",
                    onAction = { onEditMetadata(bookId) },
                )
            }

            MetadataRows(
                state = state,
                onOpenAuthor = {
                    viewModel.onOpenAuthor()
                    onOpenLibrary()
                },
                onOpenSeries = {
                    viewModel.onOpenSeries()
                    onOpenLibrary()
                },
            )

            DescriptionBlock(description = state.description)

            FileBlock(state = state)

            ActionsBlock(
                state = state,
                onOpenCollections = { viewModel.onOpenCollectionPicker() },
                onEditMetadata = { onEditMetadata(bookId) },
                viewModel = viewModel,
            )
        }
    }
}

/**
 * Выбор полки для книги.
 *
 * Список показывается на месте карточки, а не в отдельном экране: полки короткие, а лишний
 * переход означал бы ещё одну кнопку «Назад», которую надо запомнить.
 */
@Composable
private fun CollectionPicker(
    state: BookDetailsUiState,
    onPick: (String) -> Unit,
    onClose: () -> Unit,
) {
    Column {
        EinkSectionHeader(title = "В какую полку", count = state.collections.size)
        if (state.collections.isEmpty()) {
            EinkEmptyState(
                title = "Полок пока нет",
                message = "Создайте полку на экране «Полки», потом вернитесь сюда.",
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            state.collections.forEach { collection ->
                EinkListRow(
                    title = collection.name,
                    trailing = if (collection.containsBook) "уже там" else "добавить",
                    onClick = { onPick(collection.id) },
                )
            }
        }
        EinkButton(
            text = "Отмена",
            onClick = onClose,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = LocalEinkDimens.current.gutter),
        )
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
                text = state.readingStatus.label,
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
private fun MetadataRows(
    state: BookDetailsUiState,
    onOpenAuthor: () -> Unit,
    onOpenSeries: () -> Unit,
) {
    Column {
        // Автор и серия — переходы, а не просто текст (требование Александра, 28.09.2026):
        // по автору открывается вид «Авторы» на нём с его сериями, по серии — книги серии.
        // Так же сделано в карточке записи каталога OPDS.
        if (state.authors.isNotBlank()) {
            MetadataRow(
                label = "Автор: ${state.authors}",
                value = null,
                subtitle = "Нажмите, чтобы открыть его книги и серии",
                onClick = onOpenAuthor,
            )
        }
        state.series?.let {
            MetadataRow(
                label = "Серия: $it",
                value = null,
                subtitle = "Нажмите, чтобы открыть книги серии",
                onClick = onOpenSeries,
            )
        }
        state.publishedYear?.let { MetadataRow(label = "Год", value = it.toString()) }
        state.language?.let { MetadataRow(label = "Язык", value = it) }
        state.publisher?.let { MetadataRow(label = "Издательство", value = it) }
        state.isbn?.let { MetadataRow(label = "ISBN", value = it) }
        state.pageCount?.let { MetadataRow(label = "Страниц", value = it.toString()) }
        state.sizeLabel?.let { MetadataRow(label = "Размер", value = it) }
        state.addedAtLabel?.let { MetadataRow(label = "Добавлена", value = it) }
    }
}

/** Одна строка метаданных: подпись слева, значение справа; `onClick` делает строку переходом. */
@Composable
private fun MetadataRow(
    label: String,
    value: String?,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
) {
    EinkListRow(
        title = label,
        subtitle = subtitle,
        trailing = if (onClick != null) "→" else value,
        onClick = onClick,
    )
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
    val path = filePathLabel(sourceName = state.sourceName, relativePath = state.relativePath)
    Column {
        EinkSectionHeader(title = "Файл")
        // Строки «Документ» больше нет: там показывался внутренний SAF-адрес файла
        // (`content://…%2F…`) — приложению он нужен, пользователю ничего не говорит.
        EinkListRow(title = "Путь", subtitle = path.ifEmpty { null })
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

        // Удаление стоит последним и отдельной строкой: это единственное необратимое действие
        // карточки, и рядом с повседневными кнопками ему не место.
        if (state.isConfirmingDelete) {
            EinkConfirmRow(
                question = "Удалить книгу и файл с устройства? Вернуть её можно будет только " +
                    "копированием файла заново.",
                confirmLabel = if (state.isDeleting) "Удаляем…" else "Удалить",
                onConfirm = viewModel::onDeletePermanently,
                onCancel = viewModel::onCancelDelete,
                enabled = !state.isDeleting,
            )
        } else {
            EinkButton(
                text = "Удалить навсегда",
                onClick = viewModel::onAskDelete,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}


