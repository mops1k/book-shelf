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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bookshelf.core.data.model.Grouping
import io.github.bookshelf.core.data.model.SortOrder
import io.github.bookshelf.core.designsystem.component.BookCover
import io.github.bookshelf.core.designsystem.component.EinkButton
import io.github.bookshelf.core.designsystem.component.EinkMenuButton
import io.github.bookshelf.core.designsystem.component.EinkChip
import io.github.bookshelf.core.designsystem.component.EinkEmptyState
import io.github.bookshelf.core.designsystem.component.EinkErrorBanner
import io.github.bookshelf.core.designsystem.text.resolve
import io.github.bookshelf.core.designsystem.component.EinkListRow
import io.github.bookshelf.core.designsystem.component.EinkScaffold
import io.github.bookshelf.core.designsystem.component.clickableNoIndication
import io.github.bookshelf.core.datastore.model.ShelfViewPreset
import io.github.bookshelf.core.designsystem.input.EinkAction
import io.github.bookshelf.core.designsystem.paging.rememberEinkGridPageScroller
import io.github.bookshelf.core.designsystem.paging.rememberEinkListPageScroller
import io.github.bookshelf.core.designsystem.text.readingStatusBadge
import io.github.bookshelf.core.designsystem.theme.LocalEinkColors
import io.github.bookshelf.core.designsystem.theme.LocalEinkDimens
import io.github.bookshelf.feature.library.R
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
 * Полка — главный экран («E-ink UX» §6.2).
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
    // Корень обзора и уровень жанра — списки категорий, а не сетка книг: от этого зависит, что
    // листать. Уровень жанра устроен так же — сверху авторы жанра, книг здесь нет.
    val rootCategories = state.browse.showsCategories &&
        (state.browse.level == BrowseLevel.Root || state.browse.level is BrowseLevel.Genre)
    val scrollByPage = rememberPageScroller(gridState, listState) { rootCategories }
    LoadMoreOnScroll(
        gridState = gridState,
        loaded = state.books.size,
        total = state.total,
        onLoadMore = viewModel::onLoadMore,
    )
    // Шаг выбора живёт только на время экрана: сам порядок хранит ViewModel, а «на каком мы шаге»
    // — состояние экрана, и хранить его в ViewModel незачем.
    var sortStep by remember { mutableStateOf(SortStep.Hidden) }
    // Новый порядок начинается с первой книги: иначе список остаётся прокрученным на прежнем
    // месте и видно кусок нового порядка — выглядит как «сортировка не применилась».
    ScrollToTopOnOrderChange(gridState, state.sort, state.descending, state.grouping)

    EinkScaffold(
        title = stringResource(R.string.library_title),
        modifier = modifier,
        // В шапке — только частые команды. Кнопки в шапке не переносятся на вторую строку:
        // каждый ряд шапки отнимает у сетки строку книг («E-ink UX» §6.2), остальное — в меню.
        actions = {
            LibraryHeaderActions(
                filtersLabel = stringResource(R.string.library_filters, state.activeFilters.activeCount),
                onOpenFilters = onOpenFilters,
                onOpenSearch = onOpenSearch,
            )
        },
        menuTitle = stringResource(R.string.library_title),
        menu = { close -> LibraryMenu(state, close, onOpenCollections, onOpenCatalogs, onOpenSettings) },
        onKeyAction = { action ->
            libraryKeyAction(
                action = action,
                scrollByPage = scrollByPage,
                onOpenSearch = onOpenSearch,
                onExit = onExit,
            )
        },
        footer = { LibraryFooter(state = state, onPickSort = { sortStep = SortStep.Field }) },
    ) {
        LibraryErrorBanner(state = state, onRetry = viewModel::onRefresh)

        // Обзор по категориям: уровень и его содержимое заменяют плоский список книг.
        if (state.browse.isActive) {
            BrowseHeader(browse = state.browse, onBack = viewModel::onBrowseBack)
        }
        // Строка видов видна всегда — она же выход из обзора: без неё в корне «Авторов» не было
        // бы ни кнопки «Серии», ни способа вернуться к книгам («E-ink UX» §6.2).
        LibraryViewControls(
            state = state,
            sortStep = sortStep,
            onSortStep = { sortStep = it },
            viewModel = viewModel,
        )

        LibraryBrowseBody(
            state = state,
            rootCategories = rootCategories,
            gridState = gridState,
            listState = listState,
            onOpenBook = onOpenBook,
            onOpenScan = onOpenScan,
            viewModel = viewModel,
        )
    }
}

