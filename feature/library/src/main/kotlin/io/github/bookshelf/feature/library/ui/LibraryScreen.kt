package io.github.bookshelf.feature.library.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.bookshelf.core.designsystem.component.EinkSectionHeader
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bookshelf.core.data.model.Grouping
import io.github.bookshelf.core.data.model.SortOrder
import io.github.bookshelf.core.data.model.badge
import io.github.bookshelf.core.designsystem.component.BookCover
import io.github.bookshelf.core.designsystem.component.EinkButton
import io.github.bookshelf.core.designsystem.component.EinkMenuButton
import io.github.bookshelf.core.designsystem.component.EinkChip
import io.github.bookshelf.core.designsystem.component.EinkEmptyState
import io.github.bookshelf.core.designsystem.component.EinkErrorBanner
import io.github.bookshelf.core.designsystem.component.EinkListRow
import io.github.bookshelf.core.designsystem.component.EinkScaffold
import io.github.bookshelf.core.designsystem.component.clickableNoIndication
import io.github.bookshelf.core.datastore.model.ShelfViewPreset
import io.github.bookshelf.core.designsystem.input.EinkAction
import io.github.bookshelf.core.designsystem.paging.rememberEinkGridPageScroller
import io.github.bookshelf.core.designsystem.paging.rememberEinkListPageScroller
import io.github.bookshelf.core.designsystem.theme.LocalEinkColors
import io.github.bookshelf.core.designsystem.theme.LocalEinkDimens
import io.github.bookshelf.feature.library.BookCardUi
import io.github.bookshelf.feature.library.BrowseLevel
import io.github.bookshelf.feature.library.BrowseMode
import io.github.bookshelf.feature.library.BrowseUi
import io.github.bookshelf.feature.library.CategoryItemUi
import io.github.bookshelf.feature.library.LibraryUiState
import io.github.bookshelf.feature.library.LibraryViewModel
import io.github.bookshelf.feature.library.ShelfEmptyState
import io.github.bookshelf.feature.library.shelfEmptyState
import kotlinx.coroutines.launch

/**
 * Полка — главный экран (docs/eink-ux.md §6.2).
 *
 * Сетка/список показывают все книги сразу и прокручиваются пальцем; аппаратные клавиши
 * листают по экрану. Нижняя строка показывает число книг и текущую сортировку с группировкой.
 */
