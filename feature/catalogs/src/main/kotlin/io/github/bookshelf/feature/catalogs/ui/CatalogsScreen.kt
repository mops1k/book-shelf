package io.github.bookshelf.feature.catalogs.ui

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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bookshelf.core.designsystem.component.BookCover
import io.github.bookshelf.core.designsystem.component.EinkButton
import io.github.bookshelf.core.designsystem.component.EinkCheckbox
import io.github.bookshelf.core.designsystem.component.EinkEmptyState
import io.github.bookshelf.core.designsystem.component.EinkErrorBanner
import io.github.bookshelf.core.designsystem.component.EinkListRow
import io.github.bookshelf.core.designsystem.component.EinkProgress
import io.github.bookshelf.core.designsystem.component.EinkScaffold
import io.github.bookshelf.core.designsystem.component.EinkTextField
import io.github.bookshelf.core.designsystem.component.TapZoneLayout
import io.github.bookshelf.core.designsystem.component.clickableNoIndication
import io.github.bookshelf.core.designsystem.input.EinkAction
import io.github.bookshelf.core.designsystem.paging.rememberEinkPagerState
import io.github.bookshelf.core.designsystem.theme.LocalEinkColors
import io.github.bookshelf.core.designsystem.theme.LocalEinkDimens
import io.github.bookshelf.feature.catalogs.CatalogUi
import io.github.bookshelf.feature.catalogs.CatalogsMode
import io.github.bookshelf.feature.catalogs.CatalogsUiState
import io.github.bookshelf.feature.catalogs.CatalogsViewModel
import io.github.bookshelf.feature.catalogs.DownloadUi
import io.github.bookshelf.feature.catalogs.FeedEntryUi

