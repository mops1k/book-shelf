package io.github.bookshelf.feature.bookdetails

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.app.FolderOpener
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.data.model.Book
import io.github.bookshelf.core.data.model.ReaderProfileSummary
import io.github.bookshelf.core.data.repository.BookOptimizationResult
import io.github.bookshelf.core.data.repository.LibraryBrowseRequest
import io.github.bookshelf.core.data.repository.ReaderRepository
import io.github.bookshelf.core.data.sync.MetadataScheduler
import io.github.bookshelf.core.testing.fake.FakeBookRepository
import io.github.bookshelf.core.testing.fake.FakeCollectionRepository
import io.github.bookshelf.core.testing.fake.FakeSourceRepository
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
 * Оптимизация книги из карточки (`BookDetailsViewModel.onOptimize`).
 *
 * Проверяются решения, за которые отвечает экран: кнопка предлагается только для FB2, результат
 * называется человеку словами и байтами (а не «готово»), а отказ читалки показывается причиной.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BookOptimizationActionTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeBookRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `optimization names the saved bytes`() = runTest(dispatcher) {
        givenBook(extension = "fb2")
        repository.optimization = AppResult.success(
            BookOptimizationResult(
                savedBytes = 1_048_576,
                beforeBytes = 4_194_304,
                afterBytes = 3_145_728,
                images = 3,
            ),
        )
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onOptimize()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isOptimizing).isFalse()
        assertThat(state.optimizationMessage).contains("Сэкономлено 1,0 МБ")
        assertThat(state.optimizationMessage).contains("было 4,0 МБ")
        assertThat(repository.optimized).containsExactly(BOOK_ID)
    }

    @Test
    fun `a book that cannot shrink says so instead of reporting a win`() = runTest(dispatcher) {
        givenBook(extension = "fb2")
        repository.optimization = AppResult.success(
            BookOptimizationResult(savedBytes = 0, beforeBytes = 100, afterBytes = 100, images = 0),
        )
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onOptimize()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.optimizationMessage).contains("уже оптимален")
    }

    @Test
    fun `a book checked earlier is reported instead of re-run`() = runTest(dispatcher) {
        givenBook(extension = "fb2")
        repository.optimization = AppResult.success(
            BookOptimizationResult(
                savedBytes = 0,
                beforeBytes = 100,
                afterBytes = 100,
                images = 0,
                alreadyOptimized = true,
            ),
        )
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onOptimize()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.optimizationMessage).contains("Уже оптимизирована")
    }

    @Test
    fun `the button is offered only for fb2 books`() = runTest(dispatcher) {
        givenBook(extension = "epub")
        val viewModel = viewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.canOptimize).isFalse()
    }

    @Test
    fun `an archived fb2 is optimizable too`() = runTest(dispatcher) {
        givenBook(extension = "fb2.zip")
        val viewModel = viewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.canOptimize).isTrue()
    }

    @Test
    fun `a refusal is shown with its reason`() = runTest(dispatcher) {
        givenBook(extension = "fb2")
        repository.optimization = AppResult.failure(
            AppError.PermissionDenied(message = "Открыта читалка — оптимизация подождёт"),
        )
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onOptimize()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.error).contains("Открыта читалка")
        assertThat(state.optimizationMessage).isNull()
    }

    private fun viewModel() = BookDetailsViewModel(
        savedStateHandle = SavedStateHandle(mapOf(BookDetailsRoute.ARG_BOOK_ID to BOOK_ID)),
        bookRepository = repository,
        readerRepository = NoReaders,
        metadataScheduler = NoScheduler,
        collectionRepository = FakeCollectionRepository(),
        browseRequest = LibraryBrowseRequest(),
        sourceRepository = FakeSourceRepository(),
        folderOpener = FolderOpener { AppResult.success(Unit) },
    )

    private fun givenBook(extension: String) {
        repository.byIdFlow = flowOf(
            Book(
                id = BOOK_ID,
                title = "Книга",
                authorsText = "Автор",
                extension = extension,
                sizeBytes = 4_194_304,
                addedAt = 1_000L,
            ),
        )
    }

    /** Читалки в этой проверке не открываются. */
    private object NoReaders : ReaderRepository {
        override fun installedReaders(): Flow<List<ReaderProfileSummary>> = flowOf(emptyList())

        override fun defaultForFormat(format: String): Flow<String?> = flowOf(null)

        override suspend fun setDefault(format: String, profileId: String?): AppResult<Unit> =
            AppResult.success(Unit)

        override suspend fun openBook(bookId: String, profileId: String?): AppResult<Unit> =
            AppResult.success(Unit)
    }

    /** Разбор метаданных в этой проверке не запускается. */
    private object NoScheduler : MetadataScheduler {
        override suspend fun enqueueMetadata(bookId: String, force: Boolean, refreshCover: Boolean) = Unit
    }

    private companion object {
        const val BOOK_ID = "doc:book"
    }
}
