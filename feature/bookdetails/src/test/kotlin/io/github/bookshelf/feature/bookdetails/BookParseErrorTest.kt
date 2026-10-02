package io.github.bookshelf.feature.bookdetails

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.app.FolderOpener
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.common.error.ParseFailure
import io.github.bookshelf.core.data.model.Book
import io.github.bookshelf.core.data.model.ParseState
import io.github.bookshelf.core.data.model.ReaderProfileSummary
import io.github.bookshelf.core.data.repository.LibraryBrowseRequest
import io.github.bookshelf.core.data.repository.ReaderRepository
import io.github.bookshelf.core.data.sync.MetadataScheduler
import io.github.bookshelf.core.designsystem.text.AppText
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
 * Причина отказа разбора в карточке книги (`BookDetailsViewModel`).
 *
 * Дефект, ради которого это проверяется: карточка рисовала баннер «Не удалось разобрать: …», но
 * поле причины никто не заполнял — состояние его объявляло, а маппинг книги не переносил
 * (проверено чтением кода 30.09.2026). Причина теперь есть в доменной модели и доходит до экрана.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BookParseErrorTest {

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
    fun `the card shows why the file was not parsed`() = runTest(dispatcher) {
        repository.byIdFlow = flowOf(
            Book(
                id = "book-1",
                title = "Книга Уткина.fb2",
                authorsText = "",
                extension = "fb2",
                sizeBytes = 13_670_282L,
                addedAt = 1L,
                parseState = ParseState.FAILED,
                parseFailure = ParseFailure.CORRUPT_FILE,
            ),
        )

        val viewModel = viewModel()
        advanceUntilIdle()

        // Карточка показывает слова по коду причины, а не текст парсера (docs/tdd.md §8).
        assertThat(viewModel.uiState.value.parseFailure)
            .isEqualTo(
                AppText(io.github.bookshelf.core.designsystem.R.string.parse_failure_corrupt_file),
            )
    }

    @Test
    fun `a book parsed fine shows no reason`() = runTest(dispatcher) {
        repository.byIdFlow = flowOf(
            Book(
                id = "book-1",
                title = "Обычная книга",
                authorsText = "",
                extension = "fb2",
                sizeBytes = 1_024L,
                addedAt = 1L,
                parseState = ParseState.OK,
            ),
        )

        val viewModel = viewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.parseFailure).isNull()
    }

    private fun viewModel() = BookDetailsViewModel(
        savedStateHandle = SavedStateHandle(mapOf(BookDetailsRoute.ARG_BOOK_ID to "book-1")),
        bookRepository = repository,
        readerRepository = NoReaders,
        metadataScheduler = NoScheduler,
        collectionRepository = FakeCollectionRepository(),
        browseRequest = LibraryBrowseRequest(),
        sourceRepository = FakeSourceRepository(),
        folderOpener = FolderOpener { AppResult.success(Unit) },
    )

    /** Читалки в этой проверке не открываются. */
    private object NoReaders : ReaderRepository {
        override fun installedReaders(): Flow<List<ReaderProfileSummary>> = flowOf(emptyList())

        override fun defaultForFormat(format: String): Flow<String?> = flowOf(null)

        override fun readerDefaults(): Flow<Map<String, String>> = flowOf(emptyMap())

        override suspend fun setDefault(format: String, profileId: String?): AppResult<Unit> =
            AppResult.success(Unit)

        override suspend fun openBook(bookId: String, profileId: String?): AppResult<Unit> =
            AppResult.success(Unit)
    }

    /** Разбор метаданных в этой проверке не запускается. */
    private object NoScheduler : MetadataScheduler {
        override suspend fun enqueueMetadata(bookId: String, force: Boolean, refreshCover: Boolean) = Unit
    }
}