@Composable
fun LibraryScreen(
    onOpenBook: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenFilters: () -> Unit,
    onOpenCollections: () -> Unit,
    onOpenCatalogs: () -> Unit,
    onOpenScan: () -> Unit,
    onOpenSettings: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dimens = LocalEinkDimens.current
    // Состояния прокрутки живут на экране: аппаратные клавиши и сетка обязаны смотреть на одно
    // и то же состояние, иначе листание уходит в никуда (см. rememberPageScroller).
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    // Корень обзора и уровень жанра — это списки категорий, а не сетка книг; от этого зависит,
    // что листать. Уровень жанра устроен так же: сверху авторы жанра, книг здесь нет.
    val rootCategories = state.browse.showsCategories &&
        (state.browse.level == BrowseLevel.Root || state.browse.level is BrowseLevel.Genre)
    val scrollByPage = rememberPageScroller(gridState, listState) { rootCategories }
    // Флаг lives только на время экрана: выбор сортировки не состояние ViewModel, и хранить
    // его там незачем — как и отменять его нечем.
    var isPickingSort by remember { mutableStateOf(false) }

    EinkScaffold(
        title = "Библиотека",
        modifier = modifier,
        // В шапке — только частые команды. Кнопки в шапке не переносятся на вторую строку:
        // каждый ряд шапки отнимает у сетки строку книг (docs/eink-ux.md §6.2), остальное — в меню.
        actions = {
            EinkButton(text = "Фильтры (${state.activeFilters.activeCount})", onClick = onOpenFilters)
            EinkButton(text = "Поиск", onClick = onOpenSearch)
        },
        menuTitle = "Библиотека",
        menu = { close ->
            // Каталоги и настройки раньше были недостижимы из кода — из меню в них можно войти.
            EinkMenuButton(text = "Полки") {
                onOpenCollections()
                close()
            }
            EinkMenuButton(text = "Каталоги") {
                onOpenCatalogs()
                close()
            }
            EinkMenuButton(text = "Настройки") {
                onOpenSettings()
                close()
            }
            // Виды полки (сетка/список/компактно и обзор по авторам/сериям) живут в строке видов
            // на самой полке: пять кнопок рядом с книгами ближе, чем те же пункты в меню.
            //
            // Кнопки «Пересканировать» здесь больше нет: библиотека обновляется сама (при возврате
            // в приложение и раз в 15 минут), а кнопка означала «забудь всё и собери заново» —
            // такой команде место в настройках рядом с папками, а не рядом с книгами (решение
            // Александра, 28.09.2026).
        },
        onKeyAction = { action ->
            when (action) {
                EinkAction.PREVIOUS_PAGE -> scrollByPage(-1)
                EinkAction.NEXT_PAGE -> scrollByPage(1)
                EinkAction.SEARCH -> onOpenSearch()
                // Корень графа: аппаратный BACK выходит из приложения (или сворачивает его).
                EinkAction.BACK -> onExit()
                // MENU открывает меню «Ещё» — этим занимается каркас экрана (EinkScaffold).
                else -> Unit
            }
        },
        footer = { LibraryFooter(state = state, onPickSort = { isPickingSort = true }) },
    ) {
        val error = state.error
        if (error != null) {
            EinkErrorBanner(
                message = error,
                actionLabel = "Повторить",
                onAction = viewModel::onRefresh,
            )
        }

        // Обзор по категориям: уровень и его содержимое заменяют плоский список книг.
        if (state.browse.isActive) {
            BrowseHeader(browse = state.browse, onBack = viewModel::onBrowseBack)
        }
        // Строка видов видна всегда — она же выход из обзора: без неё в корне «Авторов» не было
        // бы ни кнопки «Серии», ни способа вернуться к книгам (docs/eink-ux.md §6.2).
        if (isPickingSort) {
            SortPicker(
                current = state.sort,
                onPick = { candidate ->
                    viewModel.onSetSort(candidate)
                    isPickingSort = false
                },
                onClose = { isPickingSort = false },
            )
        } else if (state.browse.showsViewSwitch) {
            // Строка видов живёт только на корне обзора: зашли в жанр, автора или серию — кнопок
            // нет, назад возвращает шапка (решение Александра 29.09.2026).
            LibraryControls(
                preset = viewPresetOf(state.browse.mode),
                onPick = viewModel::onSetViewPreset,
            )
        }

        when {
            // Корень обзора — категории: авторы или серии с числом книг.
            rootCategories -> CategoryList(
                categories = state.browse.categories,
                listState = listState,
                onOpen = viewModel::onOpenCategory,
            )

            // Уровень автора и уровень набора — один и тот же совмещённый экран: серии
            // категориями, ниже книги вне серий. Разница только в том, что фильтр набора —
            // точный состав авторов, а не «все книги человека».
            state.browse.level is BrowseLevel.Author || state.browse.level is BrowseLevel.AuthorSet ->
                AuthorBrowseContent(
                    categories = state.browse.categories,
                    books = state.books,
                    gridState = gridState,
                    onOpenCategory = viewModel::onOpenCategory,
                    onOpenBook = onOpenBook,
                )

            else -> LibraryContent(
                state = state,
                columns = GridCells.Adaptive(minSize = dimens.coverMinWidth),
                gridState = gridState,
                listState = listState,
                onOpenBook = onOpenBook,
                onOpenScan = onOpenScan,
                onClearFilters = viewModel::onClearFilters,
            )
        }
    }
}

/** Ключи строк-заголовков в ленивом контейнере уровня автора. */
private const val CATEGORIES_HEADER_KEY = "browse:series"
private const val WITHOUT_SERIES_HEADER_KEY = "browse:without-series"

/** Подпись секции с книгами автора, не входящими ни в одну серию. */
private const val WITHOUT_SERIES_TITLE = "Вне серий"

/** Вид полки: книги плитками, обзор по авторам и обзор по сериям — одним рядом кнопок. */
@Composable
private fun LibraryControls(
    preset: ShelfViewPreset,
    onPick: (ShelfViewPreset) -> Unit,
) {
    val dimens = LocalEinkDimens.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.gutter, vertical = dimens.gutterSmall),
        verticalAlignment = Alignment.CenterVertically,
        // Кнопки — по размеру подписи и прижаты влево, как строки полки: растянутые на всю
        // ширину прямоугольники на e-ink выглядят пустыми рамками.
        horizontalArrangement = Arrangement.spacedBy(dimens.gutterSmall),
    ) {
        ShelfViewPreset.entries.forEach { candidate ->
            EinkChip(
                label = presetLabel(candidate),
                selected = candidate == preset,
                onClick = { onPick(candidate) },
            )
        }
    }
}