/**
 * Выбор порядка и вида полки: шаг «поле → направление» и строка видов обзора.
 *
 * Вынесено из `LibraryScreen` отдельной функцией: экран перевалил за предел длины detekt, а это
 * цельный кусок — он зависит только от состояния и колбэков ViewModel.
 */
@Composable
private fun LibraryViewControls(
    state: LibraryUiState,
    sortStep: SortStep,
    onSortStep: (SortStep) -> Unit,
    viewModel: LibraryViewModel,
) {
    if (sortStep == SortStep.Field) {
        SortPicker(
            current = state.sort,
            onPick = { candidate ->
                viewModel.onSetSort(candidate)
                // Второй шаг, а не закрытие: направление — часть решения «как смотреть полку»,
                // и без него подпись «Сначала те, что открывали недавно» оставалась обещанием.
                onSortStep(SortStep.Direction)
            },
            onClose = { onSortStep(SortStep.Hidden) },
        )
    } else if (sortStep == SortStep.Direction) {
        SortDirectionPicker(
            descending = state.descending,
            onPick = { descending ->
                viewModel.onSetSortDirection(descending)
                onSortStep(SortStep.Hidden)
            },
            onBack = { onSortStep(SortStep.Field) },
            onClose = { onSortStep(SortStep.Hidden) },
        )
    } else if (state.browse.showsViewSwitch) {
        // Строка видов живёт только на корне обзора: зашли в жанр, автора или серию — кнопок
        // нет, назад возвращает шапка (решение Александра 29.09.2026).
        LibraryControls(
            preset = viewPresetOf(state.browse.mode),
            onPick = viewModel::onSetViewPreset,
        )
    }
}

/** Ключи строк-заголовков в ленивом контейнере уровня автора. */
private const val CATEGORIES_HEADER_KEY = "browse:series"
private const val WITHOUT_SERIES_HEADER_KEY = "browse:without-series"

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
                label = stringResource(presetLabelRes(candidate)),
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

