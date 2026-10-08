package io.github.bookshelf.feature.bookdetails.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bookshelf.core.data.model.ReadingStatus
import io.github.bookshelf.core.designsystem.component.BookCover
import io.github.bookshelf.core.designsystem.component.EinkButton
import io.github.bookshelf.core.designsystem.component.EinkConfirmRow
import io.github.bookshelf.core.designsystem.component.EinkEmptyState
import io.github.bookshelf.core.designsystem.component.EinkErrorBanner
import io.github.bookshelf.core.designsystem.text.resolve
import io.github.bookshelf.core.designsystem.component.EinkListRow
import io.github.bookshelf.core.designsystem.component.EinkScaffold
import io.github.bookshelf.core.designsystem.component.EinkSectionHeader
import io.github.bookshelf.core.designsystem.input.EinkAction
import io.github.bookshelf.core.designsystem.text.readingStatusText
import io.github.bookshelf.core.designsystem.text.sourceNameOrFolder
import io.github.bookshelf.core.designsystem.theme.LocalEinkColors
import io.github.bookshelf.core.designsystem.theme.LocalEinkDimens
import io.github.bookshelf.feature.bookdetails.BookDetailsUiState
import io.github.bookshelf.feature.bookdetails.BookDetailsViewModel
import io.github.bookshelf.feature.bookdetails.SeriesNeighbourUi
import io.github.bookshelf.feature.bookdetails.filePathLabel
import io.github.bookshelf.feature.bookdetails.R

/** Ширина обложки в карточке. */
private val COVER_WIDTH = 160.dp

/**
 * Карточка книги («E-ink UX» §6.4).
 *
 * Скелет этапа каркаса: показаны обложка, все поля метаданных, аннотация, путь и действия.
 * Данные появятся в Sprint 4 (`BookRepository.observe(bookId)`), открытие в читалке — Sprint 5.
 *
 * Вертикальный скролл здесь разрешён осознанно: это единственный экран, где «иначе нельзя»
 * («E-ink UX» §3.1), список книг всегда листается страницами.
 */
@Composable
fun BookDetailsScreen(
    bookId: String,
    onBack: () -> Unit,
    onRead: (String) -> Unit,
    onOpenCollections: (String) -> Unit,
    onEditMetadata: (String) -> Unit,
    /** Перейти к соседней книге серии: стрелки «назад» и «вперёд» в подвале. */
    onOpenSeriesBook: (String) -> Unit,
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
        title = state.title.ifEmpty { stringResource(R.string.book_title) },
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
            BookReadFooter(
                bookId = bookId,
                state = state,
                onRead = onRead,
                onOpenSeriesBook = onOpenSeriesBook,
            )
        },
    ) {
        BookDetailsContent(
            state = state,
            bookId = bookId,
            viewModel = viewModel,
            onEditMetadata = onEditMetadata,
            onOpenLibrary = onOpenLibrary,
        )

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
        EinkSectionHeader(title = stringResource(R.string.book_collections_title), count = state.collections.size)
        if (state.collections.isEmpty()) {
            EinkEmptyState(
                title = stringResource(R.string.book_collections_empty_title),
                message = stringResource(R.string.book_collections_empty_message),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            state.collections.forEach { collection ->
                EinkListRow(
                    title = collection.name,
                    trailing = if (collection.containsBook) {
                        stringResource(R.string.book_collection_present)
                    } else {
                        stringResource(R.string.book_collection_add)
                    },
                    onClick = { onPick(collection.id) },
                )
            }
        }
        EinkButton(
            text = stringResource(R.string.book_action_cancel),
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
            contentDescription = stringResource(
                R.string.book_cover_description,
                state.title,
                state.authors,
            ),
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
                text = state.readingStatus.name
                    .let { name -> readingStatusText(name)?.resolve() ?: name },
                style = MaterialTheme.typography.labelMedium,
                color = colors.foreground,
            )
            if (state.isFavorite) {
                Text(
                    text = stringResource(R.string.book_favorite_yes),
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
                label = stringResource(R.string.book_author_line, state.authors),
                value = null,
                subtitle = stringResource(R.string.book_author_hint),
                onClick = onOpenAuthor,
            )
        }
        state.series?.let {
            MetadataRow(
                label = stringResource(R.string.book_series_line, it),
                value = null,
                subtitle = stringResource(R.string.book_series_hint),
                onClick = onOpenSeries,
            )
        }
        state.publishedYear?.let { MetadataRow(label = stringResource(R.string.book_year), value = it.toString()) }
        state.language?.let { MetadataRow(label = stringResource(R.string.book_language), value = it) }
        state.publisher?.let { MetadataRow(label = stringResource(R.string.book_publisher), value = it) }
        state.isbn?.let { MetadataRow(label = "ISBN", value = it) }
        state.pageCount?.let { MetadataRow(label = stringResource(R.string.book_pages), value = it.toString()) }
        state.sizeLabel?.let { size ->
            MetadataRow(label = stringResource(R.string.book_size), value = size.resolve())
        }
        state.addedAtLabel?.let { MetadataRow(label = stringResource(R.string.book_added), value = it) }
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
        EinkSectionHeader(title = stringResource(R.string.edit_field_description_hint))
        Text(
            text = description ?: stringResource(R.string.book_description_missing),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.foreground,
        )
    }
}

