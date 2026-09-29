package io.github.bookshelf.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.bookshelf.core.data.author.authorSetNames
import io.github.bookshelf.core.data.model.AuthorSeries
import io.github.bookshelf.core.data.model.AuthorSet
import io.github.bookshelf.core.data.model.Book
import io.github.bookshelf.core.data.model.BookQuery
import io.github.bookshelf.core.data.model.FacetItem
import io.github.bookshelf.core.data.model.Grouping
import io.github.bookshelf.core.data.model.LibraryQuery
import io.github.bookshelf.core.data.model.SortOrder
import io.github.bookshelf.core.data.mapper.LibraryQueryDefaults
import io.github.bookshelf.core.data.mapper.toLibraryQueryDefaults
import io.github.bookshelf.core.data.repository.BookRepository
import io.github.bookshelf.core.data.repository.FilterRepository
import io.github.bookshelf.core.data.repository.FilterSelection
import io.github.bookshelf.core.data.repository.LibraryBrowseRequest
import io.github.bookshelf.core.datastore.SettingsRepository
import io.github.bookshelf.core.data.tag.genreDisplayName
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
import android.database.SQLException

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
    private val browseRequest: LibraryBrowseRequest,
    private val filterRepository: FilterRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())

    private var query: LibraryQuery = LibraryQuery(
        sort = _uiState.value.sort,
        grouping = _uiState.value.grouping,
        page = 0,
        pageSize = SINGLE_PAGE_SIZE,
    )

    /**
     * Жанр, в который зашли в обзоре; `null` — обзор без ограничения по жанру.
     *
     * Живёт здесь, а не в [query]: жанр — часть пути обзора (жанр → авторы жанра → серии), и как
     * только человек вышел на корень или сменил вид кнопками, ограничение обязано исчезнуть.
     * Дефект 29.09.2026: жанр оставался в запросе, и «Авторы» показывали только авторов этого жанра.
     */
    private var browseGenreCode: String? = null

    /**
     * Набор авторов, в который зашли в обзоре; `null` — уровень набора не открыт.
     *
     * Хранится рядом с уровнем, а не внутри него: из серии назад нужно вернуть именно набор
     * с его подписью, а у уровня серии подписи набора нет. Так же устроен [browseGenreCode].
     */
    private var currentAuthorSet: BrowseLevel.AuthorSet? = null

    private var pageJob: Job? = null

    /** Подписка на категории обзора: своя на каждый уровень, старую отменяем. */
    private var categoryJob: Job? = null

    /** Пользователь уже выбирал вид на самой полке: первое значение настройки его не перебивает. */
    private var viewChosenOnShelf = false

    init {
        observeBrowseRequest()
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
        // Сортировка, направление и группировка по умолчанию — тоже настройки (docs/eink-ux.md
        // §6.2). Это подписка, а не разовое чтение: изменение настройки перестраивает уже
        // открытую полку, без перезапуска приложения («применяется сразу»). `distinctUntilChanged`
        // по самой тройке значений нужен, чтобы смена посторонней настройки (например, лимита
        // кэша обложек) не пересортировала полку под пользователем: выбор, сделанный на полке,
        // живёт, пока не изменится именно порядок по умолчанию.
        viewModelScope.launch {
            settingsRepository.settings
                .map { settings -> settings.library.toLibraryQueryDefaults() }
                .distinctUntilChanged()
                .collect { defaults -> applyDefaultOrder(defaults) }
        }
        // Полка читает тот же выбор, что и экран фильтров: раньше каждый ViewModel держал своё
        // состояние, поэтому «Применить» закрывал экран, а полка показывала все книги.
        viewModelScope.launch {
            filterSelection.query.collect { shared ->
                // Кнопка «Фильтры (N)» и подпись «Порядок серии: по выпуску» в подвале читают тот же
                // общий выбор. Раньше `activeFilters` заполнялось только при сбросе, поэтому на
                // кнопке всегда стоял ноль, хотя полка фильтровала книги (дефект от 28.09.2026).
                _uiState.update { it.copy(activeFilters = shared.toFilterChipsUi()) }
                if (query.bookQuery != shared) {
                    // Общий фильтр — это выбор с экрана фильтров, а он живёт по всему списку книг.
                    // Продолжать в нём обзор значило бы спрятать категорийные ограничения:
                    // уровень обзора сбрасывается, дальше пользователь снова выбирает автора.
                    categoryJob?.cancel()
                    browseGenreCode = null
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
     * Порядок полки, заданный настройками.
     *
     * Применяется целиком (сортировка, направление, группировка) одним запросом: по отдельности
     * это были бы три обращения к БД. Вызывается как на первой эмиссии настроек (чем открывается
     * полка), так и при каждом изменении этой тройки — поэтому смена умолчания видна сразу.
     */
    private fun applyDefaultOrder(defaults: LibraryQueryDefaults) {
        if (
            defaults.sort == query.sort &&
            defaults.descending == query.descending &&
            defaults.grouping == query.grouping
        ) {
            return
        }
        query = query.copy(
            sort = defaults.sort,
            descending = defaults.descending,
            grouping = defaults.grouping,
            page = 0,
        )
        _uiState.update { it.copy(sort = defaults.sort, grouping = defaults.grouping, page = 0) }
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

            ShelfViewPreset.GENRES -> onSetBrowse(BrowseMode.GENRES)
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
        // Смена вида — выход из категории: ограничения пути обзора снимаются.
        browseGenreCode = null
        currentAuthorSet = null
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
     * Открыть категорию: в корне авторов — набор авторов, в корне серий — серию.
     *
     * Категория — это тот же [BookQuery], что и у фильтров, поэтому книги запрашиваются обычной
     * страницей полки, а сортировка и поиск продолжают работать внутри категории.
     */
    fun onOpenCategory(category: CategoryItemUi) {
        val browse = _uiState.value.browse
        when {
            // Категория обзора авторов — ТОЧНЫЙ набор авторов книги (решение владельца,
            // 29.09.2026): «A, B» и «B, A» — одна категория, «A, B, C» — другая.
            browse.mode == BrowseMode.AUTHORS && browse.level == BrowseLevel.Root ->
                openAuthorSet(category)

            browse.mode == BrowseMode.AUTHORS && browse.level is BrowseLevel.AuthorSet ->
                openSeries(category)

            browse.mode == BrowseMode.SERIES && browse.level == BrowseLevel.Root -> openSeries(category)

            // Обзор по жанрам идёт той же лестницей, что и по авторам, только с корнем «жанр»:
            // жанр → его наборы авторов → серии набора (внутри жанра) → книги серии.
            browse.mode == BrowseMode.GENRES && browse.level == BrowseLevel.Root -> openGenre(category)

            browse.mode == BrowseMode.GENRES && browse.level is BrowseLevel.Genre ->
                openAuthorSet(category)

            browse.mode == BrowseMode.GENRES && browse.level is BrowseLevel.AuthorSet ->
                openSeries(category)

            else -> Unit
        }
    }

    /**
     * Просьбы карточки книги: показать книги автора или серии.
     *
     * Приём тот же, что у фильтров: карточка кладёт просьбу в общее состояние, полка её забирает.
     * Просьба снимается сразу после обработки — иначе она срабатывала бы при каждом возвращении
     * на полку и затирала выбранное пользователем.
     */
    private fun observeBrowseRequest() {
        viewModelScope.launch {
            browseRequest.request.collect { request ->
                if (request == null) return@collect
                browseRequest.consume()
                when (request) {
                    is LibraryBrowseRequest.Request.Author -> onOpenAuthorByName(request.name)
                    is LibraryBrowseRequest.Request.Series -> onOpenSeriesByName(request.name)
                }
            }
        }
    }

    /**
     * Показать книги автора в виде «Авторы».
     *
     * Именно вид авторов, а не плоский список с фильтром: на уровне автора сверху его серии,
     * снизу книги вне серий — так книга находится без поиска (требование Александра, 28.09.2026).
     */
    fun onOpenAuthorByName(name: String) {
        if (name.isBlank()) return
        onSetBrowse(BrowseMode.AUTHORS)
        openAuthor(CategoryItemUi(id = name, label = name, count = 0))
    }

    /**
     * Показать книги серии.
     *
     * Авторский фильтр снимается: серия открывается целиком, а не только у автора, чей фильтр
     * остался от прошлого захода (иначе с полки автора серия показала бы часть книг).
     */
    fun onOpenSeriesByName(name: String) {
        if (name.isBlank()) return
        onSetBrowse(BrowseMode.SERIES)
        applyCategoryFilter(authorName = null, seriesName = name)
        val level = BrowseLevel.Series(name)
        _uiState.update {
            it.copy(
                page = 0,
                browse = it.browse.copy(
                    level = level,
                    categories = emptyList(),
                    title = browseTitle(BrowseMode.SERIES, level),
                    canGoBack = true,
                ),
            )
        }
    }

    /** Вернуться на уровень выше; из корня обзор выключается. */
    fun onBrowseBack() {
        val browse = _uiState.value.browse
        val parent = when (val level = browse.level) {
            // Из серии возвращаемся туда, откуда пришли: в набор авторов, если заходили в него,
            // иначе к автору из карточки книги.
            is BrowseLevel.Series -> if (browse.mode == BrowseMode.AUTHORS ||
                browse.mode == BrowseMode.GENRES
            ) {
                currentAuthorSet ?: BrowseLevel.Author(currentAuthorName().orEmpty())
            } else {
                BrowseLevel.Root
            }

            // Из автора возвращаемся туда, откуда пришли: в обзоре по жанрам — в его жанр,
            // в обзоре по авторам — к списку авторов. Решает режим, а не фильтр: фильтр по жанру
            // мог остаться с экрана фильтров, и в обзоре по авторам он не повод уводить в жанр.
            is BrowseLevel.Author -> if (browse.mode == BrowseMode.GENRES) {
                currentGenreLevel() ?: BrowseLevel.Root
            } else {
                BrowseLevel.Root
            }

            // Уровень набора устроен как уровень автора: сверху серии набора, снизу его книги
            // вне серий. Назад — в жанр (обзор по жанрам) или к списку категорий.
            is BrowseLevel.AuthorSet -> if (browse.mode == BrowseMode.GENRES) {
                currentGenreLevel() ?: BrowseLevel.Root
            } else {
                BrowseLevel.Root
            }

            is BrowseLevel.Genre -> BrowseLevel.Root

            BrowseLevel.Root -> {
                onSetBrowse(BrowseMode.OFF)
                return
            }
        }
        categoryJob?.cancel()
        if (parent == BrowseLevel.Root) {
            // Вышли на корень — жанр обзора и набор перестают ограничивать запрос.
            browseGenreCode = null
            currentAuthorSet = null
        }
        // Уровень автора и уровень набора — совмещённые экраны: их книги в списке — это книги
        // ВНЕ серий, поэтому и при возврате из серии запрос восстанавливается тем же правилом.
        applyCategoryFilter(
            authorName = (parent as? BrowseLevel.Author)?.name,
            authorSetKey = (parent as? BrowseLevel.AuthorSet)?.key,
            withoutSeries = parent is BrowseLevel.Author || parent is BrowseLevel.AuthorSet,
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

    /**
     * Открыть жанр: фильтр по коду жанра, категориями — авторы этого жанра.
     *
     * Автор и серия снимаются: человек сменил измерение, и «остатки» прошлого захода показывали бы
     * часть книг жанра как весь жанр.
     */
    private fun openGenre(category: CategoryItemUi) {
        browseGenreCode = category.id
        currentAuthorSet = null
        applyCategoryFilter()
        val level = BrowseLevel.Genre(code = category.id, label = category.label)
        _uiState.update {
            it.copy(
                page = 0,
                browse = it.browse.copy(
                    level = level,
                    categories = emptyList(),
                    title = browseTitle(BrowseMode.GENRES, level),
                    canGoBack = true,
                ),
            )
        }
        refreshCategories()
    }

    /**
     * Открыть набор авторов — категорию обзора.
     *
     * Фильтр ставится по составу («ровно эти авторы»), а не по одному имени: иначе в категории
     * «Александр Тарарев, Юрий Тарарев» показались бы и книги, где у Александра другие соавторы,
     * и число в категории перестало бы совпадать с её содержимым.
     */
    private fun openAuthorSet(category: CategoryItemUi) {
        currentAuthorSet = BrowseLevel.AuthorSet(key = category.id, label = category.label)
        // Уровень набора — совмещённый экран, как и уровень автора: сверху серии набора,
        // снизу его книги вне серий.
        applyCategoryFilter(authorSetKey = category.id, withoutSeries = true)
        val level = requireNotNull(currentAuthorSet)
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
        refreshCategories()
    }

    /**
     * Открыть автора из карточки книги — все его книги, в любой позиции и с любыми соавторами.
     *
     * Это не категория обзора: категория — точный набор авторов, и книга с соавторами в неё
     * не попадает. Здесь же человек пришёл по конкретному человеку, поэтому фильтр — имя автора.
     */
    private fun openAuthor(category: CategoryItemUi) {
        // Уровень автора — совмещённый экран: сверху его серии, снизу книги вне серий.
        // Поэтому в запрос сразу идёт `onlyWithoutSeries`, а не «все книги автора».
        currentAuthorSet = null
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
        // Набор, если зашли в него, сохраняется: серия внутри категории — серия её книг, а не
        // всех книг библиотеки с таким именем.
        val authorSetKey = (_uiState.value.browse.level as? BrowseLevel.AuthorSet)?.key
        applyCategoryFilter(
            authorName = currentAuthorName(),
            authorSetKey = authorSetKey,
            seriesName = category.id,
        )
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
     * @param authorName имя автора (все его книги, в любой позиции); `null` — ограничение снимается;
     * @param authorSetKey ключ набора авторов — категория обзора «книги ровно этого состава»;
     * @param seriesName имя серии; `null` без [withoutSeries] — серии не ограничивают;
     * @param withoutSeries только книги без серии (категория «Вне серий»).
     */
    private fun applyCategoryFilter(
        authorName: String? = null,
        authorSetKey: String? = null,
        seriesName: String? = null,
        withoutSeries: Boolean = false,
        genreTags: Set<String> = browseTags(),
    ) {
        query = query.copy(
            bookQuery = query.bookQuery.copy(
                authorNames = authorName?.let { setOf(it) } ?: emptySet(),
                // Набор ходит по UI ключом, а фильтру нужны имена: запрос сравнивает состав
                // авторов, поэтому порядок имён в ключе на результат не влияет.
                authorSets = authorSetKey?.let { key -> setOf(authorSetNames(key)) } ?: emptySet(),
                series = seriesName?.let { setOf(it) } ?: emptySet(),
                onlyWithoutSeries = withoutSeries,
                // Жанры по умолчанию остаются как были: и выбранный в обзоре жанр, и фильтр с
                // экрана фильтров. Сбрасывать их на каждом шаге вложенности было бы потерей выбора.
                tags = genreTags,
            ),
            page = 0,
        )
        observePage(query)
    }

    /** Жанры, выбранные на экране фильтров: они живут по всей библиотеке и обзором не снимаются. */
    private fun selectionTags(): Set<String> = filterSelection.query.value.tags

    /** Жанры запроса обзора: выбор с экрана фильтров плюс жанр, в который зашли. */
    private fun browseTags(): Set<String> = selectionTags() + listOfNotNull(browseGenreCode)

    /** Код жанра, по которому сейчас идёт обзор; `null` — жанр не выбран. */
    private fun currentGenreCode(): String? = browseGenreCode

    /**
     * Уровень жанра для возврата из автора.
     *
     * Название берётся из записи уровня, а если её нет (например, автора открыли из карточки книги
     * внутри жанра) — из кода жанра через тот же перевод, что и в списке жанров.
     */
    private fun currentGenreLevel(): BrowseLevel.Genre? {
        val code = currentGenreCode() ?: return null
        return BrowseLevel.Genre(code = code, label = genreDisplayName(code))
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
            // Корень «Авторы» — категории-наборы: «Александр Тарарев, Юрий Тарарев» и
            // «Юрий Тарарев, Александр Тарарев» обязаны быть одной категорией.
            browse.mode == BrowseMode.AUTHORS && level == BrowseLevel.Root ->
                filterRepository.observeAuthorSets(query.bookQuery)
                    .map { sets -> sets.map { it.toCategoryUi() } }

            browse.mode == BrowseMode.SERIES && level == BrowseLevel.Root ->
                filterRepository.observeFacets(query.bookQuery)
                    .map { facets -> facets.series.map { it.toCategoryUi() } }

            // Жанры: `id` — код из метаданных (по нему фильтрует SQL), подпись уже переведена
            // в фасете («sf_space» → «Космическая фантастика»).
            browse.mode == BrowseMode.GENRES && level == BrowseLevel.Root ->
                filterRepository.observeFacets(query.bookQuery)
                    .map { facets -> facets.tags.map { it.toCategoryUi() } }

            // Внутри жанра категориями идут наборы авторов этого жанра: запрос уже несёт тег,
            // поэтому фасет считается только по книгам этого жанра.
            browse.mode == BrowseMode.GENRES && level is BrowseLevel.Genre ->
                filterRepository.observeAuthorSets(query.bookQuery)
                    .map { sets -> sets.map { it.toCategoryUi() } }

            // Сериями уровня набора служат серии его книг — не серии соавтора целиком.
            browse.mode == BrowseMode.GENRES && level is BrowseLevel.AuthorSet ->
                filterRepository.observeAuthorSetSeries(
                    authorNames = authorSetNames(level.key),
                    genreCode = currentGenreCode(),
                ).map { authorSeries -> authorSeries.series.map { it.toCategoryUi() } }

            // На уровне автора и набора категориями идут ТОЛЬКО их серии: книги вне серий
            // показываются ниже обычным списком/сеткой полки, поэтому отдельной категории нет.
            browse.mode == BrowseMode.AUTHORS && level is BrowseLevel.Author ->
                filterRepository.observeAuthorSeries(level.name)
                    .map { authorSeries -> authorSeries.series.map { it.toCategoryUi() } }

            browse.mode == BrowseMode.AUTHORS && level is BrowseLevel.AuthorSet ->
                filterRepository.observeAuthorSetSeries(authorNames = authorSetNames(level.key))
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
        level == BrowseLevel.Root && mode == BrowseMode.GENRES -> "Жанры"
        level == BrowseLevel.Root -> "Серии"
        level is BrowseLevel.Genre -> "Жанр: ${level.label}"
        level is BrowseLevel.Author -> "Автор: ${level.name}"
        // «Автор» для одного имени и «Авторы» для набора: подпись набора перечисляет людей
        // через запятую, и единственное число на ней читалось бы как ошибка.
        level is BrowseLevel.AuthorSet -> if (level.label.contains(',')) {
            "Авторы: ${level.label}"
        } else {
            "Автор: ${level.label}"
        }
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

    private fun observePage(currentQuery: LibraryQuery) {
        pageJob?.cancel()
        // Порядок внутри серии приходит из запроса: подвал обязан повторять то, как книги реально
        // упорядочены, а не то, что выбрано на экране фильтров (`BookPageSql` включает порядок
        // по выпуску, когда серия ровно одна — и при выборе серии в обзоре тоже).
        _uiState.update { it.copy(seriesOrderByRelease = currentQuery.bookQuery.series.size == 1) }
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
            } catch (error: SQLException) {
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
        extension = extension,
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

/**
 * Набор авторов → строка категории.
 *
 * `id` — ключ набора: по нему отличается категория «Александр Тарарев, Юрий Тарарев» от категорий
 * отдельных авторов, и по нему же строится фильтр при заходе в категорию.
 */
private fun AuthorSet.toCategoryUi(): CategoryItemUi =
    CategoryItemUi(id = key, label = label, count = count)