/** Вид полки, соответствующий текущему состоянию: обзор важнее показа книг плитками. */
private fun viewPresetOf(browseMode: BrowseMode): ShelfViewPreset = when (browseMode) {
    BrowseMode.AUTHORS -> ShelfViewPreset.AUTHORS
    BrowseMode.SERIES -> ShelfViewPreset.SERIES
    BrowseMode.GENRES -> ShelfViewPreset.GENRES
    BrowseMode.OFF -> ShelfViewPreset.GRID
}

/** Подпись вида полки — та же в настройках «вид по умолчанию». */
private fun presetLabel(preset: ShelfViewPreset): String = when (preset) {
    ShelfViewPreset.GRID -> "Сетка"
    ShelfViewPreset.AUTHORS -> "Авторы"
    ShelfViewPreset.SERIES -> "Серии"
    ShelfViewPreset.GENRES -> "Жанры"
}

/**
 * Уровень автора: серии категориями сверху и книги вне серий ниже — как на главном экране.
 *
 * Один ленивый контейнер, а не два: два вертикальных списка на экране прокручивались бы
 * независимо друг от друга, и нижний оказался бы недостижим пальцем. Книги — теми же плитками
 * с обложками, что и на самой полке.
 */
@Composable
private fun AuthorBrowseContent(
    categories: List<CategoryItemUi>,
    books: List<BookCardUi>,
    gridState: LazyGridState,
    onOpenCategory: (CategoryItemUi) -> Unit,
    onOpenBook: (String) -> Unit,
) {
    val dimens = LocalEinkDimens.current

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = dimens.coverMinWidth),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
    ) {
        if (categories.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = CATEGORIES_HEADER_KEY) {
                EinkSectionHeader(title = "Серии", count = categories.size)
            }
        }
        categories.forEach { category ->
            item(span = { GridItemSpan(maxLineSpan) }, key = "category:${category.id}") {
                CategoryRow(category = category, onClick = onOpenCategory)
            }
        }
        if (books.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = WITHOUT_SERIES_HEADER_KEY) {
                EinkSectionHeader(title = WITHOUT_SERIES_TITLE, count = books.size)
            }
        }
        gridItems(items = books, key = { book -> book.id }) { book ->
            BookGridItem(book = book, onClick = { onOpenBook(book.id) })
        }
    }
}

/** Строка категории: подпись, число книг, вся строка открывает категорию. */
@Composable
private fun CategoryRow(
    category: CategoryItemUi,
    onClick: (CategoryItemUi) -> Unit,
) {
    EinkListRow(
        title = category.label,
        trailing = category.count.toString(),
        onClick = { onClick(category) },
    )
}

/**
 * Строка уровня обзора: назад и подпись текущего уровня.
 *
 * Отдельная кнопка «Назад» нужна потому, что у корневого экрана нет системной шапки с возвратом:
 * аппаратный BACK отсюда выходит из приложения.
 */