/** Подпись вида полки — та же в настройках «вид по умолчанию». Возвращает ресурс, не текст. */
private fun presetLabelRes(preset: ShelfViewPreset): Int = when (preset) {
    ShelfViewPreset.GRID -> R.string.library_view_grid
    ShelfViewPreset.AUTHORS -> R.string.library_view_authors
    ShelfViewPreset.SERIES -> R.string.library_view_series
    ShelfViewPreset.GENRES -> R.string.library_view_genres
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
                EinkSectionHeader(
                    title = stringResource(R.string.library_view_series),
                    count = categories.size,
                )
            }
        }
        categories.forEach { category ->
            item(span = { GridItemSpan(maxLineSpan) }, key = "category:${category.id}") {
                CategoryRow(category = category, onClick = onOpenCategory)
            }
        }
        if (books.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = WITHOUT_SERIES_HEADER_KEY) {
                EinkSectionHeader(
                    title = stringResource(R.string.library_without_series),
                    count = books.size,
                )
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
        title = category.labelText?.resolve() ?: category.label,
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
            EinkButton(text = stringResource(R.string.library_action_back), onClick = onBack)
        }
        val title = browse.title
        Text(
            // Без раскрытия массива: у подписи уровня либо нет аргумента, либо он один.
            text = title?.let { spec ->
                when {
                    spec.argText != null -> stringResource(spec.titleRes, spec.argText.resolve())
                    spec.arg != null -> stringResource(spec.titleRes, spec.arg)
                    else -> stringResource(spec.titleRes)
                }
            }.orEmpty(),
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
                title = category.labelText?.resolve() ?: category.label,
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
                title = stringResource(R.string.library_empty_filtered_title),
                message = stringResource(
                    R.string.library_empty_filtered_message,
                    state.activeFilters.activeCount,
                ),
                actionLabel = stringResource(R.string.library_empty_filtered_action),
                onAction = onClearFilters,
                modifier = Modifier.fillMaxSize(),
            )

            ShelfEmptyState.NO_BOOKS -> EinkEmptyState(
                title = stringResource(R.string.library_empty_no_books_title),
                message = stringResource(R.string.library_empty_no_books_message),
                actionLabel = stringResource(R.string.library_empty_no_books_action),
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
        EinkSectionHeader(title = stringResource(R.string.library_sort_header))
        SortOrder.entries.forEach { candidate ->
            EinkListRow(
                title = stringResource(sortLabelRes(candidate)),
                subtitle = stringResource(sortHintRes(candidate)),
                trailing = if (candidate == current) {
                    stringResource(R.string.library_sort_selected)
                } else {
                    null
                },
                onClick = { onPick(candidate) },
            )
        }
        EinkButton(
            text = stringResource(R.string.library_action_cancel),
            onClick = onClose,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = dimens.gutter),
        )
        Text(
            text = stringResource(R.string.library_sort_hint),
            style = MaterialTheme.typography.labelMedium,
            color = colors.foreground,
            modifier = Modifier.padding(top = dimens.gutterSmall),
        )
    }
}

/**
 * Второй шаг выбора сортировки: направление.
 *
 * Вынесен отдельным шагом, а не строкой внутри первого: на e-ink список из восьми полей занимает
 * экран целиком, и направление в нём терялось. Отдельный шаг ещё и показывает текущий порядок,
 * поэтому выбор не приходится угадывать.
 */
@Composable
private fun SortDirectionPicker(
    descending: Boolean,
    onPick: (Boolean) -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    val dimens = LocalEinkDimens.current
    val colors = LocalEinkColors.current

    Column(modifier = Modifier.padding(horizontal = dimens.gutter)) {
        EinkSectionHeader(title = stringResource(R.string.library_sort_direction_header))
        listOf(false to R.string.library_sort_ascending, true to R.string.library_sort_descending)
            .forEach { (value, labelRes) ->
                EinkListRow(
                    title = stringResource(labelRes),
                    subtitle = stringResource(
                        if (value) {
                            R.string.library_sort_descending_hint
                        } else {
                            R.string.library_sort_ascending_hint
                        },
                    ),
                    trailing = if (value == descending) {
                        stringResource(R.string.library_sort_selected)
                    } else {
                        null
                    },
                    onClick = { onPick(value) },
                )
            }
        EinkButton(
            text = stringResource(R.string.library_sort_direction_back),
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = dimens.gutter),
        )
        EinkButton(
            text = stringResource(R.string.library_action_cancel),
            onClick = onClose,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = dimens.gutterSmall),
        )
        Text(
            text = stringResource(R.string.library_sort_hint),
            style = MaterialTheme.typography.labelMedium,
            color = colors.foreground,
            modifier = Modifier.padding(top = dimens.gutterSmall),
        )
    }
}

/**
 * Шаг выбора сортировки: поле или направление.
 *
 * Состояние экрана, а не ViewModel: порядок хранит запрос полки, а «на каком шаге остановился
 * человек» к данным отношения не имеет.
 */
