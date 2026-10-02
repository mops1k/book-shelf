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
 * Переходы из карточки книги в фиды автора и серии.
 *
 * Требование Александра 28.09.2026: касание автора должно открыть его книги и серии, касание
 * серии — книги этой серии. Проверяется вся цепочка, потому что карточка и браузер — разные экраны
 * со своими ViewModel: карточка кладёт просьбу, живой браузер её забирает.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CatalogsFeedRequestTest {

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
    fun `author feed request opens that feed in the browser`() = runTest(dispatcher) {
        repository.urlEntries[AUTHOR_FEED_URL] = listOf(entry(id = "20", title = "Ксили"))
        val viewModel = browser()
        viewModel.onOpenCatalog(FakeOpdsRepository.CATALOG_ID)
        advanceUntilIdle()

        request.open(
            FakeOpdsRepository.CATALOG_ID,
            AUTHOR_FEED_URL,
            FeedLabel.Named("Бакстер Стивен"),
        )
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.currentFeed?.entries?.map { it.title })
            .containsExactly("Ксили")
        // Подпись уровня показывает, к кому пришли, а не «Каталог».
        assertThat(viewModel.uiState.value.currentFeed?.breadcrumbs)
            .containsExactly(FeedLabel.Named("Flibusta"), FeedLabel.Named("Бакстер Стивен"))
        // Просьба снята: иначе возврат в браузер снова открывал бы фид автора.
        assertThat(request.request.value).isNull()
    }

    @Test
    fun `back from the author feed returns to the feed where the book was`() = runTest(dispatcher) {
        repository.urlEntries[AUTHOR_FEED_URL] = listOf(entry(id = "20", title = "Ксили"))
        val viewModel = browser()
        viewModel.onOpenCatalog(FakeOpdsRepository.CATALOG_ID)
        advanceUntilIdle()

        request.open(FakeOpdsRepository.CATALOG_ID, AUTHOR_FEED_URL, FeedLabel.Named("Бакстер Стивен"))
        advanceUntilIdle()
        viewModel.onBack()

        // Фид автора — обычный уровень: «Назад» возвращает туда, где была книга.
        assertThat(viewModel.uiState.value.currentFeed?.breadcrumbs)
            .containsExactly(FeedLabel.Named("Flibusta"))
        assertThat(viewModel.uiState.value.currentFeed?.entries?.map { it.title })
            .containsExactly("Черновик", "Дозор")
    }

    @Test
    fun `request for another catalog starts the browser from a clean history`() = runTest(dispatcher) {
        val viewModel = browser()
        viewModel.onOpenCatalog(FakeOpdsRepository.CATALOG_ID)
        advanceUntilIdle()
        repository.urlEntries[AUTHOR_FEED_URL] = listOf(entry(id = "20", title = "Ксили"))

        request.open("другой-каталог", AUTHOR_FEED_URL, FeedLabel.Named("Бакстер Стивен"))
        advanceUntilIdle()

        // История уровней чужого каталога не должна пережить переключение: иначе «Назад» вела бы
        // в разделы каталога, который пользователь уже закрыл.
        viewModel.onBack()
        assertThat(viewModel.uiState.value.mode).isEqualTo(CatalogsMode.LIST)
    }

    @Test
    fun `request is kept until the browser consumes it`() {
        // Карточка может положить просьбу раньше, чем браузер подпишется: терять её нельзя.
        request.open("c1", AUTHOR_FEED_URL, FeedLabel.Named("Бакстер Стивен"))

        assertThat(request.request.value?.feedUrl).isEqualTo(AUTHOR_FEED_URL)
        request.consume()
        assertThat(request.request.value).isNull()
    }

    private fun browser() = CatalogsViewModel(
        opdsRepository = repository,
        sourceRepository = FakeSourceRepository,
        catalogFeedRequest = request,
    )
}
