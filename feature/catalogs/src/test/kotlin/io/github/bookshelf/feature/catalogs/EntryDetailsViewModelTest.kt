package io.github.bookshelf.feature.catalogs

import androidx.lifecycle.SavedStateHandle
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
 * Карточка книги в каталоге OPDS.
 *
 * Дефект от 28.09.2026: карточка показывала только автора и формат, хотя фид присылал и описание,
 * и серию, и язык, и год. Причина была в маппинге: `:core:data` выбрасывал эти поля, а ViewModel
 * явно ставила `null`. Здесь проверяется, что метаданные доезжают до состояния экрана и что
 * переходы к автору и серии запрашивают нужные фиды.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EntryDetailsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeOpdsRepository()
    private val request = CatalogFeedRequest()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `card shows everything the feed knows about the book`() = runTest(dispatcher) {
        repository.rootEntries = listOf(
            entry(
                id = "1",
                title = "Кольцо",
                authors = "Бакстер Стивен",
                summary = "Корабль поколений уходит в замкнутый полёт.",
                seriesName = "Ксили",
                seriesUrl = SERIES_FEED_URL,
                authorUrl = AUTHOR_FEED_URL,
                formats = listOf("FB2", "EPUB", "MOBI"),
                expectedSize = 1_500_000L,
            ),
        )

        val viewModel = card()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.summary).isEqualTo("Корабль поколений уходит в замкнутый полёт.")
        assertThat(state.seriesName).isEqualTo("Ксили")
        assertThat(state.authorUrl).isEqualTo(AUTHOR_FEED_URL)
        assertThat(state.seriesUrl).isEqualTo(SERIES_FEED_URL)
        assertThat(state.formatsLabel).isEqualTo("FB2, EPUB, MOBI")
        assertThat(state.sizeLabel).isEqualTo("1,4 МБ")
        assertThat(state.canDownload).isTrue()
    }

    @Test
    fun `card survives a feed without description and series`() = runTest(dispatcher) {
        repository.rootEntries = listOf(entry(id = "1", title = "Черновик", authors = "Лукьяненко"))

        val viewModel = card()
        advanceUntilIdle()

        // Описания нет — экран покажет честную подпись, а не пустоту, и переходов не обещает.
        val state = viewModel.uiState.value
        assertThat(state.summary).isNull()
        assertThat(state.seriesName).isNull()
        assertThat(state.authorUrl).isNull()
        assertThat(state.title).isEqualTo("Черновик")
    }

    @Test
    fun `tapping the series asks the browser for that series feed`() = runTest(dispatcher) {
        repository.rootEntries = listOf(
            entry(
                id = "1",
                title = "Кольцо",
                seriesName = "Ксили",
                seriesUrl = SERIES_FEED_URL,
                authorUrl = AUTHOR_FEED_URL,
            ),
        )
        val viewModel = card()
        advanceUntilIdle()

        viewModel.onOpenSeries()

        assertThat(request.request.value?.feedUrl).isEqualTo(SERIES_FEED_URL)
        // Подпись уровня — название серии: в хлебных крошках браузера видно, куда пришли.
        assertThat(request.request.value?.title).isEqualTo("Ксили")
    }

    @Test
    fun `tapping the author asks the browser for that author feed`() = runTest(dispatcher) {
        repository.rootEntries = listOf(
            entry(id = "1", title = "Кольцо", authors = "Бакстер Стивен", authorUrl = AUTHOR_FEED_URL),
        )
        val viewModel = card()
        advanceUntilIdle()

        viewModel.onOpenAuthor()

        assertThat(request.request.value?.feedUrl).isEqualTo(AUTHOR_FEED_URL)
        assertThat(request.request.value?.title).isEqualTo("Бакстер Стивен")
    }

    @Test
    fun `tapping the author without a feed does nothing`() = runTest(dispatcher) {
        repository.rootEntries = listOf(entry(id = "1", title = "Кольцо", authors = "Бакстер Стивен"))
        val viewModel = card()
        advanceUntilIdle()

        viewModel.onOpenAuthor()

        // Каталог фида автора не объявил: обещать переход, которого не будет, нельзя.
        assertThat(request.request.value).isNull()
    }

    private fun card() = EntryDetailsViewModel(
        savedStateHandle = SavedStateHandle(
            mapOf(
                EntryRoute.ARG_CATALOG_ID to FakeOpdsRepository.CATALOG_ID,
                EntryRoute.ARG_ENTRY_ID to "1",
                EntryRoute.ARG_FEED_URL to "",
            ),
        ),
        opdsRepository = repository,
        sourceRepository = FakeSourceRepository,
        catalogFeedRequest = request,
    )
}
