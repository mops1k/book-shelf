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
    private val loadedEntries = LoadedFeedEntries()

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
        assertThat(state.sizeLabel).isEqualTo(CatalogsText(R.string.catalogs_size_mb, "1,4"))
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
        assertThat(request.request.value?.title).isEqualTo(FeedLabel.Named("Ксили"))
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
        assertThat(request.request.value?.title).isEqualTo(FeedLabel.Named("Бакстер Стивен"))
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

    @Test
    fun `the card opens the book the list showed even when the feed no longer has it`() =
        runTest(dispatcher) {
            // Дефект от 08.10.2026: «Боевая целительница» лежит на третьей странице фида, а карточка
            // перечитывала первую и отвечала «Запись больше не входит в этот фид», хотя книга
            // показана в списке. То, что отдал список, карточка обязана помнить сама.
            val shown = entry(
                id = "2",
                title = "Боевая целительница в Академии. Книга 1",
                authors = "Иванова Мария",
                formats = listOf("FB2"),
            )
            loadedEntries.remember(
                catalogId = FakeOpdsRepository.CATALOG_ID,
                entries = listOf(shown),
                feedUrl = SECOND_PAGE_URL,
            )
            // Свежий фид книги не содержит: так выглядит ответ сервера, где она ушла на другую страницу.
            repository.rootEntries = listOf(entry(id = "7", title = "Другая книга"))

            val viewModel = card(entryId = "2")
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.title).isEqualTo(shown.title)
            assertThat(viewModel.uiState.value.canDownload).isTrue()
            assertThat(viewModel.uiState.value.error).isNull()
            // За фидом не ходили вовсе: то, что увидел пользователь, уже есть в памяти.
            assertThat(repository.requestedUrls).isEmpty()
        }

    @Test
    fun `a card that is still loading reports loading, not a missing entry`() = runTest(dispatcher) {
        // Фид не отвечает: экран не должен утверждать, что записи нет, пока идёт чтение.
        repository.rootEntries = emptyList()

        val viewModel = card()

        assertThat(viewModel.uiState.value.isLoading).isTrue()
        assertThat(viewModel.uiState.value.error).isNull()
    }

    private fun card(entryId: String = "1", feedUrl: String = "") = EntryDetailsViewModel(
        savedStateHandle = SavedStateHandle(
            mapOf(
                EntryRoute.ARG_CATALOG_ID to FakeOpdsRepository.CATALOG_ID,
                EntryRoute.ARG_ENTRY_ID to entryId,
                EntryRoute.ARG_FEED_URL to feedUrl,
            ),
        ),
        opdsRepository = repository,
        sourceRepository = FakeSourceRepository,
        catalogFeedRequest = request,
        loadedEntries = loadedEntries,
    )
}
