package io.github.bookshelf.feature.catalogs

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Поиск по каталогу OPDS.
 *
 * Проверяется весь путь, а не наличие поля на экране: с объявленным OpenSearch запрос уходит на
 * сервер подставленным в шаблон, без него — отбор по уже загруженному фиду, и в обоих случаях
 * «Назад» возвращает туда, где пользователь искал.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CatalogsSearchTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeOpdsRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `search goes to the server when the catalog declares opensearch`() = runTest(dispatcher) {
        repository.searchTemplate = SEARCH_TEMPLATE
        val viewModel = viewModel()
        viewModel.onOpenCatalog(CATALOG_ID)
        advanceUntilIdle()

        viewModel.onFeedQueryChange("Лукьяненко")
        viewModel.onSearchInFeed()
        advanceUntilIdle()

        // Запрос уходит на сервер: без этого поиск был бы только по видимой странице фида.
        assertThat(repository.searchCalls).hasSize(1)
        assertThat(repository.searchCalls.single().query).isEqualTo("Лукьяненко")
        assertThat(repository.searchCalls.single().template).isEqualTo(SEARCH_TEMPLATE)
        // Результат — обычный уровень фида с подписью, по которой видно, что это поиск.
        assertThat(viewModel.uiState.value.currentFeed?.breadcrumbs)
            .containsExactly("Flibusta", "Поиск: Лукьяненко")
        assertThat(viewModel.uiState.value.currentFeed?.entries?.map { it.title })
            .containsExactly("Черновик")
        assertThat(viewModel.uiState.value.error).isNull()
    }

    @Test
    fun `back from search results returns to the feed where the search started`() = runTest(dispatcher) {
        repository.searchTemplate = SEARCH_TEMPLATE
        val viewModel = viewModel()
        viewModel.onOpenCatalog(CATALOG_ID)
        advanceUntilIdle()
        viewModel.onFeedQueryChange("Лукьяненко")
        viewModel.onSearchInFeed()
        advanceUntilIdle()

        viewModel.onBack()

        // Результаты поиска не должны быть ловушкой: возврат идёт к прежнему списку без сети.
        assertThat(viewModel.uiState.value.currentFeed?.breadcrumbs).containsExactly("Flibusta")
        assertThat(viewModel.uiState.value.currentFeed?.entries?.map { it.title })
            .containsExactly("Черновик", "Дозор")
        assertThat(viewModel.uiState.value.mode).isEqualTo(CatalogsMode.FEED)
    }

    @Test
    fun `without opensearch the search filters the loaded feed`() = runTest(dispatcher) {
        repository.searchTemplate = null
        val viewModel = viewModel()
        viewModel.onOpenCatalog(CATALOG_ID)
        advanceUntilIdle()

        viewModel.onFeedQueryChange("дозор")
        viewModel.onSearchInFeed()
        advanceUntilIdle()

        // Сети нет вовсе: каталог поиск не объявляет, и обещать «поиск по каталогу» нельзя.
        assertThat(repository.searchCalls).isEmpty()
        assertThat(viewModel.uiState.value.currentFeed?.entries?.map { it.title })
            .containsExactly("Дозор")
        // Подпись честно говорит, что отбор был по загруженному фиду.
        assertThat(viewModel.uiState.value.currentFeed?.breadcrumbs)
            .containsExactly("Flibusta", "Поиск по загруженному фиду: дозор")
    }

    @Test
    fun `local search matches authors too`() = runTest(dispatcher) {
        repository.searchTemplate = null
        repository.rootEntries = listOf(
            entry(id = "1", title = "Ночной дозор", authors = "Сергей Лукьяненко"),
            entry(id = "2", title = "Черновик", authors = "Сергей Лукьяненко"),
            entry(id = "3", title = "Кольцо", authors = "Стивен Бакстер"),
        )
        val viewModel = viewModel()
        viewModel.onOpenCatalog(CATALOG_ID)
        advanceUntilIdle()

        viewModel.onFeedQueryChange("бакстер")
        viewModel.onSearchInFeed()
        advanceUntilIdle()

        // Ищем и по автору: на e-ink набирать «Бакстер» быстрее, чем полное название книги.
        assertThat(viewModel.uiState.value.currentFeed?.entries?.map { it.title })
            .containsExactly("Кольцо")
    }

    @Test
    fun `empty query is reported instead of searching`() = runTest(dispatcher) {
        repository.searchTemplate = SEARCH_TEMPLATE
        val viewModel = viewModel()
        viewModel.onOpenCatalog(CATALOG_ID)
        advanceUntilIdle()

        viewModel.onFeedQueryChange("   ")
        viewModel.onSearchInFeed()
        advanceUntilIdle()

        assertThat(repository.searchCalls).isEmpty()
        assertThat(viewModel.uiState.value.error).contains("введите запрос")
    }

    private fun viewModel() = CatalogsViewModel(
        opdsRepository = repository,
        sourceRepository = FakeSourceRepository,
        catalogFeedRequest = CatalogFeedRequest(),
    )

    private companion object {
        const val CATALOG_ID = FakeOpdsRepository.CATALOG_ID
    }
}
