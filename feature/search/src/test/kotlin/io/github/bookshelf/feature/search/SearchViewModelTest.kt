package io.github.bookshelf.feature.search

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.data.model.Book
import io.github.bookshelf.core.data.repository.SearchRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Логика экрана поиска: подсказки с задержкой, поиск по термину, история запросов.
 *
 * До этих тестов у модуля поиска не было ни одной проверки: ошибка в отмене предыдущего запроса
 * показывала бы результаты старого термина после нового ввода, а её на экране видно только
 * глазами и только иногда.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = RecordingSearchRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `search fills results and the counter`() = runTest(dispatcher) {
        repository.books = listOf(book(id = "b1", title = "Солярис"), book(id = "b2", title = "Солярис. Возвращение"))
        val viewModel = SearchViewModel(repository)

        viewModel.onQueryChange("соляр")
        viewModel.onSearch()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.results.map { it.id }).containsExactly("b1", "b2").inOrder()
        assertThat(viewModel.uiState.value.total).isEqualTo(2)
        assertThat(viewModel.uiState.value.isSearching).isFalse()
        assertThat(repository.searchTerms).containsExactly("соляр")
    }

    @Test
    fun `results carry the field where the match was found`() = runTest(dispatcher) {
        repository.books = listOf(book(id = "b1", title = "Солярис", authors = "Станислав Лем"))
        val viewModel = SearchViewModel(repository)

        viewModel.onQueryChange("лем")
        viewModel.onSearch()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.results.single().matchedField).isEqualTo("автор")
    }

    @Test
    fun `suggestions arrive after the debounce and only for the current query`() = runTest(dispatcher) {
        repository.suggestions = listOf("Солярис")
        val viewModel = SearchViewModel(repository)

        viewModel.onQueryChange("сол")
        // До истечения задержки к базе не обращаемся: иначе запрос уходит на каждый символ.
        advanceTimeBy(100)
        assertThat(repository.autocompletePrefixes).isEmpty()

        advanceUntilIdle()

        assertThat(viewModel.uiState.value.suggestions).containsExactly("Солярис")
        assertThat(repository.autocompletePrefixes).containsExactly("сол")
    }

    @Test
    fun `empty query asks the repository nothing and clears the state`() = runTest(dispatcher) {
        val viewModel = SearchViewModel(repository)

        viewModel.onQueryChange("   ")
        viewModel.onSearch()
        advanceUntilIdle()

        assertThat(repository.searchTerms).isEmpty()
        assertThat(viewModel.uiState.value.results).isEmpty()
        assertThat(viewModel.uiState.value.suggestions).isEmpty()
        assertThat(viewModel.uiState.value.total).isEqualTo(0)
    }

    @Test
    fun `history keeps the last query once and can be cleared`() = runTest(dispatcher) {
        val viewModel = SearchViewModel(repository)

        viewModel.onQueryChange("лем")
        viewModel.onSearch()
        advanceUntilIdle()
        viewModel.onQueryChange("фостер")
        viewModel.onSearch()
        advanceUntilIdle()
        viewModel.onQueryChange("лем")
        viewModel.onSearch()
        advanceUntilIdle()

        // Повторный запрос переезжает наверх, а не дублируется.
        assertThat(viewModel.uiState.value.recentQueries).containsExactly("лем", "фостер").inOrder()

        viewModel.onClearHistory()

        assertThat(viewModel.uiState.value.recentQueries).isEmpty()
    }

    @Test
    fun `typing a new query drops the previous results`() = runTest(dispatcher) {
        repository.books = listOf(book(id = "b1", title = "Солярис"))
        val viewModel = SearchViewModel(repository)
        viewModel.onQueryChange("соляр")
        viewModel.onSearch()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.results).hasSize(1)

        viewModel.onQueryChange("флинкс")
        advanceUntilIdle()

        // Пока идёт ввод, старые результаты не должны выглядеть как ответ на новый запрос.
        assertThat(viewModel.uiState.value.results).isEmpty()
        assertThat(viewModel.uiState.value.total).isEqualTo(0)
    }

    private fun book(id: String, title: String, authors: String = "Лем"): Book = Book(
        id = id,
        title = title,
        authorsText = authors,
        extension = "fb2",
        sizeBytes = 1024L,
        addedAt = 1L,
    )

    private class RecordingSearchRepository : SearchRepository {
        var books: List<Book> = emptyList()
        var suggestions: List<String> = emptyList()
        val searchTerms = mutableListOf<String>()
        val autocompletePrefixes = mutableListOf<String>()

        override fun search(term: String, limit: Int): Flow<List<Book>> {
            searchTerms += term
            return flowOf(books)
        }

        override fun autocomplete(prefix: String, limit: Int): Flow<List<String>> {
            autocompletePrefixes += prefix
            return flowOf(suggestions)
        }
    }
}