/** Сколько каталогов, записей и загрузок помещается на «страницу». */
private const val CATALOGS_PAGE_SIZE = 12

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
    modifier: Modifier = Modifier,
    viewModel: CatalogsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(catalogId) {
        if (catalogId != null) viewModel.onOpenCatalog(catalogId)
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
            onBack = viewModel::onBackToList,
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

/** Список добавленных каталогов. */
@Composable
private fun CatalogsListScreen(
    state: CatalogsUiState,
    viewModel: CatalogsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pager = rememberEinkPagerState(pageSize = CATALOGS_PAGE_SIZE, totalItems = state.catalogs.size)

    LaunchedEffect(state.catalogs.size) { pager.updateTotalItems(state.catalogs.size) }

    EinkScaffold(
        title = "Каталоги OPDS",
        subtitle = "Ручное добавление по URL",
        modifier = modifier,
        onBack = onBack,
        actions = {
            EinkButton(text = "Добавить", onClick = viewModel::onOpenAddForm)
            EinkButton(text = "Загрузки", onClick = viewModel::onOpenDownloads)
        },
        onKeyAction = { action ->
            when (action) {
                EinkAction.PREVIOUS_PAGE -> pager.previous()
                EinkAction.NEXT_PAGE -> pager.next()
                EinkAction.MENU -> viewModel.onOpenAddForm()
                EinkAction.BACK -> onBack()
                else -> Unit
            }
        },
        footer = { StatusFooter(label = pager.statusLabel("каталогов")) },
    ) {
        val error = state.error
        if (error != null) {
            EinkErrorBanner(message = error, actionLabel = "Обновить", onAction = viewModel::onBackToList)
        }

        TapZoneLayout(
            onPrevious = pager::previous,
            onNext = pager::next,
            modifier = Modifier.weight(1f),
        ) {
            if (state.catalogs.isEmpty()) {
                EinkEmptyState(
                    title = "Раздел в разработке — Спринт 8",
                    message = "Здесь появятся OPDS-каталоги: добавление по URL с логином и паролем, " +
                        "браузер фида с хлебными крошками и загрузка книг в библиотеку.",
                    actionLabel = "Добавить каталог",
                    onAction = viewModel::onOpenAddForm,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                val visible = state.catalogs.drop(pager.offset).take(pager.pageSize)
                LazyColumn(modifier = Modifier.fillMaxSize(), userScrollEnabled = false) {
                    listItems(items = visible, key = { catalog -> catalog.id }) { catalog ->
                        EinkListRow(
                            title = catalog.title,
                            subtitle = catalog.url,
                            trailing = catalogTrailing(catalog),
                            onClick = { viewModel.onOpenCatalog(catalog.id) },
                        )
                    }
                }
            }
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

    EinkScaffold(
        title = "Новый каталог",
        subtitle = "Проверим фид перед добавлением",
        modifier = modifier,
        onBack = onBack,
        onKeyAction = { action ->
            when (action) {
                EinkAction.BACK -> onBack()
                else -> Unit
            }
        },
        footer = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.gutter, vertical = dimens.gutterSmall),
                horizontalArrangement = Arrangement.spacedBy(dimens.gutterSmall),
            ) {
                EinkButton(text = "Отмена", onClick = onBack, modifier = Modifier.weight(1f))
                EinkButton(
                    text = "Проверить и добавить",
                    onClick = viewModel::onCheckAndAdd,
                    primary = true,
                    modifier = Modifier.weight(1f),
                )
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(dimens.gutter),
            verticalArrangement = Arrangement.spacedBy(dimens.gutter),
        ) {
            EinkTextField(
                value = form.title,
                onValueChange = viewModel::onFormTitleChange,
                label = "Название",
                placeholder = "Моя библиотека",
            )
            EinkTextField(
                value = form.url,
                onValueChange = viewModel::onFormUrlChange,
                label = "URL каталога",
                placeholder = "https://example.org/opds",
            )
            EinkTextField(
                value = form.username,
                onValueChange = viewModel::onFormUsernameChange,
                label = "Логин (если требуется)",
            )
            // Пароль не логируется и не экспортируется: в БД попадает только alias.
            EinkTextField(
                value = form.password,
                onValueChange = viewModel::onFormPasswordChange,
                label = "Пароль (если требуется)",
                visualTransformation = PasswordVisualTransformation(),
            )
            EinkCheckbox(
                checked = form.openImmediately,
                onCheckedChange = viewModel::onFormOpenImmediatelyChange,
                label = "Открывать этот каталог сразу",
            )
            StubNotice(sprint = 8)
        }
    }
}

/** Браузер фида: хлебные крошки, поиск по каталогу и обложки записей. */
@Composable
private fun FeedBrowserScreen(
    state: CatalogsUiState,
    viewModel: CatalogsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalEinkColors.current
    val dimens = LocalEinkDimens.current
    val feed = state.currentFeed
    val entries = feed?.entries.orEmpty()
    val pager = rememberEinkPagerState(pageSize = CATALOGS_PAGE_SIZE, totalItems = entries.size)

    LaunchedEffect(entries.size) { pager.updateTotalItems(entries.size) }

    EinkScaffold(
        title = feed?.title ?: "Браузер каталога",
        subtitle = feed?.breadcrumbs?.joinToString(separator = " / ") ?: "Фид ещё не загружен",
        modifier = modifier,
        onBack = onBack,
        onKeyAction = { action ->
            when (action) {
                EinkAction.PREVIOUS_PAGE -> pager.previous()
                EinkAction.NEXT_PAGE -> pager.next()
                EinkAction.SEARCH -> viewModel.onSearchInFeed()
                EinkAction.BACK -> onBack()
                else -> Unit
            }
        },
        footer = { StatusFooter(label = pager.statusLabel("записей")) },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.gutter, vertical = dimens.gutterSmall),
            horizontalArrangement = Arrangement.spacedBy(dimens.gutterSmall),
        ) {
            EinkTextField(
                value = state.feedQuery,
                onValueChange = viewModel::onFeedQueryChange,
                placeholder = "Поиск по каталогу",
                modifier = Modifier.weight(1f),
            )
            EinkButton(text = "Искать", onClick = viewModel::onSearchInFeed)
        }

        Text(
            text = "Найдено записей: ${entries.size}",
            style = MaterialTheme.typography.labelMedium,
            color = colors.foreground,
            modifier = Modifier.padding(horizontal = dimens.gutter),
        )

        TapZoneLayout(
            onPrevious = pager::previous,
            onNext = pager::next,
            modifier = Modifier.weight(1f),
        ) {
            if (entries.isEmpty()) {
                EinkEmptyState(
                    title = "Раздел в разработке — Спринт 8",
                    message = "Здесь появятся записи фида: хлебные крошки, обложки, форматы и размер, " +
                        "действия «Скачать» и «Скачать в другую папку…». Пагинация — страницами фида.",
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                EntryGrid(
                    entries = entries.drop(pager.offset).take(pager.pageSize),
                    columns = dimens.gridColumns,
                    onDownload = { entry -> viewModel.onDownloadEntry(entry.id) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** Сетка записей фида: обложка, название, авторы, форматы. */
@Composable
private fun EntryGrid(
    entries: List<FeedEntryUi>,
    columns: Int,
    onDownload: (FeedEntryUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier.fillMaxSize(),
        userScrollEnabled = false,
    ) {
        gridItems(items = entries, key = { entry -> entry.id }) { entry ->
            EntryCard(entry = entry, onClick = { onDownload(entry) })
        }
    }
}

/** Карточка записи фида: тап по карточке и кнопка «Скачать» делают одно и то же. */
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
        BookCover(
            coverPath = entry.coverPath,
            title = entry.title,
            modifier = Modifier.fillMaxWidth(),
            contentDescription = "Обложка: ${entry.title}, ${entry.authors}",
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
        EinkButton(text = "Скачать", onClick = onClick)
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
    val pager = rememberEinkPagerState(pageSize = CATALOGS_PAGE_SIZE, totalItems = state.downloads.size)

    LaunchedEffect(state.downloads.size) { pager.updateTotalItems(state.downloads.size) }

    EinkScaffold(
        title = "Загрузки",
        subtitle = "Файлы качаются во временный .part и переименовываются атомарно",
        modifier = modifier,
        onBack = onBack,
        onKeyAction = { action ->
            when (action) {
                EinkAction.PREVIOUS_PAGE -> pager.previous()
                EinkAction.NEXT_PAGE -> pager.next()
                EinkAction.BACK -> onBack()
                else -> Unit
            }
        },
        footer = { StatusFooter(label = pager.statusLabel("загрузок")) },
    ) {
        TapZoneLayout(
            onPrevious = pager::previous,
            onNext = pager::next,
            modifier = Modifier.weight(1f),
        ) {
            if (state.downloads.isEmpty()) {
                EinkEmptyState(
                    title = "Раздел в разработке — Спринт 8",
                    message = "Здесь появится очередь загрузок: прогресс, отмена, повтор и переход " +
                        "к уже скачанной книге.",
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                val visible = state.downloads.drop(pager.offset).take(pager.pageSize)
                LazyColumn(modifier = Modifier.fillMaxSize(), userScrollEnabled = false) {
                    listItems(items = visible, key = { download -> download.id }) { download ->
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
            subtitle = download.stateLabel,
            trailing = "${download.progressPercent}%",
            onClick = openDownloadedBook,
        )
        if (download.isActive) {
            EinkProgress(
                caption = "Скачивание",
                progress = download.progressPercent / PERCENT_MAX,
                detail = "Скачано ${download.progressPercent}%",
            )
            EinkButton(text = "Отменить", onClick = onCancel)
        }
        val error = download.error
        if (error != null) {
            EinkErrorBanner(message = error, actionLabel = "Повторить", onAction = onCancel)
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

/** Подпись состояния каталога в списке. */
private fun catalogTrailing(catalog: CatalogUi): String = when {
    !catalog.isAvailable -> "недоступен"
    catalog.requiresAuth -> "нужен вход"
    catalog.lastOpenedLabel != null -> catalog.lastOpenedLabel
    else -> "готов"
}

/** Подпись записи фида: форматы, размер, язык. */
private fun entryMeta(entry: FeedEntryUi): String {
    val parts = buildList {
        if (entry.formats.isNotEmpty()) add(entry.formats.joinToString(separator = ", "))
        entry.sizeLabel?.let { add(it) }
        entry.language?.let { add(it) }
    }
    return parts.joinToString(separator = " · ")
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
