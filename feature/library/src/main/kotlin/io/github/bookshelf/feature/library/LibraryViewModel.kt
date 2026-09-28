package io.github.bookshelf.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.errorOrNull
import io.github.bookshelf.core.common.error.isFailure
import io.github.bookshelf.core.data.model.AuthorSeries
import io.github.bookshelf.core.data.model.Book
import io.github.bookshelf.core.data.model.BookQuery
import io.github.bookshelf.core.data.model.FacetItem
import io.github.bookshelf.core.data.model.Grouping
import io.github.bookshelf.core.data.model.LibraryQuery
import io.github.bookshelf.core.data.model.SortOrder
import io.github.bookshelf.core.data.repository.BookRepository
import io.github.bookshelf.core.data.repository.FilterRepository
import io.github.bookshelf.core.data.repository.FilterSelection
import io.github.bookshelf.core.datastore.SettingsRepository
import io.github.bookshelf.core.datastore.model.ShelfViewPreset
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel полки (docs/eink-ux.md §6.2).
 *
 * Страница наблюдается через [BookRepository.observePage] в [viewModelScope]. Смена сортировки,
 * группировки или номера страницы отменяет предыдущую подписку и создаёт новый запрос, поэтому
 * устаревшие результаты не могут перезаписать текущую страницу.
 *
 * Полка листается обычной прокруткой, а не постраничными «зонами касания»: разбивка на страницы
 * при выключенном скролле выглядела экономной, но палец по книгам не вёл, а неочевидные зоны
 * листания найти было нельзя. Поэтому запрос всегда один, на [SINGLE_PAGE_SIZE] книг.
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    /** Настройки нужны для вида полки по умолчанию (docs/eink-ux.md §6.8). */
    private val settingsRepository: SettingsRepository,
    private val filterSelection: FilterSelection,
    private val filterRepository: FilterRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())

    private var query: LibraryQuery = LibraryQuery(
        sort = _uiState.value.sort,
        grouping = _uiState.value.grouping,
        page = 0,
        pageSize = SINGLE_PAGE_SIZE,
    )

    private var pageJob: Job? = null

    /** Подписка на категории обзора: своя на каждый уровень, старую отменяем. */
    private var categoryJob: Job? = null

    /** Пользователь уже выбирал вид на самой полке: первое значение настройки его не перебивает. */
    private var viewChosenOnShelf = false

    init {
        observePage(query)
        // Вид полки по умолчанию задаётся на экране настроек. Подписка, а не разовое чтение:
        // пользователь меняет настройку и возвращается на полку без перезапуска приложения.
        // Первое значение — это значение по умолчанию: если пользователь успел выбрать вид сам
        // (или тест вызвал выбор до первой эмиссии), оно не должно откатывать этот выбор.
        viewModelScope.launch {
            var first = true
            settingsRepository.settings
                .map { settings -> settings.library.viewPreset }
                .distinctUntilChanged()
                .collect { preset ->
                    val isDefault = first
                    first = false
                    if (isDefault && viewChosenOnShelf) return@collect
                    applyViewPreset(preset)
                }
        }
        // Полка читает тот же выбор, что и экран фильтров: раньше каждый ViewModel держал своё
        // состояние, поэтому «Применить» закрывал экран, а полка показывала все книги.
        viewModelScope.launch {
            filterSelection.query.collect { shared ->
                if (query.bookQuery != shared) {
                    // Общий фильтр — это выбор с экрана фильтров, а он живёт по всему списку книг.
                    // Продолжать в нём обзор значило бы спрятать категорийные ограничения:
                    // уровень обзора сбрасывается, дальше пользователь снова выбирает автора.
                    categoryJob?.cancel()
                    query = query.copy(bookQuery = shared, page = 0)
                    _uiState.update {
                        it.copy(
                            page = 0,
                            browse = BrowseUi(),
                        )
                    }
                    observePage(query)
                }
            }
        }
    }

    /** Состояние полки для `collectAsStateWithLifecycle`. */
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    /** Смена сортировки: запрос к БД начинается с первой страницы. */
    fun onSetSort(sort: SortOrder) {
        query = query.copy(sort = sort, page = 0)
        _uiState.update { it.copy(sort = sort, page = 0) }
        observePage(query)
    }

    /** Смена группировки: запрос к БД начинается с первой страницы. */
    fun onSetGrouping(grouping: Grouping) {
        query = query.copy(grouping = grouping, page = 0)
        _uiState.update { it.copy(grouping = grouping, page = 0) }
        observePage(query)
    }

    /**
     * Вид полки одной кнопкой: книги плитками или обзор по авторам/сериям.
     *
     * Варианты объединены в один выбор, потому что для пользователя это одно решение
     * «чем смотреть библиотеку», а не два независимых переключателя. Выбор по умолчанию хранится
     * в настройках ([ShelfViewPreset]) и применяется при открытии полки.
     */
    fun onSetViewPreset(preset: ShelfViewPreset) {
        viewChosenOnShelf = true
        applyViewPreset(preset)
    }

    /** Применить вид полки: книги плитками или обзор по категориям. */
    private fun applyViewPreset(preset: ShelfViewPreset) {
        when (preset) {
            ShelfViewPreset.GRID -> onSetBrowse(BrowseMode.OFF)

            // Обзор по категориям: режим показа книг сохраняется — внутри автора и серии книги
            // показываются так, как пользователь уже выбрал.
            ShelfViewPreset.AUTHORS -> onSetBrowse(BrowseMode.AUTHORS)

            ShelfViewPreset.SERIES -> onSetBrowse(BrowseMode.SERIES)
        }
    }

    /**
     * Включить или выключить обзор по категориям.
     *
     * Обзор — способ дойти до книги без поиска: при 70+ книгах плоский список листается неудобно,
     * а категории дают два-три касания до нужной полки.
     */
    fun onSetBrowse(mode: BrowseMode) {
        viewChosenOnShelf = true
        if (mode == _uiState.value.browse.mode) return
        categoryJob?.cancel()
        applyCategoryFilter()
        _uiState.update {
            it.copy(
                page = 0,
                browse = BrowseUi(
                    mode = mode,
                    level = BrowseLevel.Root,
                    title = browseTitle(mode, BrowseLevel.Root),
                ),
            )
        }
        refreshCategories()
    }

    /**
     * Открыть категорию: в корне — автора или серию, внутри автора — серию либо «Вне серий».
     *
     * Категория — это тот же [BookQuery], что и у фильтров, поэтому книги запрашиваются обычной
     * страницей полки, а сортировка и поиск продолжают работать внутри категории.
     */
    fun onOpenCategory(category: CategoryItemUi) {
        val browse = _uiState.value.browse
        when {
            browse.mode == BrowseMode.AUTHORS && browse.level == BrowseLevel.Root -> openAuthor(category)

            browse.mode == BrowseMode.AUTHORS && browse.level is BrowseLevel.Author -> openSeries(category)

            browse.mode == BrowseMode.SERIES && browse.level == BrowseLevel.Root -> openSeries(category)

            else -> Unit
        }
    }

    /** Вернуться на уровень выше; из корня обзор выключается. */
    fun onBrowseBack() {
        val browse = _uiState.value.browse
        val parent = when (val level = browse.level) {
            is BrowseLevel.Series -> if (browse.mode == BrowseMode.AUTHORS) {
                BrowseLevel.Author(currentAuthorName().orEmpty())
            } else {
                BrowseLevel.Root
            }

            is BrowseLevel.Author -> BrowseLevel.Root

            BrowseLevel.Root -> {
                onSetBrowse(BrowseMode.OFF)
                return
            }
        }
        categoryJob?.cancel()
        // Уровень автора — совмещённый экран: его книги в списке — это книги ВНЕ серий,
        // поэтому и при возврате из серии запрос восстанавливается тем же правилом.
        applyCategoryFilter(
            authorName = (parent as? BrowseLevel.Author)?.name,
            withoutSeries = parent is BrowseLevel.Author,
        )
        _uiState.update {
            it.copy(
                page = 0,
                browse = it.browse.copy(
                    level = parent,
                    categories = emptyList(),
                    title = browseTitle(it.browse.mode, parent),
                    canGoBack = parent != BrowseLevel.Root,
                ),
            )
        }
        refreshCategories()
    }

    private fun openAuthor(category: CategoryItemUi) {
        // Уровень автора — совмещённый экран: сверху его серии, снизу книги вне серий.
        // Поэтому в запрос сразу идёт `onlyWithoutSeries`, а не «все книги автора».
        applyCategoryFilter(authorName = category.id, withoutSeries = true)
        val level = BrowseLevel.Author(category.id)
        _uiState.update {
            it.copy(
                page = 0,
                browse = it.browse.copy(
                    level = level,
                    categories = emptyList(),
                    title = browseTitle(BrowseMode.AUTHORS, level),
                    canGoBack = true,
                ),
            )
        }
        refreshCategories()
    }

    private fun openSeries(category: CategoryItemUi) {
        applyCategoryFilter(authorName = currentAuthorName(), seriesName = category.id)
        val level = BrowseLevel.Series(category.id)
        _uiState.update {
            it.copy(
                page = 0,
                browse = it.browse.copy(
                    level = level,
                    categories = emptyList(),
                    title = browseTitle(it.browse.mode, level),
                    canGoBack = true,
                ),
            )
        }
    }

    /** Ключ текущего автора: он живёт в [BookQuery.authorNames]. */
    private fun currentAuthorName(): String? = query.bookQuery.authorNames.firstOrNull()

    /**
     * Категорийный фильтр запроса.
     *
     * @param authorName ключ автора; `null` — ограничение по автору снимается;
     * @param seriesName имя серии; `null` без [withoutSeries] — серии не ограничивают;
     * @param withoutSeries только книги без серии (категория «Вне серий»).
     */
    private fun applyCategoryFilter(
        authorName: String? = null,
        seriesName: String? = null,
        withoutSeries: Boolean = false,
    ) {
        query = query.copy(
            bookQuery = query.bookQuery.copy(
                authorNames = authorName?.let { setOf(it) } ?: emptySet(),
                series = seriesName?.let { setOf(it) } ?: emptySet(),
                onlyWithoutSeries = withoutSeries,
            ),
            page = 0,
        )
        observePage(query)
    }

    /**
     * Категории текущего уровня: авторы или серии из фасетов, а внутри автора — его серии
     * плюс счётчик книг вне серий.
     */
    private fun refreshCategories() {
        categoryJob?.cancel()
        val browse = _uiState.value.browse
        val level = browse.level
        val categories: Flow<List<CategoryItemUi>> = when {
            browse.mode == BrowseMode.AUTHORS && level == BrowseLevel.Root ->
                filterRepository.observeFacets(query.bookQuery)
                    .map { facets -> facets.authors.map { it.toCategoryUi() } }

            browse.mode == BrowseMode.SERIES && level == BrowseLevel.Root ->
                filterRepository.observeFacets(query.bookQuery)
                    .map { facets -> facets.series.map { it.toCategoryUi() } }

            browse.mode == BrowseMode.AUTHORS && level is BrowseLevel.Author ->
                // На уровне автора категориями идут ТОЛЬКО его серии: книги вне серий показываются
                // ниже обычным списком/сеткой полки, поэтому отдельной категории для них нет.
                filterRepository.observeAuthorSeries(level.name)
                    .map { authorSeries -> authorSeries.series.map { it.toCategoryUi() } }

            else -> flowOf(emptyList())
        }
        categoryJob = viewModelScope.launch {
            categories.collect { items ->
                _uiState.update { it.copy(browse = it.browse.copy(categories = items)) }
            }
        }
    }

    private fun browseTitle(mode: BrowseMode, level: BrowseLevel): String? = when {
        mode == BrowseMode.OFF -> null
        level == BrowseLevel.Root && mode == BrowseMode.AUTHORS -> "Авторы"
        level == BrowseLevel.Root -> "Серии"
        level is BrowseLevel.Author -> "Автор: ${level.name}"
        level is BrowseLevel.Series -> "Серия: ${level.name}"
        else -> null
    }

    /**
     * Сброс активных фильтров над полкой.
     *
     * Идёт через общий выбор: иначе экран фильтров продолжил бы показывать старые чипы.
     */
    fun onClearFilters() {
        filterSelection.clear()
        query = query.copy(bookQuery = BookQuery(), page = 0)
        _uiState.update { it.copy(activeFilters = FilterChipsUi(), page = 0) }
        observePage(query)
    }

    /** Перезагрузка текущей страницы: старая подписка отменяется перед новой. */
    fun onRefresh() {
        observePage(query)
    }

    /**
     * Пересканировать библиотеку: очистить базу и прогнать сканы заново.
     *
     * Подпись кнопки обещает «пересканировать», а не «стереть книги», поэтому подтверждения
     * не спрашиваем: файлы на устройстве команда не трогает, а записи базы восстановятся
     * следующим сканом. Ошибка показывается баннером, а не молчанием.
     */
    fun onRescanAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRescanning = true, error = null) }
            val result = bookRepository.rescanAll()
            _uiState.update {
                it.copy(
                    isRescanning = false,
                    error = if (result.isFailure) {
                        (result.errorOrNull() ?: AppError.Unknown()).message
                            ?: "Не удалось пересканировать библиотеку"
                    } else {
                        null
                    },
                )
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun observePage(currentQuery: LibraryQuery) {
        pageJob?.cancel()
        pageJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                bookRepository.observePage(currentQuery).collect { page ->
                    _uiState.update { state ->
                        state.copy(
                            books = page.items.map { book -> book.toCard() },
                            total = page.totalCount,
                            page = clampPage(currentQuery.page, page.totalCount, currentQuery.pageSize),
                            isLoading = false,
                            error = null,
                        )
                    }
                }
                _uiState.update { it.copy(isLoading = false) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = error.message ?: "Не удалось загрузить полку",
                    )
                }
            }
        }
    }

    private fun Book.toCard(): BookCardUi = BookCardUi(
        id = id,
        title = title,
        authors = authorsText.ifBlank { authors.joinToString(", ") },
        coverPath = coverPath,
        readingStatus = readingStatus,
        progressPercent = progressPercent?.let { value ->
            value.roundToInt().coerceIn(MIN_PROGRESS_PERCENT, MAX_PROGRESS_PERCENT)
        },
    )

    private companion object {
        const val MIN_PROGRESS_PERCENT = 0
        const val MAX_PROGRESS_PERCENT = 100

        /**
         * Сколько книг полка запрашивает за раз.
         *
         * Страницы как единица листания больше нет, а ограничивать 50 книгами (значение по
         * умолчанию) означало бы тихо обрезать полку: при 200 книгах половина просто не
         * появлялась бы. 500 — с запасом для домашней библиотеки и с честным пределом запроса.
         */
        const val SINGLE_PAGE_SIZE = 500
    }
}

/** Фасет → строка категории: подпись и число книг приходят из БД. */
private fun FacetItem.toCategoryUi(): CategoryItemUi = CategoryItemUi(id = id, label = label, count = count)