/** Путь к файлу — по нему пользователь понимает, откуда пришла книга. */
@Composable
private fun FileBlock(state: BookDetailsUiState) {
    // Имя папки-источника — данные; у источника без имени слова подставляет экран.
    val path = filePathLabel(
        sourceName = sourceNameOrFolder(state.sourceName),
        relativePath = state.relativePath,
    )
    Column {
        EinkSectionHeader(title = stringResource(R.string.book_file))
        // Строки «Документ» больше нет: там показывался внутренний SAF-адрес файла
        // (`content://…%2F…`) — приложению он нужен, пользователю ничего не говорит.
        EinkListRow(title = stringResource(R.string.book_path), subtitle = path.ifEmpty { null })
    }
}

/**
 * Кнопки действий с книгой: избранное, статус чтения, полки, правка, обновление и папка.
 *
 * Вынесены из `ActionsBlock` отдельной функцией: он перевалил за предел длины detekt, а это
 * цельный кусок — три пары кнопок, не зависящие от оптимизации и удаления.
 */
@Composable
private fun BookActionButtons(
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
            text = if (state.isFavorite) {
                stringResource(R.string.book_favorite_remove)
            } else {
                stringResource(R.string.book_favorite_no)
            },
            onClick = viewModel::onToggleFavorite,
            modifier = Modifier.weight(1f),
        )
        EinkButton(
            text = if (state.readingStatus == ReadingStatus.READ) {
                stringResource(R.string.book_mark_unread)
            } else {
                stringResource(R.string.book_mark_read)
            },
            onClick = viewModel::onToggleRead,
            modifier = Modifier.weight(1f),
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(dimens.gutterSmall),
    ) {
        EinkButton(
            text = stringResource(R.string.book_add_to_collection),
            onClick = onOpenCollections,
            modifier = Modifier.weight(1f),
        )
        EinkButton(
            text = stringResource(R.string.book_action_edit),
            onClick = onEditMetadata,
            modifier = Modifier.weight(1f),
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(dimens.gutterSmall),
    ) {
        EinkButton(
            text = stringResource(R.string.book_action_refresh),
            onClick = viewModel::onRefreshMetadata,
            modifier = Modifier.weight(1f),
        )
        EinkButton(
            text = stringResource(R.string.book_action_show_in_folder),
            onClick = viewModel::onShowInFolder,
            modifier = Modifier.weight(1f),
        )
    }
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
        // Результат оптимизации показывается отдельной строкой, а не плашкой ошибки: успех и сбой
        // не должны выглядеть одинаково.
        state.optimizationMessage?.let { message ->
            EinkListRow(title = message.resolve(), subtitle = stringResource(R.string.book_optimization_hint))
        }
        BookActionButtons(
            state = state,
            onOpenCollections = onOpenCollections,
            onEditMetadata = onEditMetadata,
            viewModel = viewModel,
        )

        // Кнопка есть только у FB2 и FB2.ZIP: механизм оптимизации других форматов не касается,
        // и кнопка-обманка здесь была бы хуже её отсутствия.
        if (state.canOptimize) {
            EinkButton(
                text = if (state.isOptimizing) {
                    stringResource(R.string.book_action_optimizing)
                } else {
                    stringResource(R.string.book_action_optimize)
                },
                onClick = viewModel::onOptimize,
                enabled = !state.isOptimizing,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Удаление стоит последним и отдельной строкой: это единственное необратимое действие
        // карточки, и рядом с повседневными кнопками ему не место.
        if (state.isConfirmingDelete) {
            EinkConfirmRow(
                question = stringResource(R.string.book_delete_confirm),
                confirmLabel = if (state.isDeleting) {
                    stringResource(R.string.book_action_deleting)
                } else {
                    stringResource(R.string.book_action_delete)
                },
                onConfirm = viewModel::onDeletePermanently,
                onCancel = viewModel::onCancelDelete,
                enabled = !state.isDeleting,
            )
        } else {
            EinkButton(
                text = stringResource(R.string.book_action_delete_forever),
                onClick = viewModel::onAskDelete,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}


/**
 * Подвал карточки: стрелки по серии и главная кнопка «Читать».
 *
 * Требование Александра (08.10.2026): если у книги есть более ранняя книга серии — слева «назад»,
 * если есть следующая — справа «вперёд», посередине «Читать». На второй книге серии из трёх видны
 * обе стрелки.
 */
@Composable
private fun BookReadFooter(
    bookId: String,
    state: BookDetailsUiState,
    onRead: (String) -> Unit,
    onOpenSeriesBook: (String) -> Unit,
) {
    val dimens = LocalEinkDimens.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(dimens.gutter),
        horizontalArrangement = Arrangement.spacedBy(dimens.gutterSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SeriesArrowButton(
            labelRes = R.string.book_series_previous,
            neighbour = state.previousInSeries,
            arrow = "←",
            onOpenSeriesBook = onOpenSeriesBook,
            modifier = Modifier.weight(1f),
        )
        EinkButton(
            text = stringResource(R.string.book_action_read),
            onClick = { onRead(bookId) },
            primary = true,
            modifier = Modifier.weight(2f),
        )
        SeriesArrowButton(
            labelRes = R.string.book_series_next,
            neighbour = state.nextInSeries,
            arrow = "→",
            onOpenSeriesBook = onOpenSeriesBook,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Стрелка перехода по серии.
 *
 * Место под стрелку сохраняется и тогда, когда соседа нет: иначе «Читать» прыгала бы влево-вправо на
 * каждой книге серии. Подпись для чтения с экрана называет соседнюю книгу — «←» сама по себе ничего
 * не объясняет, а название и есть то, что человеку нужно перед нажатием.
 */
@Composable
private fun SeriesArrowButton(
    labelRes: Int,
    neighbour: SeriesNeighbourUi?,
    arrow: String,
    onOpenSeriesBook: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (neighbour == null) {
        Box(modifier = modifier)
        return
    }
    val label = stringResource(labelRes, neighbour.title)
    EinkButton(
        text = arrow,
        onClick = { onOpenSeriesBook(neighbour.bookId) },
        modifier = modifier.semantics { contentDescription = label },
    )
}

/** Баннер ошибки карточки книги: переводимый текст по типу и кнопка «Повторить». */
@Composable
private fun BookErrorBanner(state: BookDetailsUiState, onRetry: () -> Unit) {
    val error = state.error ?: return
    EinkErrorBanner(
        message = error.resolve(),
        actionLabel = stringResource(R.string.book_action_retry),
        onAction = onRetry,
    )
}


/**
 * Содержимое карточки: шапка, выбор полки, причина отказа разбора и блоки сведений.
 *
 * Вынесено из `BookDetailsScreen` отдельной функцией: та перевалила за предел длины detekt,
 * а содержимое — цельный кусок, зависящий только от состояния и колбэков.
 */
@Composable
private fun BookDetailsContent(
    state: BookDetailsUiState,
    bookId: String,
    viewModel: BookDetailsViewModel,
    onEditMetadata: (String) -> Unit,
    onOpenLibrary: () -> Unit,
) {
    val dimens = LocalEinkDimens.current
    val contentScroll = rememberScrollState()

    // Строка подтверждения выше кнопки, которую она заменяет, а прокрутка остаётся на прежнем
    // месте: низ строки уходил за границу окна, кнопки «Удалить»/«Отмена» обрезались до полоски,
    // и тап по этой полоске попадал в закреплённый подвал «Читать» — вместо подтверждения
    // удаления открывалась читалка (дефект с устройства 02.10.2026). Поэтому доводим содержимое
    // до конца списка: подтверждение — последний его элемент. Прыжком, а не анимацией: на e-ink
    // анимации запрещены («E-ink UX» §5).
    LaunchedEffect(state.isConfirmingDelete) {
        if (state.isConfirmingDelete) {
            // Кадр пропускаем: разметка с новой строкой ещё не пересчитана, и `maxValue` пока
            // указывает туда, где строки подтверждения нет.
            withFrameNanos { }
            contentScroll.scrollTo(contentScroll.maxValue)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(contentScroll)
            .padding(dimens.gutter),
        verticalArrangement = Arrangement.spacedBy(dimens.gutter),
    ) {
        BookErrorBanner(state = state, onRetry = viewModel::onRefreshMetadata)

        BookHeader(state = state)

        if (state.isPickingCollection) {
            CollectionPicker(
                state = state,
                onPick = viewModel::onAddToCollection,
                onClose = viewModel::onCloseCollectionPicker,
            )
            return@Column
        }

        val parseFailure = state.parseFailure
        if (parseFailure != null) {
            EinkErrorBanner(
                message = parseFailure.resolve(),
                actionLabel = stringResource(R.string.book_action_fill_manually),
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
