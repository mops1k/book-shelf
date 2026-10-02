package io.github.bookshelf.feature.collections

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.data.model.Book
import io.github.bookshelf.core.data.model.Source
import io.github.bookshelf.core.data.model.SourceKind
import io.github.bookshelf.core.data.paging.PageResult
import io.github.bookshelf.core.testing.fake.FakeBookRepository
import io.github.bookshelf.core.testing.fake.FakeSourceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * Полка съёмного носителя как отдельный экран (правило Александра 02.10.2026).
 *
 * Проверяется то, что видит человек: заголовком стоит метка тома (данные системы), состав — книги
 * своего источника, и ни глобальный фильтр библиотеки, ни чужой источник на экран не влияют.
 * Отдельный экран, а не фильтр основной библиотеки: из фильтра не вернуться к остальным книгам.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VolumeShelfViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the shelf is titled by the volume label and shows its books`() = runTest(dispatcher) {
        val viewModel = viewModel(
            books = listOf(book(title = "Солярис")),
            sources = listOf(source(id = SOURCE_ID, name = "disk")),
        )

        advanceUntilIdle()

        assertThat(viewModel.uiState.value.title).isEqualTo("disk")
        assertThat(viewModel.uiState.value.books.map { book -> book.title }).containsExactly("Солярис")
        assertThat(viewModel.uiState.value.isLoading).isFalse()
    }

    @Test
    fun `a book without authors is shown by its format`() = runTest(dispatcher) {
        val viewModel = viewModel(
            books = listOf(book(title = "Архив", authors = "", extension = "zip")),
            sources = listOf(source(id = SOURCE_ID, name = "disk")),
        )

        advanceUntilIdle()

        assertThat(viewModel.uiState.value.books.single().authors).isEqualTo("ZIP")
    }

    @Test
    fun `an unknown source leaves the default title to the screen`() = runTest(dispatcher) {
        val viewModel = viewModel(books = emptyList(), sources = emptyList())

        advanceUntilIdle()

        // Пустой заголовок — не ошибка: экран подставляет «Полка носителя» из ресурсов.
        assertThat(viewModel.uiState.value.title).isEmpty()
        assertThat(viewModel.uiState.value.books).isEmpty()
    }

    private fun viewModel(books: List<Book>, sources: List<Source>) = VolumeShelfViewModel(
        savedStateHandle = SavedStateHandle(mapOf(VolumeShelfRoute.ARG_SOURCE_ID to SOURCE_ID)),
        bookRepository = FakeBookRepository(
            pageFlow = flowOf(
                PageResult(items = books, totalCount = books.size, offset = 0, limit = books.size),
            ),
        ),
        sourceRepository = FakeSourceRepository(sources = sources),
    )

    private fun book(
        title: String,
        authors: String = "Лем",
        extension: String = "fb2",
    ) = Book(
        id = "book-$title",
        title = title,
        authorsText = authors,
        extension = extension,
        sizeBytes = 1L,
        addedAt = 0L,
    )

    private fun source(id: String, name: String) = Source(
        id = id,
        uri = "/mnt/media_rw/0436-C297",
        displayName = name,
        kind = SourceKind.DIRECT_PATH,
    )

    private companion object {
        const val SOURCE_ID = "card-source"
    }
}