@Composable
private fun BrowseHeader(
    browse: BrowseUi,
    onBack: () -> Unit,
) {
    val colors = LocalEinkColors.current
    val dimens = LocalEinkDimens.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.gutter, vertical = dimens.gutterSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.gutterSmall),
    ) {
        if (browse.canGoBack) {
            EinkButton(text = "← Назад", onClick = onBack)
        }
        Text(
            text = browse.title.orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            color = colors.foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Категории обзора: строка списка с числом книг; вся строка открывает категорию. */
@Composable
private fun CategoryList(
    categories: List<CategoryItemUi>,
    listState: LazyListState,
    onOpen: (CategoryItemUi) -> Unit,
) {
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        listItems(items = categories, key = { category -> category.id }) { category ->
            EinkListRow(
                title = category.label,
                trailing = category.count.toString(),
                onClick = { onOpen(category) },
            )
        }
    }
}

/**
 * Листание по аппаратным клавишам: прокрутка на высоту экрана в сторону `+1`/`-1`.
 *
 * Пальцем список листается сам — `LazyVerticalGrid` прокручивается по умолчанию, и отключать
 * это нельзя: без прокрутки всё, что не влезло на экран, становится недостижимым.
 *
 * Состояния прокрутки принадлежат **экрану** и передаются сюда, а не создаются внутри: раньше
 * функция держала своё `LazyGridState`, сетка создавала своё, и клавиши Page Up/Down крутили
 * состояние, которого нет на экране — на устройстве 28.09.2026 листание не работало вовсе.
 * Какой контейнер сейчас показан, знает экран (обзор по категориям — список, книги — сетка).
 */
@Composable
private fun rememberPageScroller(
    gridState: LazyGridState,
    listState: LazyListState,
    showsCategories: () -> Boolean,
): (Int) -> Unit {
    // Прокрутка каждой разновидности контейнера — из дизайн-системы, здесь только выбор:
    // какой контейнер сейчас на экране, знает сам экран (обзор — список, книги — сетка).
    val scrollList = rememberEinkListPageScroller(listState)
    val scrollGrid = rememberEinkGridPageScroller(gridState)
    val currentShowsCategories by rememberUpdatedState(showsCategories)
    return { direction ->
        if (currentShowsCategories()) scrollList(direction) else scrollGrid(direction)
    }
}

/** Содержимое полки: пустое состояние или сами книги. */
@Composable
private fun ColumnScope.LibraryContent(
    state: LibraryUiState,
    columns: GridCells,
    gridState: LazyGridState,
    listState: LazyListState,
    onOpenBook: (String) -> Unit,
    onOpenScan: () -> Unit,
    onClearFilters: () -> Unit,
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
    ) {
        when (shelfEmptyState(state)) {
            // Пустая полка бывает по двум разным причинам, и подсказки у них разные: «книг нет
            // вовсе — отсканируйте папку» и «фильтр ничего не нашёл — снимите фильтр». Раньше
            // в обоих случаях предлагалось сканирование, и при активном фильтре это сбивало
            // с толку: книги в библиотеке есть, их отсекает отбор.
            ShelfEmptyState.FILTERED_OUT -> EinkEmptyState(
                title = "Ничего не найдено",
                message = "Книги в библиотеке есть, но ни одна не подходит под выбранные фильтры " +
                    "(активно фильтров: ${state.activeFilters.activeCount}). " +
                    "Сбросьте фильтры или вернитесь на экран «Фильтры» и снимите часть условий.",
                actionLabel = "Сбросить фильтры",
                onAction = onClearFilters,
                modifier = Modifier.fillMaxSize(),
            )

            ShelfEmptyState.NO_BOOKS -> EinkEmptyState(
                title = "Пока нет книг",
                message = "Нажмите «Сканировать», чтобы найти книги в выбранной папке. " +
                    "Если папка ещё не добавлена — вернитесь в онбординг и выберите её.",
                actionLabel = "Сканировать",
                onAction = onOpenScan,
                modifier = Modifier.fillMaxSize(),
            )

            null -> BookPage(
                state = state,
                columns = columns,
                gridState = gridState,
                onOpenBook = onOpenBook,
            )
        }
    }
}

/**
 * Выбор сортировки: одна строка с текущим значением, по нажатию — список вариантов.
 *
 * Восемь чипок подряд выглядели мусором и занимали три экрана высоты, вытесняя книги. На
 * e-ink правильнее один компактный переключатель и отдельный экран выбора: видно, что
 * сортировка вообще меняется, и вариант всегда открыт целиком.
 */
@Composable
private fun SortPicker(
    current: SortOrder,
    onPick: (SortOrder) -> Unit,
    onClose: () -> Unit,
) {
    val dimens = LocalEinkDimens.current
    val colors = LocalEinkColors.current

    Column(modifier = Modifier.padding(horizontal = dimens.gutter)) {
        EinkSectionHeader(title = "Сортировка")
        SortOrder.entries.forEach { candidate ->
            EinkListRow(
                title = sortLabel(candidate),
                subtitle = sortHint(candidate),
                trailing = if (candidate == current) "выбрано" else null,
                onClick = { onPick(candidate) },
            )
        }
        EinkButton(
            text = "Отмена",
            onClick = onClose,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = dimens.gutter),
        )
        Text(
            text = "Порядок применяется сразу ко всей полке.",
            style = MaterialTheme.typography.labelMedium,
            color = colors.foreground,
            modifier = Modifier.padding(top = dimens.gutterSmall),
        )
    }
}

/**
 * Книги полки плитками с обложками.
 *
 * Прокрутка включена: список один и длинный, а постраничная нарезка с выключенной прокруткой
 * делала всё, что не влезло на «страницу», недостижимым.
 */
@Composable
private fun BookPage(
    state: LibraryUiState,
    columns: GridCells,
    gridState: LazyGridState,
    onOpenBook: (String) -> Unit,
) {
    LazyVerticalGrid(
        columns = columns,
        state = gridState,
        modifier = Modifier.fillMaxSize(),
    ) {
        gridItems(items = state.books, key = { book -> book.id }) { book ->
            BookGridItem(book = book, onClick = { onOpenBook(book.id) })
        }
    }
}

