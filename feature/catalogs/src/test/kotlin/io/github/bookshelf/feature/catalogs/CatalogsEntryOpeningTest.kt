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
 * Открытие карточки книги из браузера каталога.
 *
 * Дефект, ради которого это проверяется (сообщение Александра, 08.10.2026): «Новинки → Все новинки
 * → Боевая целительница» — книга видна в списке, а карточка отвечает «Запись больше не входит в
 * этот фид». Причина: список догружает страницы 2, 3…, но карточке отдавался адрес первой страницы,
 * и она перечитывала только её. На живой Флибусте «Боевая целительница» лежит на третьей странице
 * `/opds/new/2/new`, а первой (`/opds/new/0/new`) в ней нет.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CatalogsEntryOpeningTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeOpdsRepository()
    private val loadedEntries = LoadedFeedEntries()
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
    fun `a book from the loaded next page opens its card`() = runTest(dispatcher) {
        val book = entry(id = "2", title = SECOND_PAGE_BOOK)
        repository.rootEntries = listOf(entry(id = "1", title = "Кольцо"))
        repository.rootNextUrl = SECOND_PAGE_URL
        repository.urlEntries[SECOND_PAGE_URL] = listOf(book)

        val browser = CatalogsViewModel(repository, FakeSourceRepository, request, loadedEntries)
        browser.onOpenCatalog(FakeOpdsRepository.CATALOG_ID)
        advanceUntilIdle()
        browser.onLoadNextPage()
        advanceUntilIdle()

        var openedCatalogId: String? = null
        var openedEntryId: String? = null
        var openedFeedUrl: String? = null
        browser.onOpenEntry("2") { catalogId, entryId, feedUrl ->
            openedCatalogId = catalogId
            openedEntryId = entryId
            openedFeedUrl = feedUrl
        }
        advanceUntilIdle()

        // Карточка должна перечитывать ту страницу, откуда запись пришла, а не первую страницу уровня.
        assertThat(openedFeedUrl).isEqualTo(SECOND_PAGE_URL)

        val card = EntryDetailsViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(
                    EntryRoute.ARG_CATALOG_ID to openedCatalogId.orEmpty(),
                    EntryRoute.ARG_ENTRY_ID to openedEntryId.orEmpty(),
                    EntryRoute.ARG_FEED_URL to openedFeedUrl.orEmpty(),
                ),
            ),
            opdsRepository = repository,
            sourceRepository = FakeSourceRepository,
            catalogFeedRequest = request,
            loadedEntries = loadedEntries,
        )
        advanceUntilIdle()

        // То самое сообщение пользователя: книга открывалась как «больше не входит в этот фид».
        assertThat(card.uiState.value.error).isNull()
        assertThat(card.uiState.value.title).isEqualTo(SECOND_PAGE_BOOK)
        assertThat(card.uiState.value.canDownload).isTrue()
    }

    private companion object {
        const val SECOND_PAGE_BOOK = "Боевая целительница в Академии. Книга 1"
    }
}
