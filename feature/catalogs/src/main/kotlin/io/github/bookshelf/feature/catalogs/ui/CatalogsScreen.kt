package io.github.bookshelf.feature.catalogs.ui

import io.github.bookshelf.feature.catalogs.messageText
import io.github.bookshelf.feature.catalogs.namedText
import io.github.bookshelf.feature.catalogs.FeedLabel
import io.github.bookshelf.feature.catalogs.FeedUi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bookshelf.core.designsystem.component.BookCover
import io.github.bookshelf.core.designsystem.component.EinkButton
import io.github.bookshelf.core.designsystem.component.EinkConfirmRow
import io.github.bookshelf.core.designsystem.component.EinkCheckbox
import io.github.bookshelf.core.designsystem.component.EinkEmptyState
import io.github.bookshelf.core.designsystem.component.EinkErrorBanner
import io.github.bookshelf.core.designsystem.component.EinkListRow
import io.github.bookshelf.core.designsystem.component.EinkProgress
import io.github.bookshelf.core.designsystem.component.EinkScaffold
import io.github.bookshelf.core.designsystem.component.EinkTextField
import io.github.bookshelf.core.designsystem.component.clickableNoIndication
import io.github.bookshelf.core.designsystem.input.EinkAction
import io.github.bookshelf.core.designsystem.paging.rememberEinkListPageScroller
import io.github.bookshelf.core.designsystem.paging.rememberEinkGridPageScroller
import io.github.bookshelf.core.designsystem.theme.LocalEinkColors
import io.github.bookshelf.core.designsystem.theme.LocalEinkDimens
import io.github.bookshelf.feature.catalogs.CatalogUi
import io.github.bookshelf.feature.catalogs.CatalogsMode
import io.github.bookshelf.feature.catalogs.CatalogsUiState
import io.github.bookshelf.feature.catalogs.CatalogsViewModel
import io.github.bookshelf.feature.catalogs.downloadsButtonLabel
import io.github.bookshelf.feature.catalogs.entryCoverOf
import io.github.bookshelf.feature.catalogs.DownloadUi
import io.github.bookshelf.feature.catalogs.FeedEntryUi
import io.github.bookshelf.feature.catalogs.R
import io.github.bookshelf.core.designsystem.text.resolve
import io.github.bookshelf.core.designsystem.text.resolve
import io.github.bookshelf.core.designsystem.theme.EinkDimens
import io.github.bookshelf.feature.catalogs.AddCatalogFormUi

/** Полная шкала прогресса: `progressPercent` приходит в процентах, `EinkProgress` ждёт 0..1. */
private const val PERCENT_MAX = 100f

/**
 * OPDS: каталоги, добавление, браузер фида и загрузки (docs/opds.md §3).
 *
 * Скелет этапа каркаса: переключение экранов, форма и поисковая строка работают без сети,
 * сами запросы появятся в Sprint 8. Каждый режим — отдельный `EinkScaffold`, поэтому
 * заголовок и кнопка «Назад» ведут себя по-разному.
 */