private enum class SortStep { Hidden, Field, Direction }

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
            contentDescription = stringResource(
                R.string.library_cover_description,
                book.title,
                book.authors,
            ),
            formatBadge = book.extension.uppercase().takeIf { extension -> extension.isNotEmpty() },
            statusBadge = readingStatusBadge(book.readingStatus.name, book.progressPercent)
                ?.resolve()
                .orEmpty(),
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
            text = pluralStringResource(R.plurals.library_book_count, state.total, state.total),
            style = MaterialTheme.typography.labelMedium,
            color = colors.foreground,
        )
        // Сортировка переехала в подвал и открывается касанием: в строке видов её место заняли
        // пять кнопок обзора, а подвал раньше только показывал значение («E-ink UX» §6.2).
        Text(
            // Внутри одной серии порядок не выбирается: книги идут по выпуску (номер в серии,
            // затем год издания), поэтому подпись «Сортировка: Автор» здесь была бы неправдой.
            text = if (state.seriesOrderByRelease) {
                stringResource(R.string.library_series_order_release)
            } else {
                stringResource(
                    R.string.library_sort_summary,
                    stringResource(sortLabelRes(state.sort)) + " " +
                        stringResource(
                            if (state.descending) {
                                R.string.library_sort_arrow_down
                            } else {
                                R.string.library_sort_arrow_up
                            },
                        ),
                    stringResource(groupingLabelRes(state.grouping)),
                )
            },
            style = MaterialTheme.typography.labelMedium,
            color = colors.foreground,
            modifier = Modifier.clickableNoIndication(onClick = onPickSort),
        )
    }
}

/**
 * Подпись сортировки для нижней строки.
 *
 * Возвращается ресурс, а не готовый текст: сами слова живут в `res/values` (ru) и `values-en`,
 * а выбор нужного варианта остаётся чистой функцией и проверяется без Compose. Счёт книг —
 * через `<plurals>`: у русского три формы, и склеивать их в коде значило бы держать текст вне
 * ресурсов.
 */
private fun sortLabelRes(sort: SortOrder): Int = when (sort) {
    SortOrder.TITLE -> R.string.library_sort_title
    SortOrder.AUTHOR -> R.string.library_sort_author
    SortOrder.SERIES -> R.string.library_sort_series
    SortOrder.ADDED_AT -> R.string.library_sort_added
    SortOrder.LAST_OPENED -> R.string.library_sort_last_opened
    SortOrder.SIZE -> R.string.library_sort_size
    SortOrder.PROGRESS -> R.string.library_sort_progress
    SortOrder.YEAR -> R.string.library_sort_year
}

/** Подпись группировки для нижней строки. Возвращает ресурс, не текст. */
private fun groupingLabelRes(grouping: Grouping): Int = when (grouping) {
    Grouping.NONE -> R.string.library_grouping_none
    Grouping.SERIES -> R.string.library_grouping_series
    Grouping.AUTHOR -> R.string.library_grouping_author
    Grouping.FOLDER -> R.string.library_grouping_folder
    Grouping.FORMAT -> R.string.library_grouping_format
}

/** Пояснение к варианту сортировки. Возвращает ресурс, не текст. */
private fun sortHintRes(sort: SortOrder): Int = when (sort) {
    SortOrder.TITLE -> R.string.library_sort_hint_title
    SortOrder.AUTHOR -> R.string.library_sort_hint_author
    SortOrder.SERIES -> R.string.library_sort_hint_series
    SortOrder.ADDED_AT -> R.string.library_sort_hint_added
    SortOrder.LAST_OPENED -> R.string.library_sort_hint_last_opened
    SortOrder.SIZE -> R.string.library_sort_hint_size
    SortOrder.PROGRESS -> R.string.library_sort_hint_progress
    SortOrder.YEAR -> R.string.library_sort_hint_year
}


/**
 * Кнопки шапки полки.
 *
 * Вынесены из `LibraryScreen`: функция перевалила за предел длины detekt, а шапка — цельный кусок,
 * который не зависит от состояния прокрутки и обзора.
 */
