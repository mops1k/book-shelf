package io.github.bookshelf.feature.library

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.data.model.AuthorSeries
import io.github.bookshelf.core.data.model.Book
import io.github.bookshelf.core.data.model.BookQuery
import io.github.bookshelf.core.data.model.FacetItem
import io.github.bookshelf.core.data.model.LibraryQuery
import io.github.bookshelf.core.data.paging.PageResult
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.data.duplicates.DuplicateReport
import io.github.bookshelf.core.data.repository.BookRepository
import io.github.bookshelf.core.data.repository.DeletionResult
import io.github.bookshelf.core.data.repository.FilterSelection
import io.github.bookshelf.core.data.repository.LibraryBrowseRequest
import io.github.bookshelf.core.datastore.SettingsRepository
import io.github.bookshelf.core.datastore.model.AppSettings
import io.github.bookshelf.core.datastore.model.LibrarySettings
import io.github.bookshelf.core.datastore.model.ShelfViewPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Обзор полки по категориям: авторы → серии (и «Вне серий») → книги.
 *
 * Проверяется то, что не поймает ни один тест БД: переходы между уровнями, подстановка фильтра
 * в запрос полки и возврат назад. Смысл обзора — найти книгу без поиска, поэтому у каждой
 * категории обязан быть рабочий путь до списка книг.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryBrowseTest {

    private val dispatcher = StandardTestDispatcher()

    /** Просьбы карточки книги: «покажи автора/серию» — их забирает эта же ViewModel. */
    private val browseRequest = LibraryBrowseRequest()
    private val repository = RecordingBookRepository()
    private val filterRepository = FakeFilterRepository(
        authors = listOf(
            FacetItem(id = "Лев Толстой", label = "Лев Толстой", count = 3),
            FacetItem(id = "Фёдор Достоевский", label = "Фёдор Достоевский", count = 1),
        ),
        series = listOf(FacetItem(id = "Классика", label = "Классика", count = 2)),
        authorSeries = mapOf(
            "Лев Толстой" to AuthorSeries(
                series = listOf(FacetItem(id = "Классика", label = "Классика", count = 2)),
                withoutSeries = 1,
            ),
        ),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `authors root shows authors as categories`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onSetBrowse(BrowseMode.AUTHORS)
        advanceUntilIdle()

        val browse = viewModel.uiState.value.browse
        assertThat(browse.title).isEqualTo("Авторы")
        assertThat(browse.categories.map { it.label })
            .containsExactly("Лев Толстой", "Фёдор Достоевский")
        assertThat(browse.canGoBack).isFalse()
        // Пока показываются категории, книги запрашиваются без категорийных ограничений.
        assertThat(repository.queries.last().bookQuery.authorNames).isEmpty()
    }

    @Test
    fun `opening an author filters books to those outside series and lists his series`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onSetBrowse(BrowseMode.AUTHORS)
        advanceUntilIdle()

        viewModel.onOpenCategory(CategoryItemUi(id = "Лев Толстой", label = "Лев Толстой", count = 3))
        advanceUntilIdle()

        // Уровень автора — совмещённый экран: категории показывают его серии, а список книг
        // под ними — книги ВНЕ серий (иначе они были бы недостижимы без отдельной категории).
        val query = repository.queries.last().bookQuery
        assertThat(query.authorNames).containsExactly("Лев Толстой")
        assertThat(query.onlyWithoutSeries).isTrue()
        assertThat(query.series).isEmpty()

        val browse = viewModel.uiState.value.browse
        assertThat(browse.title).isEqualTo("Автор: Лев Толстой")
        assertThat(browse.categories.map { it.label }).containsExactly("Классика")
        assertThat(browse.canGoBack).isTrue()
    }

    @Test
    fun `opening a series filters books by series`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onSetBrowse(BrowseMode.AUTHORS)
        advanceUntilIdle()
        viewModel.onOpenCategory(CategoryItemUi(id = "Лев Толстой", label = "Лев Толстой", count = 3))
        advanceUntilIdle()

        viewModel.onOpenCategory(CategoryItemUi(id = "Классика", label = "Классика", count = 2))
        advanceUntilIdle()

        val query = repository.queries.last().bookQuery
        assertThat(query.authorNames).containsExactly("Лев Толстой")
        assertThat(query.series).containsExactly("Классика")
        assertThat(query.onlyWithoutSeries).isFalse()
        // Уровень «серия» показывает книги, а не категории.
        assertThat(viewModel.uiState.value.browse.categories).isEmpty()
        assertThat(viewModel.uiState.value.browse.title).isEqualTo("Серия: Классика")
    }

    @Test
    fun `author without series shows only his books, without categories`() = runTest(dispatcher) {
        // У «Дениса Грея» все книги без серии: категорий не будет вовсе, а под заголовком
        // уровня останется тот же список книг — без лишнего уровня «Вне серий».
        val viewModel = LibraryViewModel(
            bookRepository = repository,
            settingsRepository = FakeSettingsRepository(),
            filterSelection = FilterSelection(),
            browseRequest = browseRequest,
            filterRepository = FakeFilterRepository(
                authorSeries = mapOf(
                    "Денис Грей" to AuthorSeries(series = emptyList(), withoutSeries = 1),
                ),
            ),
        )
        viewModel.onSetBrowse(BrowseMode.AUTHORS)
        advanceUntilIdle()

        viewModel.onOpenCategory(CategoryItemUi(id = "Денис Грей", label = "Денис Грей", count = 1))
        advanceUntilIdle()

        val browse = viewModel.uiState.value.browse
        assertThat(browse.categories).isEmpty()
        assertThat(browse.showsCategories).isFalse()
        val query = repository.queries.last().bookQuery
        assertThat(query.authorNames).containsExactly("Денис Грей")
        assertThat(query.onlyWithoutSeries).isTrue()
    }

    @Test
    fun `back from a series returns to the author, then to the authors list`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onSetBrowse(BrowseMode.AUTHORS)
        advanceUntilIdle()
        viewModel.onOpenCategory(CategoryItemUi(id = "Лев Толстой", label = "Лев Толстой", count = 3))
        advanceUntilIdle()
        viewModel.onOpenCategory(CategoryItemUi(id = "Классика", label = "Классика", count = 2))
        advanceUntilIdle()

        viewModel.onBrowseBack()
        advanceUntilIdle()

        // Назад из серии: снова серии того же автора, а книги остаются его же.
        assertThat(viewModel.uiState.value.browse.title).isEqualTo("Автор: Лев Толстой")
        assertThat(repository.queries.last().bookQuery.authorNames).containsExactly("Лев Толстой")
        assertThat(repository.queries.last().bookQuery.series).isEmpty()

        viewModel.onBrowseBack()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.browse.title).isEqualTo("Авторы")
        assertThat(viewModel.uiState.value.browse.canGoBack).isFalse()
        assertThat(repository.queries.last().bookQuery.authorNames).isEmpty()
    }

    @Test
    fun `back from the root turns browsing off`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onSetBrowse(BrowseMode.SERIES)
        advanceUntilIdle()

        viewModel.onBrowseBack()
        advanceUntilIdle()

        val browse = viewModel.uiState.value.browse
        assertThat(browse.mode).isEqualTo(BrowseMode.OFF)
        assertThat(browse.title).isNull()
        assertThat(browse.categories).isEmpty()
    }

    @Test
    fun `series root lists series and opening one filters books`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onSetBrowse(BrowseMode.SERIES)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.browse.title).isEqualTo("Серии")
        assertThat(viewModel.uiState.value.browse.categories.map { it.label }).containsExactly("Классика")

        viewModel.onOpenCategory(CategoryItemUi(id = "Классика", label = "Классика", count = 2))
        advanceUntilIdle()

        assertThat(repository.queries.last().bookQuery.series).containsExactly("Классика")
    }

    @Test
    fun `applying shared filters turns browsing off`() = runTest(dispatcher) {
        val selection = FilterSelection()
        val viewModel = LibraryViewModel(
            bookRepository = repository,
            settingsRepository = FakeSettingsRepository(),
            filterSelection = selection,
            browseRequest = browseRequest,
            filterRepository = filterRepository,
        )
        viewModel.onSetBrowse(BrowseMode.AUTHORS)
        advanceUntilIdle()

        selection.apply(BookQuery(authorNames = setOf("Лев Толстой")))
        advanceUntilIdle()

        // Обзор сбрасывается: иначе на экране остались бы категорийные ограничения,
        // которых пользователь не выбирал на экране фильтров.
        assertThat(viewModel.uiState.value.browse.mode).isEqualTo(BrowseMode.OFF)
        assertThat(repository.queries.last().bookQuery.authorNames).containsExactly("Лев Толстой")
    }

    @Test
    fun `view preset switches between the shelf and category browsing`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onSetViewPreset(ShelfViewPreset.GRID)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.browse.mode).isEqualTo(BrowseMode.OFF)

        viewModel.onSetViewPreset(ShelfViewPreset.AUTHORS)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.browse.mode).isEqualTo(BrowseMode.AUTHORS)
        assertThat(viewModel.uiState.value.browse.categories.map { it.label })
            .containsExactly("Лев Толстой", "Фёдор Достоевский")

        viewModel.onSetViewPreset(ShelfViewPreset.SERIES)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.browse.mode).isEqualTo(BrowseMode.SERIES)

        viewModel.onSetViewPreset(ShelfViewPreset.GRID)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.browse.mode).isEqualTo(BrowseMode.OFF)
        assertThat(viewModel.uiState.value.browse.categories).isEmpty()
    }

    @Test
    fun `default view preset from settings is applied when the shelf opens`() = runTest(dispatcher) {
        val viewModel = LibraryViewModel(
            bookRepository = repository,
            settingsRepository = FakeSettingsRepository(ShelfViewPreset.SERIES),
            filterSelection = FilterSelection(),
            browseRequest = browseRequest,
            filterRepository = filterRepository,
        )
        advanceUntilIdle()

        // Настройка «вид полки по умолчанию» — то, чем полка открывается, а не только значение
        // на экране настроек.
        assertThat(viewModel.uiState.value.browse.mode).isEqualTo(BrowseMode.SERIES)
        assertThat(viewModel.uiState.value.browse.title).isEqualTo("Серии")
        assertThat(viewModel.uiState.value.browse.categories.map { it.label }).containsExactly("Классика")
    }

    @Test
    fun `card request opens the author view on that author with his series`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        // Так карточка книги просит полку показать автора: требование Александра (28.09.2026) —
        // приходим в вид «Авторы» на нём, сверху его серии, снизу книги вне серий.
        browseRequest.openAuthor("Лев Толстой")
        advanceUntilIdle()

        val browse = viewModel.uiState.value.browse
        assertThat(browse.mode).isEqualTo(BrowseMode.AUTHORS)
        assertThat(browse.level).isEqualTo(BrowseLevel.Author("Лев Толстой"))
        assertThat(browse.categories.map { it.label }).containsExactly("Классика")
        assertThat(repository.queries.last().bookQuery.authorNames).containsExactly("Лев Толстой")
        // Просьба снимается: иначе она срабатывала бы при каждом возвращении на полку.
        assertThat(browseRequest.request.value).isNull()
    }

    @Test
    fun `card request opens the series without the previous author filter`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onSetBrowse(BrowseMode.AUTHORS)
        viewModel.onOpenCategory(CategoryItemUi(id = "Лев Толстой", label = "Лев Толстой", count = 3))
        advanceUntilIdle()

        browseRequest.openSeries("Классика")
        advanceUntilIdle()

        val browse = viewModel.uiState.value.browse
        assertThat(browse.mode).isEqualTo(BrowseMode.SERIES)
        assertThat(browse.level).isEqualTo(BrowseLevel.Series("Классика"))
        // Авторский фильтр прошлого захода снят: иначе серия показала бы только книги Толстого.
        val query = repository.queries.last().bookQuery
        assertThat(query.series).containsExactly("Классика")
        assertThat(query.authorNames).isEmpty()
    }

    private fun viewModel(): LibraryViewModel = LibraryViewModel(
        bookRepository = repository,
        settingsRepository = FakeSettingsRepository(),
        filterSelection = FilterSelection(),
        browseRequest = browseRequest,
        filterRepository = filterRepository,
    )

    /** Полка, запоминающая запросы: проверяем, что фильтр категории доходит до репозитория. */
    private class RecordingBookRepository : BookRepository {
        val queries = mutableListOf<LibraryQuery>()

        override fun observePage(query: LibraryQuery): Flow<PageResult<Book>> {
            queries += query
            return flowOf(PageResult(items = emptyList(), totalCount = 0, offset = 0, limit = 20))
        }

        override fun observeById(id: String): Flow<Book?> = flowOf(null)

        override suspend fun getById(id: String) = io.github.bookshelf.core.common.error.AppResult.success<Book?>(null)

        override suspend fun updateMetadata(bookId: String, patch: io.github.bookshelf.core.data.model.MetadataPatch) =
            io.github.bookshelf.core.common.error.AppResult.success(Unit)

        override suspend fun setFavorite(bookId: String, favorite: Boolean) =
            io.github.bookshelf.core.common.error.AppResult.success(Unit)

        override suspend fun setRating(bookId: String, rating: Int?) =
            io.github.bookshelf.core.common.error.AppResult.success(Unit)

        override suspend fun markRead(bookId: String, status: io.github.bookshelf.core.data.model.ReadingStatus) =
            io.github.bookshelf.core.common.error.AppResult.success(Unit)

        override suspend fun delete(bookId: String) =
            io.github.bookshelf.core.common.error.AppResult.success(Unit)

        /** Удаление с файлом и поиск дублей в тестах полки не проверяются. */
        override suspend fun deletePermanently(bookIds: List<String>): AppResult<DeletionResult> =
            AppResult.success(DeletionResult(deleted = bookIds.size, failed = 0))

        override suspend fun findDuplicates(): AppResult<DuplicateReport> = AppResult.success(DuplicateReport())
        /** Прогресс чтения и поиск по адресу в этих проверках не участвуют. */
        override suspend fun findByDocumentUri(documentUri: String): AppResult<Book?> =
            AppResult.success(null)

        override suspend fun saveReadingProgress(
            bookId: String,
            percent: Float,
            locatorJson: String?,
            source: String,
        ): AppResult<Unit> = AppResult.success(Unit)


        override suspend fun rescanAll() = io.github.bookshelf.core.common.error.AppResult.success(Unit)
    }
}

/** Настройки в проверках обзора участвуют только видом полки по умолчанию. */
private class FakeSettingsRepository(
    private val preset: ShelfViewPreset = ShelfViewPreset.GRID,
) : SettingsRepository {

    override suspend fun resetToDefaults() = Unit
    override val settings: Flow<AppSettings> = flowOf(appSettings())

    override suspend fun current(): AppSettings = appSettings()

    override suspend fun update(transform: (AppSettings) -> AppSettings) = Unit

    private fun appSettings(): AppSettings =
        AppSettings(library = LibrarySettings(viewPreset = preset))
}
