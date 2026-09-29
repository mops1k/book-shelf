package io.github.bookshelf.feature.library

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.data.model.AuthorSeries
import io.github.bookshelf.core.data.model.BookQuery
import io.github.bookshelf.core.data.repository.FilterSelection
import io.github.bookshelf.core.data.repository.LibraryBrowseRequest
import io.github.bookshelf.core.data.model.FacetItem
import io.github.bookshelf.core.data.model.FacetSet
import io.github.bookshelf.core.datastore.SettingsRepository
import io.github.bookshelf.core.datastore.model.AppSettings
import io.github.bookshelf.core.testing.fake.FakeBookRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
 * Обзор полки по жанрам (`LibraryViewModel`).
 *
 * Проверяется выбранная Александром лестница: «Жанры» → авторы жанра → серии автора внутри жанра →
 * книги. Ключевые утверждения — про данные, а не про экран: в запрос уходит код жанра, при заходе
 * в автора жанр сохраняется, а серии спрашиваются вместе с кодом жанра (иначе в «Космической
 * фантастике» у автора всплыли бы серии других жанров и числа в категориях разошлись бы с книгами).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryGenreBrowseTest {

    private val dispatcher = StandardTestDispatcher()
    private val books = FakeBookRepository()
    private val filters = FakeFilterRepository(
        authorSeries = mapOf(
            "Лем Станислав" to AuthorSeries(
                series = listOf(
                    FacetItem(id = "Приключения Флинкса", label = "Приключения Флинкса", count = 1),
                ),
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
    fun `genre root lists genres with their names and counts`() = runTest(dispatcher) {
        filters.tagFacets.value = listOf(
            FacetItem(id = "sf_space", label = "Космическая фантастика", count = 43),
        )
        val viewModel = viewModel()

        viewModel.onSetBrowse(BrowseMode.GENRES)
        advanceUntilIdle()

        val browse = viewModel.uiState.value.browse
        assertThat(browse.mode).isEqualTo(BrowseMode.GENRES)
        assertThat(browse.level).isEqualTo(BrowseLevel.Root)
        // Подпись — название жанра, а не код из метаданных.
        assertThat(browse.categories.map { it.label }).containsExactly("Космическая фантастика")
        assertThat(browse.categories.single().count).isEqualTo(43)
        assertThat(browse.title).isEqualTo("Жанры")
    }

    @Test
    fun `opening a genre filters by its code and shows the authors of that genre`() = runTest(dispatcher) {
        filters.tagFacets.value = listOf(
            FacetItem(id = "sf_space", label = "Космическая фантастика", count = 43),
        )
        val viewModel = viewModel()
        viewModel.onSetBrowse(BrowseMode.GENRES)
        advanceUntilIdle()
        // Дальше фасет отвечает уже по запросу с жанром: авторы этого жанра.
        filters.authorFacets.value = listOf(
            FacetItem(id = "Лем Станислав", label = "Лем Станислав", count = 2),
        )

        viewModel.onOpenCategory(viewModel.uiState.value.browse.categories.single())
        advanceUntilIdle()

        // В запрос ушёл именно код жанра: по нему фильтрует SQL.
        assertThat(books.pageQueries.last().bookQuery.tags).containsExactly("sf_space")
        val browse = viewModel.uiState.value.browse
        assertThat(browse.level).isEqualTo(BrowseLevel.Genre(code = "sf_space", label = "Космическая фантастика"))
        assertThat(browse.categories.map { it.label }).containsExactly("Лем Станислав")
        assertThat(browse.title).isEqualTo("Жанр: Космическая фантастика")
        assertThat(browse.canGoBack).isTrue()
    }

    @Test
    fun `opening an author keeps the genre and asks only that genre's series`() = runTest(dispatcher) {
        filters.tagFacets.value = listOf(
            FacetItem(id = "sf_space", label = "Космическая фантастика", count = 43),
        )
        val viewModel = viewModel()
        viewModel.onSetBrowse(BrowseMode.GENRES)
        advanceUntilIdle()
        filters.authorFacets.value = listOf(
            FacetItem(id = "Лем Станислав", label = "Лем Станислав", count = 2),
        )
        viewModel.onOpenCategory(viewModel.uiState.value.browse.categories.single())
        advanceUntilIdle()


        viewModel.onOpenCategory(viewModel.uiState.value.browse.categories.single())
        advanceUntilIdle()

        val browse = viewModel.uiState.value.browse
        assertThat(browse.level).isEqualTo(BrowseLevel.Author("Лем Станислав"))
        // Жанр не потерялся при заходе в автора: иначе «назад» и книги показали бы весь жанр целиком.
        assertThat(books.pageQueries.last().bookQuery.tags).containsExactly("sf_space")
        assertThat(books.pageQueries.last().bookQuery.authorNames).containsExactly("Лем Станислав")
        // Серии спрошены вместе с кодом жанра — это и есть выбранное поведение.
        assertThat(filters.authorSeriesQueries.last()).isEqualTo("Лем Станислав" to "sf_space")
        assertThat(browse.title).isEqualTo("Автор: Лем Станислав")
    }

    @Test
    fun `back from an author returns to the genre, then to the genre list, then leaves browse`() =
        runTest(dispatcher) {
            filters.tagFacets.value = listOf(
                FacetItem(id = "sf_space", label = "Космическая фантастика", count = 43),
            )
            val viewModel = viewModel()
            viewModel.onSetBrowse(BrowseMode.GENRES)
            advanceUntilIdle()
            filters.authorFacets.value = listOf(
                FacetItem(id = "Лем Станислав", label = "Лем Станислав", count = 2),
            )
            viewModel.onOpenCategory(viewModel.uiState.value.browse.categories.single())
            advanceUntilIdle()
            viewModel.onOpenCategory(viewModel.uiState.value.browse.categories.single())
            advanceUntilIdle()

            viewModel.onBrowseBack()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.browse.level)
                .isEqualTo(BrowseLevel.Genre(code = "sf_space", label = "Космическая фантастика"))

            viewModel.onBrowseBack()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.browse.level).isEqualTo(BrowseLevel.Root)

            viewModel.onBrowseBack()
            advanceUntilIdle()

            // Из корня «назад» выключает обзор, а не оставляет пустой экран категорий.
            assertThat(viewModel.uiState.value.browse.mode).isEqualTo(BrowseMode.OFF)
        }

    @Test
    fun `leaving a genre drops its filter so the authors chip shows every author`() =
        runTest(dispatcher) {
            filters.tagFacets.value = listOf(
                FacetItem(id = "sf_space", label = "Космическая фантастика", count = 43),
            )
            val viewModel = viewModel()
            viewModel.onSetBrowse(BrowseMode.GENRES)
            advanceUntilIdle()
            viewModel.onOpenCategory(viewModel.uiState.value.browse.categories.single())
            advanceUntilIdle()

            // «Назад» на корень жанров, затем кнопка «Авторы»: жанр — часть пути обзора, а не
            // постоянного запроса. Дефект Александра (29.09.2026): после жанрового обзора «Авторы»
            // показывали только авторов этого жанра.
            viewModel.onBrowseBack()
            advanceUntilIdle()
            viewModel.onSetBrowse(BrowseMode.AUTHORS)
            advanceUntilIdle()

            assertThat(filters.observedQueries.last().tags).isEmpty()
            assertThat(books.pageQueries.last().bookQuery.tags).isEmpty()
        }

    @Test
    fun `a genre chosen on the filters screen survives switching the view`() = runTest(dispatcher) {
        val selection = FilterSelection()
        val viewModel = viewModel(selection)
        selection.apply(BookQuery(tags = setOf("sf_space")))
        advanceUntilIdle()

        viewModel.onSetBrowse(BrowseMode.AUTHORS)
        advanceUntilIdle()

        // Фильтр с экрана фильтров — выбор человека по всей библиотеке, его обзор не снимает.
        assertThat(filters.observedQueries.last().tags).containsExactly("sf_space")
    }

    private fun viewModel(selection: FilterSelection = FilterSelection()) = LibraryViewModel(
        bookRepository = books,
        settingsRepository = StaticSettingsRepository(),
        filterSelection = selection,
        browseRequest = LibraryBrowseRequest(),
        filterRepository = filters,
    )

    /** Настройки в этих проверках не участвуют: полка берёт из них только вид по умолчанию. */
    private class StaticSettingsRepository : SettingsRepository {

        override val settings: Flow<AppSettings> = MutableStateFlow(AppSettings())

        override suspend fun current(): AppSettings = AppSettings()

        override suspend fun update(transform: (AppSettings) -> AppSettings) = Unit

        override suspend fun resetToDefaults() = Unit
    }
}