@Composable
fun CatalogsScreen(
    catalogId: String?,
    onBack: () -> Unit,
    onOpenBook: (String) -> Unit,
    onOpenEntry: (String, String, String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CatalogsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(catalogId) {
        // Не null, а пустая строка: шаблон маршрута содержит ?catalogId={catalogId}, и незаполненный
        // аргумент приходит как "". Проверка на null проходила, и экран открывал фид каталога "",
        // уходя из списка. Поэтому пустое — тоже отсутствие.
        if (!catalogId.isNullOrBlank()) viewModel.onOpenCatalog(catalogId)
    }

    when (state.mode) {
        CatalogsMode.LIST -> CatalogsListScreen(
            state = state,
            viewModel = viewModel,
            onBack = onBack,
            modifier = modifier,
        )

        CatalogsMode.ADD_FORM -> AddCatalogScreen(
            state = state,
            viewModel = viewModel,
            onBack = viewModel::onBackToList,
            modifier = modifier,
        )

        CatalogsMode.FEED -> FeedBrowserScreen(
            state = state,
            viewModel = viewModel,
            // Уровень выше по истории разделов, а в корне каталога — к списку каталогов.
            onBack = viewModel::onBack,
            onOpenEntry = onOpenEntry,
            modifier = modifier,
        )

        CatalogsMode.DOWNLOADS -> DownloadsScreen(
            state = state,
            viewModel = viewModel,
            onBack = viewModel::onBackToList,
            onOpenBook = onOpenBook,
            modifier = modifier,
        )
    }
}


/** Баннер ошибки списка каталогов: вынесен ради предела длины `CatalogsListScreen`. */
@Composable
private fun CatalogsListErrorBanner(state: CatalogsUiState, onRetry: () -> Unit) {
    val error = state.error ?: return
    EinkErrorBanner(
        message = error.resolve(),
        actionLabel = stringResource(R.string.catalogs_action_refresh),
        onAction = onRetry,
    )
}
/** Подвал списка каталогов: число каталогов и кнопка добавления. */
@Composable
private fun CatalogsListFooter(count: Int, onAddCatalog: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        StatusFooter(label = stringResource(R.string.catalogs_count, count))
        EinkButton(
            text = stringResource(R.string.catalogs_action_add_catalog),
            onClick = onAddCatalog,
            primary = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Аппаратные клавиши браузера фида: страниц у фида нет, список прокручивается целиком. */
private fun feedBrowserKeyAction(
    action: EinkAction,
    scrollEntries: (Int) -> Unit,
    onSearchInFeed: () -> Unit,
    onOpenDownloads: () -> Unit,
    onBack: () -> Unit,
) {
    when (action) {
        EinkAction.PREVIOUS_PAGE -> scrollEntries(-1)
        EinkAction.NEXT_PAGE -> scrollEntries(1)
        EinkAction.SEARCH -> onSearchInFeed()
        EinkAction.MENU -> onOpenDownloads()
        EinkAction.BACK -> onBack()
        else -> Unit
    }
}

/** Подвал формы каталога: «Отмена» и «Проверить и добавить» либо «Сохранить». */
@Composable
private fun AddCatalogFooter(
    isEditing: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onCheckAndAdd: () -> Unit,
) {
    val dimens = LocalEinkDimens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.gutter, vertical = dimens.gutterSmall),
        horizontalArrangement = Arrangement.spacedBy(dimens.gutterSmall),
    ) {
        EinkButton(
            text = stringResource(R.string.catalogs_action_cancel),
            onClick = onBack,
            modifier = Modifier.weight(1f),
        )
        EinkButton(
            text = if (isEditing) {
                stringResource(R.string.catalogs_action_save)
            } else {
                stringResource(R.string.catalogs_action_check_and_add)
            },
            onClick = if (isEditing) onSave else onCheckAndAdd,
            primary = true,
            modifier = Modifier.weight(1f),
        )
    }
}
/** Список добавленных каталогов. */
@Composable
private fun CatalogsListScreen(
    state: CatalogsUiState,
    viewModel: CatalogsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalEinkDimens.current
    // Каталогов у человека единицы, и страницы тут не нужны: список прокручивается целиком.
    // Прежнее постраничное листание работало только аппаратными клавишами, а кнопок на экране
    // не было — на устройстве без таких клавиш вторая страница была недостижима вовсе.
    val listState = rememberLazyListState()
    val scrollByPage = rememberEinkListPageScroller(listState)

    EinkScaffold(
        title = stringResource(R.string.catalogs_title),
        subtitle = stringResource(R.string.catalogs_subtitle),
        modifier = modifier,
        onBack = onBack,
        actions = {
            EinkButton(
                text = stringResource(R.string.catalogs_action_add),
                onClick = viewModel::onOpenAddForm,
            )
            EinkButton(
                text = stringResource(R.string.catalogs_downloads_title),
                onClick = viewModel::onOpenDownloads,
            )
        },
        onKeyAction = { action ->
            when (action) {
                EinkAction.PREVIOUS_PAGE -> scrollByPage(-1)
                EinkAction.NEXT_PAGE -> scrollByPage(1)
                EinkAction.MENU -> viewModel.onOpenAddForm()
                EinkAction.BACK -> onBack()
                else -> Unit
            }
        },
        footer = {
            CatalogsListFooter(
                count = state.catalogs.size,
                onAddCatalog = viewModel::onOpenAddForm,
            )
        },
    ) {
        CatalogsListContent(
            state = state,
            viewModel = viewModel,
            listState = listState,
        )
    }
}

/**
 * Содержимое списка каталогов: баннер ошибки, пустое состояние и строки с действиями.
 *
 * Вынесено из `CatalogsListScreen` отдельной функцией: экран перевалил за предел длины detekt,
 * а содержимое — цельный кусок, зависящий только от состояния, списка и ViewModel.
 */
@Composable
private fun ColumnScope.CatalogsListContent(
    state: CatalogsUiState,
    viewModel: CatalogsViewModel,
    listState: LazyListState,
) {
    val dimens = LocalEinkDimens.current
    CatalogsListErrorBanner(state = state, onRetry = viewModel::onBackToList)

    Box(
            modifier = Modifier.weight(1f),
        ) {
        if (state.catalogs.isEmpty()) {
            EinkEmptyState(
                title = stringResource(R.string.catalogs_empty_title),
                message = stringResource(R.string.catalogs_empty_message),
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            catalogsList(
                state = state,
                viewModel = viewModel,
                listState = listState,
                dimens = dimens,
            )

        }
    }
}

/** Форма ручного добавления каталога (docs/opds.md §3.1). */
@Composable
private fun AddCatalogScreen(
    state: CatalogsUiState,
    viewModel: CatalogsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalEinkDimens.current
    val form = state.addForm

    val isEditing = state.editingCatalogId != null
    EinkScaffold(
        title = if (isEditing) {
            stringResource(R.string.catalogs_form_edit_title)
        } else {
            stringResource(R.string.catalogs_form_new_title)
        },
        subtitle = if (isEditing) {
            stringResource(R.string.catalogs_form_edit_hint)
        } else {
            stringResource(R.string.catalogs_form_new_hint)
        },
        modifier = modifier,
        onBack = onBack,
        onKeyAction = { action ->
            when (action) {
                EinkAction.BACK -> onBack()
                else -> Unit
            }
        },
        footer = {
            AddCatalogFooter(
                isEditing = isEditing,
                onBack = onBack,
                onSave = viewModel.catalogActions::onSaveChanges,
                onCheckAndAdd = viewModel::onCheckAndAdd,
            )
        },
    ) {
        // Ошибка валидации раньше не отображалась вовсе: нажатие выглядело как «ничего не
        // происходит». В footer её ставить нельзя — там Row с кнопками, и баннер их вытеснял.
        state.error?.let { message ->
            EinkErrorBanner(message = message.resolve(), actionLabel = null, onAction = {})
        }

        if (state.isLoading) {
            Text(
                text = stringResource(R.string.catalogs_form_checking),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = dimens.gutterSmall),
            )
        }

        AddCatalogFields(form = form, isEditing = isEditing, viewModel = viewModel)
    }
}

/** Браузер фида: хлебные крошки, поиск по каталогу и обложки записей. */
@Composable
private fun FeedBrowserScreen(
    state: CatalogsUiState,
    viewModel: CatalogsViewModel,
    onBack: () -> Unit,
    onOpenEntry: (String, String, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalEinkColors.current
    val dimens = LocalEinkDimens.current
    val feed = state.currentFeed
    val entries = feed?.entries.orEmpty()
    val entriesGridState = rememberLazyGridState()
    val scrollEntries = rememberEinkGridPageScroller(entriesGridState)

    EinkScaffold(
        title = feed?.title?.label() ?: stringResource(R.string.catalogs_browser_title),
        subtitle = crumbText(feed),
        modifier = modifier,
        onBack = onBack,
        // Кнопка правого края: из любого уровня каталога возвращает к списку каталогов одним
        // касанием. Левая «← Назад» идёт на уровень выше, и из глубокого раздела до списка
        // приходилось щёлкать её несколько раз.
        actions = {
            FeedBrowserActions(
                downloads = state.downloads,
                onOpenDownloads = viewModel::onOpenDownloads,
                onBackToList = viewModel::onBackToList,
            )
        },
        onKeyAction = { action ->
            feedBrowserKeyAction(
                action = action,
                scrollEntries = scrollEntries,
                onSearchInFeed = viewModel::onSearchInFeed,
                onOpenDownloads = viewModel::onOpenDownloads,
                onBack = onBack,
            )
        },
        // Страниц у фида больше нет: весь список прокручивается. Подпись «Стр. 1/2» вводила
        // в заблуждение — вторая страница была недостижима, а список на самом деле целиком.
        footer = {
            StatusFooter(
                label = feedStatusLabel(
                    loaded = entries.size,
                    hasNext = feed?.hasNextPage == true,
                    isNavigation = feed?.isNavigation == true,
                ),
            )
        },
    ) {
        FeedBrowserContent(
            state = state,
            viewModel = viewModel,
            feed = feed,
            entries = entries,
            entriesGridState = entriesGridState,
            onOpenEntry = onOpenEntry,
        )
    }
}

/**
 * Подпись о записях фида.
 *
 * Точное число показывается только там, где точно известно, что страниц больше нет
 * (`rel="next"` отсутствует): OPDS не отдаёт общее количество записей, и «записей: 20» на фиде
 * из двухсот книг читалось как реальное число, хотя это была всего одна страница.
 */
@Composable
private fun feedStatusLabel(loaded: Int, hasNext: Boolean, isNavigation: Boolean): String = when {
    loaded == 0 && !hasNext -> stringResource(R.string.catalogs_nothing_found_short)
    hasNext -> stringResource(R.string.catalogs_feed_loaded_more, loaded)
    isNavigation -> stringResource(R.string.catalogs_feed_sections, loaded)
    else -> stringResource(R.string.catalogs_feed_books, loaded)
}

/**
 * Строка индикации загрузки: подпись, что именно грузится.
 *
 * Без полосы прогресса: у запроса фида нет измеримого прогресса, а полоса «на глаз» или на нуле
 * врала бы о состоянии. На e-ink статичная подпись честнее и не мигает (docs/eink-ux.md §5).
 */
@Composable
internal fun LoadingRow(label: String) {
    EinkListRow(
        title = label,
        subtitle = stringResource(R.string.catalogs_network_request),
        hasDivider = false,
    )
}

/**
 * Записи фида: разделы каталога — списком, книги — плитками с обложками, как на главной полке.
 *
 * Один ленивый контейнер и навигационные строки во всю ширину (span): смешанный фид встречается
 * у Флибусты, и два независимых списка прокручивались бы по отдельности.
 */
@Composable
internal fun FeedContent(
    entries: List<FeedEntryUi>,
    gridState: LazyGridState,
    columns: GridCells,
    hasNextPage: Boolean,
    isLoadingMore: Boolean,
    onOpen: (FeedEntryUi) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {

    // Текучая догрузка: как только до конца списка осталось меньше экрана, просим следующую
    // страницу фида. Без этого фид длиннее одной страницы обрывался на первой.
    val atListEnd by remember {
        derivedStateOf {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= gridState.layoutInfo.totalItemsCount - LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(atListEnd, hasNextPage) {
        if (atListEnd && hasNextPage) onLoadMore()
    }

    LazyVerticalGrid(
        columns = columns,
        state = gridState,
        modifier = modifier.fillMaxSize(),
    ) {
        val sections = entries.filter { entry -> entry.isSection }
        val books = entries.filterNot { entry -> entry.isSection }

        sections.forEach { section ->
            item(span = { GridItemSpan(maxLineSpan) }, key = "section:${section.id}") {
                EinkListRow(
                    title = section.title,
                    subtitle = section.authors.takeIf { it.isNotBlank() },
                    trailing = stringResource(R.string.catalogs_section_marker),
                    onClick = { onOpen(section) },
                )
            }
        }
        gridItemsIndexed(items = books, key = { index, entry -> "$index:${entry.id}" }) { _, entry ->
            EntryCard(entry = entry, onClick = { onOpen(entry) })
        }
        if (isLoadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "loading-more") {
                LoadingRow(label = stringResource(R.string.catalogs_loading_next))
            }
        }
    }
}

/** За сколько записей до конца списка начинать догрузку: примерно один экран на e-ink. */
private const val LOAD_MORE_THRESHOLD = 6

/** Карточка записи фида: тап и кнопка делают одно и то же — открывают раздел или качают книгу. */
@Composable
private fun EntryCard(
    entry: FeedEntryUi,
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
        // Обложка записи: локальный кэш, если книга уже скачана, иначе ссылка из фида. Раньше
        // карточка отдавала только `coverPath` (у записей OPDS он всегда null) — в списке книг
        // вместо обложек рисовалась заглушка.
        val cover = entryCoverOf(entry)
        BookCover(
            coverPath = cover.path,
            coverUrl = cover.url,
            title = entry.title,
            modifier = Modifier.fillMaxWidth(),
            contentDescription = stringResource(
                R.string.catalogs_cover_description,
                entry.title,
                entry.authors,
            ),
        )
        Text(
            text = entry.title,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.foreground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = entry.authors,
            style = MaterialTheme.typography.bodySmall,
            color = colors.foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = entryMeta(entry),
            style = MaterialTheme.typography.labelMedium,
            color = colors.foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // Состояние загрузки книги — в самой карточке, там же, где пользователь нажал «Скачать».
        // Раньше после постановки в очередь не было ни подписи, ни прогресса: казалось, что
        // ничего не произошло.
        entry.download?.let { download ->
            Text(
                text = download.label.resolve(),
                style = MaterialTheme.typography.labelMedium,
                color = colors.foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Отдельной кнопки нет намеренно: карточка целиком и есть элемент управления.
        // Кнопка внутри кликабельной карточки была мёртвой — нажатие на подпись не
        // срабатывало, хотя рядом работало нажатие на саму карточку, и это выглядело как
        // «приложение зависло». Одно действие — одна зона касания.
    }
}

/** Экран загрузок: активные, завершённые и неудавшиеся. */
@Composable
private fun DownloadsScreen(
    state: CatalogsUiState,
    viewModel: CatalogsViewModel,
    onBack: () -> Unit,
    onOpenBook: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Постраничной нарезки больше нет: список один и длинный, и до второй страницы пальцем
    // было не добраться (прокрутка была выключена, листали только аппаратные клавиши).
    val listState = rememberLazyListState()
    val scrollByPage = rememberEinkListPageScroller(listState)

    EinkScaffold(
        title = stringResource(R.string.catalogs_downloads_title),
        subtitle = stringResource(R.string.catalogs_downloads_subtitle),
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
        actions = {
            // Кнопка показывается только когда есть что убирать: пустое действие сбивает с толку.
            if (state.downloads.isNotEmpty()) {
                EinkButton(
                    text = stringResource(R.string.catalogs_action_clear_history),
                    onClick = viewModel::onToggleClearHistory,
                )
            }
        },
        footer = { StatusFooter(label = downloadCountLabel(state.downloads.size)) },
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (state.isConfirmingClearHistory) {
                EinkConfirmRow(
                    question = stringResource(R.string.catalogs_downloads_clear_confirm),
                    confirmLabel = stringResource(R.string.catalogs_action_remove),
                    onConfirm = viewModel::onConfirmClearHistory,
                    onCancel = viewModel::onToggleClearHistory,
                )
            }
            if (state.downloads.isEmpty()) {
                EinkEmptyState(
                    title = stringResource(R.string.catalogs_downloads_empty_title),
                    message = stringResource(R.string.catalogs_downloads_empty_message),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    listItems(items = state.downloads, key = { download -> download.id }) { download ->
                        DownloadRow(
                            download = download,
                            onCancel = { viewModel.onCancelDownload(download.id) },
                            onOpenBook = onOpenBook,
                        )
                    }
                }
            }
        }
    }
}

/** Подпись под списком загрузок: сколько всего строк в очереди и в истории. */
@Composable
private fun downloadCountLabel(count: Int): String = when (count) {
    0 -> stringResource(R.string.catalogs_downloads_none)
    1 -> stringResource(R.string.catalogs_downloads_one)
    else -> stringResource(R.string.catalogs_downloads_many, count)
}


/** Строка загрузки: полоса прогресса только у активных, ошибка — инвертированной плашкой. */
@Composable
private fun DownloadRow(
    download: DownloadUi,
    onCancel: () -> Unit,
    onOpenBook: (String) -> Unit,
) {
    val openDownloadedBook: (() -> Unit)? = download.bookId?.let { bookId -> { onOpenBook(bookId) } }

    Column {
        EinkListRow(
            title = download.title,
            subtitle = download.stateLabel.resolve(),
            trailing = "${download.progressPercent}%",
            onClick = openDownloadedBook,
        )
        if (download.isActive) {
            EinkProgress(
                caption = stringResource(R.string.catalogs_download_status_downloading),
                progress = download.progressPercent / PERCENT_MAX,
                detail = stringResource(R.string.catalogs_download_progress, download.progressPercent),
            )
            EinkButton(text = stringResource(R.string.catalogs_action_cancel_download), onClick = onCancel)
        }
        val failure = download.failure
        if (failure != null) {
            EinkErrorBanner(
                message = failure.resolve(),
                actionLabel = stringResource(R.string.catalogs_action_retry),
                onAction = onCancel,
            )
        }
    }
}

/** Нижняя строка со подписью «Стр. N/M · K …». */
@Composable
private fun StatusFooter(label: String) {
    val colors = LocalEinkColors.current
    val dimens = LocalEinkDimens.current
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = colors.foreground,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.gutter, vertical = dimens.gutterSmall),
    )
}

/**
 * Подпись под списком каталогов.
 *
 * Страниц нет — список прокручивается, поэтому подпись только считает каталоги: «Стр. 1/2»
 * обещала страницу, до которой нельзя было добраться пальцем.
 */
