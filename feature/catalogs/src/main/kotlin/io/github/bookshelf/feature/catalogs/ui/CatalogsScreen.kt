package io.github.bookshelf.feature.catalogs.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import io.github.bookshelf.core.designsystem.paging.rememberEinkPagerState
import io.github.bookshelf.core.designsystem.paging.statusLabel
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
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState

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

/** Список добавленных каталогов. */
@Composable
private fun CatalogsListScreen(
    state: CatalogsUiState,
    viewModel: CatalogsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pager = rememberEinkPagerState(pageSize = CATALOGS_PAGE_SIZE, totalItems = state.catalogs.size)
    val dimens = LocalEinkDimens.current

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
        footer = {
            Column(modifier = Modifier.fillMaxWidth()) {
                StatusFooter(label = pager.statusLabel("каталогов"))
                EinkButton(
                    text = "Добавить каталог",
                    onClick = viewModel::onOpenAddForm,
                    primary = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    ) {
        val error = state.error
        if (error != null) {
            EinkErrorBanner(message = error, actionLabel = "Обновить", onAction = viewModel::onBackToList)
        }

        Box(
                modifier = Modifier.weight(1f),
            ) {
            if (state.catalogs.isEmpty()) {
                EinkEmptyState(
                    title = "Каталогов пока нет",
                    message = "Добавьте каталог по адресу — например, https://m.flibusta.is/opds " +
                        "без логина. Каталогов может быть сколько угодно, и каждый открывается отдельно. " +
                        "Нажмите «Добавить каталог» внизу экрана.",
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                val visible = state.catalogs.drop(pager.offset).take(pager.pageSize)
                LazyColumn(modifier = Modifier.fillMaxSize(), userScrollEnabled = false) {
                    listItems(items = visible, key = { catalog -> catalog.id }) { catalog ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            EinkListRow(
                                title = catalog.title,
                                subtitle = catalog.url,
                                trailing = catalogTrailing(catalog),
                                // «⋯» — единственный вход к правке и удалению: раньше каталог
                                // можно было только добавить, а убрать или переименовать — нет.
                                trailingContent = {
                                    EinkButton(
                                        text = "⋯",
                                        onClick = { viewModel.catalogActions.onToggleActions(catalog.id) },
                                    )
                                },
                                onClick = { viewModel.onOpenCatalog(catalog.id) },
                            )
                            if (state.actionsForCatalogId == catalog.id) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = dimens.gutter),
                                    horizontalArrangement = Arrangement.spacedBy(dimens.gutterSmall),
                                ) {
                                    EinkButton(
                                        text = "Изменить",
                                        onClick = { viewModel.catalogActions.onAskEdit(catalog.id) },
                                        modifier = Modifier.weight(1f),
                                    )
                                    EinkButton(
                                        text = "Удалить",
                                        onClick = { viewModel.catalogActions.onAskDelete(catalog.id) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                            if (state.confirmingDeleteCatalogId == catalog.id) {
                                EinkConfirmRow(
                                    question = "Удалить каталог «${catalog.title}»? Книги, скачанные " +
                                        "из него, останутся в библиотеке.",
                                    confirmLabel = "Удалить",
                                    onConfirm = viewModel.catalogActions::onConfirmDelete,
                                    onCancel = viewModel.catalogActions::onCancelDelete,
                                    modifier = Modifier.padding(horizontal = dimens.gutter),
                                )
                            }
                        }
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

    val isEditing = state.editingCatalogId != null
    EinkScaffold(
        title = if (isEditing) "Изменить каталог" else "Новый каталог",
        subtitle = if (isEditing) {
            "Проверим фид по новому адресу перед сохранением"
        } else {
            "Проверим фид перед добавлением"
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.gutter, vertical = dimens.gutterSmall),
                horizontalArrangement = Arrangement.spacedBy(dimens.gutterSmall),
            ) {
                EinkButton(text = "Отмена", onClick = onBack, modifier = Modifier.weight(1f))
                EinkButton(
                    text = if (isEditing) "Сохранить" else "Проверить и добавить",
                    onClick = if (isEditing) viewModel.catalogActions::onSaveChanges else viewModel::onCheckAndAdd,
                    primary = true,
                    modifier = Modifier.weight(1f),
                )
            }
        },
    ) {
        // Ошибка валидации раньше не отображалась вовсе: нажатие выглядело как «ничего не
        // происходит». В footer её ставить нельзя — там Row с кнопками, и баннер их вытеснял.
        state.error?.let { message ->
            EinkErrorBanner(message = message, actionLabel = null, onAction = {})
        }

        if (state.isLoading) {
            Text(
                text = "Проверяем каталог…",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = dimens.gutterSmall),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(dimens.gutter),
            verticalArrangement = Arrangement.spacedBy(dimens.gutter),
        ) {
            EinkTextField(
                value = form.title,
                onValueChange = { viewModel.onFormChange(form.copy(title = it)) },
                label = "Название",
                placeholder = "Моя библиотека",
            )
            EinkTextField(
                value = form.url,
                onValueChange = { viewModel.onFormChange(form.copy(url = it)) },
                label = "URL каталога",
                placeholder = "https://example.org/opds",
            )
            EinkTextField(
                value = form.username,
                onValueChange = { viewModel.onFormChange(form.copy(username = it)) },
                label = "Логин (если требуется)",
            )
            // Пароль не логируется и не экспортируется: в БД попадает только alias.
            EinkTextField(
                value = form.password,
                onValueChange = { viewModel.onFormChange(form.copy(password = it)) },
                label = if (isEditing) "Новый пароль (пусто — оставить прежний)" else "Пароль (если требуется)",
                visualTransformation = PasswordVisualTransformation(),
            )
            EinkCheckbox(
                checked = form.openImmediately,
                onCheckedChange = { viewModel.onFormChange(form.copy(openImmediately = it)) },
                label = "Открывать этот каталог сразу",
            )
        }
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
    val pager = rememberEinkPagerState(pageSize = CATALOGS_PAGE_SIZE, totalItems = entries.size)

    LaunchedEffect(entries.size) { pager.updateTotalItems(entries.size) }

    EinkScaffold(
        title = feed?.title ?: "Браузер каталога",
        subtitle = feed?.breadcrumbs?.joinToString(separator = " / ") ?: "Фид ещё не загружен",
        modifier = modifier,
        onBack = onBack,
        // Кнопка правого края: из любого уровня каталога возвращает к списку каталогов одним
        // касанием. Левая «← Назад» идёт на уровень выше, и из глубокого раздела до списка
        // приходилось щёлкать её несколько раз.
        actions = {
            // Дорога к очереди прямо из фида: раньше после «Скачать» карточка молча закрывалась,
            // а «Загрузки» были доступны только из списка каталогов — в разделах фида их не было
            // видно вовсе (жалоба Александра, 28.09.2026).
            EinkButton(
                text = downloadsButtonLabel(state.downloads),
                onClick = viewModel::onOpenDownloads,
            )
            EinkButton(text = "Каталоги", onClick = viewModel::onBackToList)
        },
        onKeyAction = { action ->
            when (action) {
                EinkAction.PREVIOUS_PAGE -> pager.previous()
                EinkAction.NEXT_PAGE -> pager.next()
                EinkAction.SEARCH -> viewModel.onSearchInFeed()
                EinkAction.MENU -> viewModel.onOpenDownloads()
                EinkAction.BACK -> onBack()
                else -> Unit
            }
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

        // Ошибка загрузки фида раньше не показывалась вовсе: нажатие на раздел, который не
        // открылся, выглядело как «ничего не произошло». Баннер стоит над списком, а не в подвале,
        // потому что в подвале уже подпись о записях.
        state.error?.let { message ->
            EinkErrorBanner(
                message = message,
                actionLabel = "Повторить",
                onAction = viewModel::onReloadFeed,
            )
        }

        // Индикация загрузки: переход по разделам ждёт сеть, и без подписи экран выглядел
        // зависшим — пользователь не понимал, идёт работа или приложение задумалось.
        if (state.isLoading) {
            LoadingRow(label = "Загружаем раздел каталога…")
        }

        Box(
                modifier = Modifier.weight(1f),
            ) {
            if (entries.isEmpty()) {
                when {
                    // Первый фид: пустая подсказка «фид не загружен» противоречила бы индикатору.
                    state.isLoading -> LoadingRow(label = "Загружаем фид каталога…")

                    feed == null -> EinkEmptyState(
                        title = "Фид не загружен",
                        message = "Здесь появятся записи фида: хлебные крошки, обложки, форматы и размер. " +
                            "Нажмите на книгу — откроется карточка с описанием и кнопкой «Скачать».",
                        modifier = Modifier.fillMaxSize(),
                    )

                    // Фид загружен, но записей в нём нет: так отвечает поиск, которому нечего
                    // показать. «Фид не загружен» здесь было бы неправдой, а «Назад» — выходом.
                    else -> EinkEmptyState(
                        title = "Ничего не найдено",
                        message = "Каталог ответил пустым списком: по этому запросу записей нет. " +
                            "Измените запрос и нажмите «Искать» или вернитесь назад — «Назад» " +
                            "возвращает к прежнему списку.",
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            } else {
                // Список один и прокручивается целиком; когда пользователь доходит до конца,
                // следующая страница фида догружается сама (rel="next").
                FeedContent(
                    entries = entries,
                    columns = GridCells.Adaptive(minSize = dimens.coverMinWidth),
                    hasNextPage = feed?.hasNextPage == true,
                    isLoadingMore = state.isLoadingMore,
                    onOpen = { entry -> viewModel.onOpenEntry(entry.id, onOpenEntry) },
                    onLoadMore = viewModel::onLoadNextPage,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/**
 * Подпись о записях фида.
 *
 * Точное число показывается только там, где точно известно, что страниц больше нет
 * (`rel="next"` отсутствует): OPDS не отдаёт общее количество записей, и «записей: 20» на фиде
 * из двухсот книг читалось как реальное число, хотя это была всего одна страница.
 */
private fun feedStatusLabel(loaded: Int, hasNext: Boolean, isNavigation: Boolean): String = when {
    loaded == 0 && !hasNext -> "ничего не найдено"
    hasNext -> "загружено $loaded, есть ещё"
    isNavigation -> "разделов: $loaded"
    else -> "книг: $loaded"
}

/**
 * Строка индикации загрузки: подпись, что именно грузится.
 *
 * Без полосы прогресса: у запроса фида нет измеримого прогресса, а полоса «на глаз» или на нуле
 * врала бы о состоянии. На e-ink статичная подпись честнее и не мигает (docs/eink-ux.md §5).
 */
@Composable
private fun LoadingRow(label: String) {
    EinkListRow(
        title = label,
        subtitle = "Идёт запрос к каталогу по сети",
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
private fun FeedContent(
    entries: List<FeedEntryUi>,
    columns: GridCells,
    hasNextPage: Boolean,
    isLoadingMore: Boolean,
    onOpen: (FeedEntryUi) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()

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
                    trailing = "раздел",
                    onClick = { onOpen(section) },
                )
            }
        }
        gridItemsIndexed(items = books, key = { index, entry -> "$index:${entry.id}" }) { _, entry ->
            EntryCard(entry = entry, onClick = { onOpen(entry) })
        }
        if (isLoadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "loading-more") {
                LoadingRow(label = "Загружаем следующую страницу…")
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
        // Состояние загрузки книги — в самой карточке, там же, где пользователь нажал «Скачать».
        // Раньше после постановки в очередь не было ни подписи, ни прогресса: казалось, что
        // ничего не произошло.
        entry.download?.let { download ->
            Text(
                text = download.label,
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
        title = "Загрузки",
        subtitle = "Файлы качаются во временный .part и переименовываются атомарно",
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
                EinkButton(text = "Очистить историю", onClick = viewModel::onToggleClearHistory)
            }
        },
        footer = { StatusFooter(label = downloadCountLabel(state.downloads.size)) },
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (state.isConfirmingClearHistory) {
                EinkConfirmRow(
                    question = "Убрать историю загрузок? Записи о завершённых, сбойных и " +
                        "отменённых загрузках исчезнут. Книги в библиотеке останутся, " +
                        "активные загрузки продолжатся.",
                    confirmLabel = "Убрать",
                    onConfirm = viewModel::onConfirmClearHistory,
                    onCancel = viewModel::onToggleClearHistory,
                )
            }
            if (state.downloads.isEmpty()) {
                EinkEmptyState(
                    title = "Загрузок пока нет",
                    message = "Вы ещё ничего не скачивали. Здесь появится очередь: прогресс, " +
                        "отмена, причина ошибки и переход к уже скачанной книге.",
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
private fun downloadCountLabel(count: Int): String = when (count) {
    0 -> "загрузок нет"
    1 -> "1 загрузка"
    else -> "загрузок: $count"
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