/**
 * Элемент сетки: обложка с бейджами формата и статуса, название, автор.
 *
 * Статус чтения переехал на саму обложку (полосой внизу), формат — в её правый верхний угол:
 * отдельная строка под плиткой стоила высоты экрана, а на e-ink это цена следующего ряда книг.
 */
@Composable
private fun BookGridItem(
    book: BookCardUi,
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
            coverPath = book.coverPath,
            title = book.title,
            modifier = Modifier.fillMaxWidth(),
            contentDescription = "Обложка: ${book.title}, ${book.authors}",
            formatBadge = book.extension.uppercase().takeIf { extension -> extension.isNotEmpty() },
            statusBadge = book.readingStatus.badge(book.progressPercent),
        )
        Text(
            text = book.title,
            // Подписи под плиткой на шаг меньше текста интерфейса: на e-ink они не должны
            // спорить с обложкой, а на экран должно влезать больше книг (Александр, 28.09.2026).
            style = MaterialTheme.typography.bodySmall,
            color = colors.foreground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = book.authors,
            style = MaterialTheme.typography.labelSmall,
            color = colors.foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Нижняя строка полки: сколько всего книг и как они упорядочены. */
@Composable
private fun LibraryFooter(
    state: LibraryUiState,
    onPickSort: () -> Unit,
) {
    val colors = LocalEinkColors.current
    val dimens = LocalEinkDimens.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.gutter, vertical = dimens.gutterSmall),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = bookCountLabel(state.total),
            style = MaterialTheme.typography.labelMedium,
            color = colors.foreground,
        )
        // Сортировка переехала в подвал и открывается касанием: в строке видов её место заняли
        // пять кнопок обзора, а подвал раньше только показывал значение (docs/eink-ux.md §6.2).
        Text(
            // Внутри одной серии порядок не выбирается: книги идут по выпуску (номер в серии,
            // затем год издания), поэтому подпись «Сортировка: Автор» здесь была бы неправдой.
            text = if (state.seriesOrderByRelease) {
                "Порядок серии: по выпуску"
            } else {
                "Сортировка: ${sortLabel(state.sort)} · ${groupingLabel(state.grouping)}"
            },
            style = MaterialTheme.typography.labelMedium,
            color = colors.foreground,
            modifier = Modifier.clickableNoIndication(onClick = onPickSort),
        )
    }
}

/** Подпись «книг» в нижней строке с правильным окончанием. */
private fun bookCountLabel(total: Int): String {
    val remainder = total % TEN
    val word = when {
        remainder == ONE -> "книга"
        remainder in FEW_FROM..FEW_TO -> "книги"
        else -> "книг"
    }
    return "$total $word"
}

private const val TEN = 10
private const val ONE = 1
private const val FEW_FROM = 2
private const val FEW_TO = 4

/** Подпись сортировки для нижней строки. */
private fun sortLabel(sort: SortOrder): String = when (sort) {
    SortOrder.TITLE -> "Название"
    SortOrder.AUTHOR -> "Автор"
    SortOrder.SERIES -> "Серия"
    SortOrder.ADDED_AT -> "Дата добавления"
    SortOrder.LAST_OPENED -> "Дата открытия"
    SortOrder.SIZE -> "Размер"
    SortOrder.PROGRESS -> "Прогресс"
    SortOrder.YEAR -> "Год"
}

/** Подпись группировки для нижней строки. */
private fun groupingLabel(grouping: Grouping): String = when (grouping) {
    Grouping.NONE -> "без группировки"
    Grouping.SERIES -> "по сериям"
    Grouping.AUTHOR -> "по авторам"
    Grouping.FOLDER -> "по папкам"
    Grouping.FORMAT -> "по форматам"
}

/** Пояснение к варианту сортировки — одной подписи «Год» мало, чтобы выбрать. */
private fun sortHint(sort: SortOrder): String = when (sort) {
    SortOrder.TITLE -> "По заглавию, без учёта «The» и «A»"
    SortOrder.AUTHOR -> "Сначала автор, потом заглавие"
    SortOrder.SERIES -> "По серии и номеру в ней"
    SortOrder.ADDED_AT -> "Сначала добавленные последними"
    SortOrder.LAST_OPENED -> "Сначала те, что открывали недавно"
    SortOrder.SIZE -> "По размеру файла"
    SortOrder.PROGRESS -> "Сначала недопрочитанные"
    SortOrder.YEAR -> "По году издания"
}
