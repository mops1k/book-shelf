package io.github.bookshelf.feature.library

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.testing.FakeFeatureFlags
import io.github.bookshelf.core.data.model.Book
import io.github.bookshelf.core.data.model.BookQuery
import io.github.bookshelf.core.data.repository.FilterSelection
import io.github.bookshelf.core.data.repository.LibraryBrowseRequest
import io.github.bookshelf.core.datastore.SettingsRepository
import io.github.bookshelf.core.datastore.model.AppSettings
import io.github.bookshelf.core.testing.fake.FakeBookRepository
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
 * Оконная загрузка полки (`LibraryViewModel`).
 *
 * Полка раньше просила 500 книг одним запросом: на большой библиотеке это одна тяжёлая выдача
 * (плюс запрос прогресса и правок на каждую книгу), а книги дальше пятисотой просто не показывались.
 * Теперь окно — 50 книг, и растёт оно только тогда, когда человек действительно долистал до конца.
 *
 * Проверяется то, что видно снаружи: сколько книг просят у репозитория и сколько их в состоянии.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryWindowTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeBookRepository()
    private val selection = FilterSelection()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the shelf asks for the first window of fifty books`() = runTest(dispatcher) {
        repository.windowedLibrary = library(size = 120)

        val viewModel = viewModel()
        advanceUntilIdle()

        assertThat(repository.pageQueries.first().pageSize).isEqualTo(50)
        assertThat(repository.pageQueries.first().page).isEqualTo(0)
        assertThat(viewModel.uiState.value.books).hasSize(50)
        // Подвал обязан показывать всю библиотеку, а не загруженное окно.
        assertThat(viewModel.uiState.value.total).isEqualTo(120)
    }

    @Test
    fun `loading more appends the next window`() = runTest(dispatcher) {
        repository.windowedLibrary = library(size = 120)
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onLoadMore()
        advanceUntilIdle()

        assertThat(repository.pageQueries.last().pageSize).isEqualTo(100)
        assertThat(viewModel.uiState.value.books).hasSize(100)
    }

    @Test
    fun `loading more never goes past the library`() = runTest(dispatcher) {
        repository.windowedLibrary = library(size = 60)
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onLoadMore()
        advanceUntilIdle()
        val afterFirstGrowth = repository.pageQueries.size
        viewModel.onLoadMore()
        advanceUntilIdle()

        // Все 60 книг уже пришли: нового обращения к базе быть не должно, окно шире библиотеки
        // не растёт — иначе каждый лишний вызов стоил бы полной выдачи.
        assertThat(viewModel.uiState.value.books).hasSize(60)
        assertThat(repository.pageQueries.size).isEqualTo(afterFirstGrowth)
    }

    @Test
    fun `changing the filter starts the window over`() = runTest(dispatcher) {
        repository.windowedLibrary = library(size = 120)
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onLoadMore()
        advanceUntilIdle()

        selection.apply(BookQuery(authorNames = setOf("tolstoy")))
        advanceUntilIdle()

        // Иначе после смены фильтра человек получил бы окно в 100 книг из чужого отбора.
        assertThat(repository.pageQueries.last().pageSize).isEqualTo(50)
    }

    @Test
    fun `an empty shelf does not ask for more`() = runTest(dispatcher) {
        repository.windowedLibrary = emptyList()
        val viewModel = viewModel()
        advanceUntilIdle()
        val before = repository.pageQueries.size

        viewModel.onLoadMore()
        advanceUntilIdle()

        assertThat(repository.pageQueries.size).isEqualTo(before)
    }

    @Test
    fun `a second request is not sent while the next window is on its way`() = runTest(dispatcher) {
        repository.windowedLibrary = library(size = 500)
        val viewModel = viewModel()
        advanceUntilIdle()
        val before = repository.pageQueries.size

        // Палец на e-ink легко попадает дважды, а экран зовёт догрузку на каждом сдвиге: пока
        // следующее окно не пришло, второй запрос — это та же работа ещё раз, только шире.
        viewModel.onLoadMore()
        viewModel.onLoadMore()
        advanceUntilIdle()

        assertThat(repository.pageQueries.size).isEqualTo(before + 1)
        assertThat(repository.pageQueries.last().pageSize).isEqualTo(100)
    }

    private fun viewModel() = LibraryViewModel(
        bookRepository = repository,
        settingsRepository = FakeSettingsRepository,
        filterSelection = selection,
        browseRequest = LibraryBrowseRequest(),
        filterRepository = FakeFilterRepository(),
        featureFlags = FakeFeatureFlags(),
    )

    private fun library(size: Int): List<Book> = (0 until size).map { index ->
        Book(
            id = "book-$index",
            title = "Книга ${index.toString().padStart(3, '0')}",
            authorsText = "Автор",
            extension = "fb2",
            sizeBytes = 1_000L,
            addedAt = 1_000L,
        )
    }

    /** Настройки в проверках окна не участвуют, но ViewModel требует настоящий интерфейс. */
    private object FakeSettingsRepository : SettingsRepository {

        override suspend fun resetToDefaults() = Unit
        override val settings: Flow<AppSettings> = flowOf(AppSettings())
        override suspend fun current(): AppSettings = AppSettings()
        override suspend fun update(transform: (AppSettings) -> AppSettings) = Unit
    }
}