@Composable
private fun LibraryHeaderActions(
    filtersLabel: String,
    onOpenFilters: () -> Unit,
    onOpenSearch: () -> Unit,
) {
    // Кнопки в шапке не переносятся на вторую строку: каждый ряд шапки отнимает у сетки строку
    // книг («E-ink UX» §6.2), остальное — в меню.
    EinkButton(text = filtersLabel, onClick = onOpenFilters)
    EinkButton(text = stringResource(R.string.library_search), onClick = onOpenSearch)
}

/**
 * Меню «Ещё» на полке.
 *
 * Каталоги и настройки раньше были недостижимы из кода — из меню в них можно войти. Виды полки
 * (сетка/список/компактно и обзор по авторам/сериям) живут в строке видов на самой полке: пять
 * кнопок рядом с книгами ближе, чем те же пункты в меню.
 *
 * Кнопки «Пересканировать» здесь больше нет: библиотека обновляется сама (при возврате в приложение
 * и раз в 15 минут), а кнопка означала «забудь всё и собери заново» — такой команде место
 * в настройках рядом с папками, а не рядом с книгами (решение Александра, 28.09.2026).
 */
@Composable
private fun LibraryMenu(
    state: LibraryUiState,
    close: () -> Unit,
    onOpenCollections: () -> Unit,
    onOpenCatalogs: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    EinkMenuButton(text = stringResource(R.string.library_menu_collections)) {
        onOpenCollections()
        close()
    }
    // В сборке без OPDS пункта нет: маршрут каталогов в ней не зарегистрирован (ADR-006).
    if (state.catalogsEnabled) {
        EinkMenuButton(text = stringResource(R.string.library_menu_catalogs)) {
            onOpenCatalogs()
            close()
        }
    }
    EinkMenuButton(text = stringResource(R.string.library_menu_settings)) {
        onOpenSettings()
        close()
    }
}


/** Баннер ошибки на полке: переводимый текст и кнопка «Повторить». */
@Composable
private fun LibraryErrorBanner(state: LibraryUiState, onRetry: () -> Unit) {
    val error = state.error ?: return
    EinkErrorBanner(
        message = error.resolve(),
        actionLabel = stringResource(R.string.library_action_retry),
        onAction = onRetry,
    )
}


/**
 * Тело полки: категории обзора, уровень автора или обычный список книг.
 *
 * Вынесено из `LibraryScreen` отдельной функцией: экран перевалил за предел длины detekt, а выбор
 * содержимого — цельный кусок, зависящий от уровня обзора и состояния прокрутки.
 */
@Composable
private fun ColumnScope.LibraryBrowseBody(
    state: LibraryUiState,
    rootCategories: Boolean,
    gridState: LazyGridState,
    listState: LazyListState,
    onOpenBook: (String) -> Unit,
    onOpenScan: () -> Unit,
    viewModel: LibraryViewModel,
) {
    val dimens = LocalEinkDimens.current
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


/**
 * Что делает аппаратная клавиша на полке.
 *
 * Вынесено из `LibraryScreen` отдельной функцией: та перевалила за предел длины detekt. Корень
 * графа: аппаратный BACK выходит из приложения (или сворачивает его), а MENU открывает меню «Ещё» —
 * этим занимается каркас экрана (`EinkScaffold`), поэтому здесь `else`.
 */
private fun libraryKeyAction(
    action: EinkAction,
    scrollByPage: (Int) -> Unit,
    onOpenSearch: () -> Unit,
    onExit: () -> Unit,
) {
    when (action) {
        EinkAction.PREVIOUS_PAGE -> scrollByPage(-1)
        EinkAction.NEXT_PAGE -> scrollByPage(1)
        EinkAction.SEARCH -> onOpenSearch()
        EinkAction.BACK -> onExit()
        else -> Unit
    }
}

