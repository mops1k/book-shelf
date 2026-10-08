package io.github.bookshelf.feature.bookdetails

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.app.FolderOpener
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.data.model.Book
import io.github.bookshelf.core.data.repository.DeletionResult
import io.github.bookshelf.core.data.repository.LibraryBrowseRequest
import io.github.bookshelf.core.data.sync.MetadataScheduler
import io.github.bookshelf.core.testing.fake.FakeBookRepository
import io.github.bookshelf.core.testing.fake.FakeCollectionRepository
import io.github.bookshelf.core.testing.fake.FakeReaderRepository
import io.github.bookshelf.core.testing.fake.FakeSourceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
 * Карточка книги и удаление файла с устройством («E-ink UX» §6.4).
 *
 * ДЕФЕКТ (Александр, 02.10.2026): «после удаления книги экран остаётся на карточке книги,
 * которой уже нет». Причина была в гонке двух сигналов: ViewModel выставляла `isDeleted = true`,
 * а подписка на книгу тут же отдавала `null` (строка удалена) и ЗАМЕНЯЛА состояние на «книга
 * не найдена» — признак удаления пропадал, и экран не уходил назад. Проверяем именно это:
 * удаление обязано переживать исчезновение книги из базы.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BookDetailsViewModelTest {

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
    fun `a deleted card closes even though the book flow reports it is gone`() = runTest(dispatcher) {
        val books = MutableStateFlow<Book?>(book())
        val repository = FakeBookRepository(byIdFlow = books)
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.onDeletePermanently()
        // Строка книги стёрта из базы: подписка отдаёт null. Это не повод вернуть карточку
        // и не повод показать «книга не найдена» — книгу удалил сам человек.
        books.value = null
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isDeleted).isTrue()
        assertThat(viewModel.uiState.value.error).isNull()
        assertThat(repository.deletedBatches).containsExactly(listOf(BOOK_ID))
    }

    @Test
    fun `a refused deletion keeps the card and explains the reason`() = runTest(dispatcher) {
        val repository = FakeBookRepository(byIdFlow = MutableStateFlow(book()))
        repository.deletionResult = DeletionResult(deleted = 0, failed = 1)
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.onDeletePermanently()
        advanceUntilIdle()

        // Файл удалить не дали: книга на месте, поэтому карточка остаётся и говорит почему.
        assertThat(viewModel.uiState.value.isDeleted).isFalse()
        assertThat(viewModel.uiState.value.error).isNotNull()
        assertThat(viewModel.uiState.value.isDeleting).isFalse()
    }

    @Test
    fun `a middle book of the series shows both neighbours`() = runTest(dispatcher) {
        val repository = FakeBookRepository(
            byIdFlow = MutableStateFlow(book(seriesName = "Ксили", seriesIndex = 2.0)),
        )
        repository.seriesFlow = flowOf(series)
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        // Требование Александра 08.10.2026: на второй книге серии из трёх видны обе стрелки.
        assertThat(viewModel.uiState.value.previousInSeries?.bookId).isEqualTo("s1")
        assertThat(viewModel.uiState.value.previousInSeries?.title).isEqualTo("Ксили. Книга 1")
        assertThat(viewModel.uiState.value.nextInSeries?.bookId).isEqualTo("s3")
    }

    @Test
    fun `a book without a series has no neighbours`() = runTest(dispatcher) {
        val repository = FakeBookRepository(byIdFlow = MutableStateFlow(book()))
        // Даже если в базе есть книги этой серии, без имени серии у книги стрелок быть не должно.
        repository.seriesFlow = flowOf(series)
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.previousInSeries).isNull()
        assertThat(viewModel.uiState.value.nextInSeries).isNull()
    }

    private fun viewModel(repository: FakeBookRepository): BookDetailsViewModel = BookDetailsViewModel(
        savedStateHandle = SavedStateHandle(mapOf(BookDetailsRoute.ARG_BOOK_ID to BOOK_ID)),
        bookRepository = repository,
        readerRepository = FakeReaderRepository(),
        metadataScheduler = RecordingScheduler(),
        collectionRepository = FakeCollectionRepository(),
        browseRequest = LibraryBrowseRequest(),
        sourceRepository = FakeSourceRepository(),
        folderOpener = FolderOpener { AppResult.success(Unit) },
    )

    private fun book(
        id: String = BOOK_ID,
        title: String = "Солярис",
        seriesName: String? = null,
        seriesIndex: Double? = null,
    ) = Book(
        id = id,
        title = title,
        authorsText = "Станислав Лем",
        extension = "fb2",
        sizeBytes = 1_024L,
        addedAt = 1_000L,
        seriesName = seriesName,
        seriesIndex = seriesIndex,
    )

    /** Серия из трёх книг: карточка открыта на средней. */
    private val series = listOf(
        book(id = "s1", title = "Ксили. Книга 1", seriesName = "Ксили", seriesIndex = 1.0),
        book(id = BOOK_ID, title = "Ксили. Книга 2", seriesName = "Ксили", seriesIndex = 2.0),
        book(id = "s3", title = "Ксили. Книга 3", seriesName = "Ксили", seriesIndex = 3.0),
    )

    /** Разбор в этих проверках не запускается: карточка только просит его по команде. */
    private class RecordingScheduler : MetadataScheduler {
        val requested: MutableList<String> = mutableListOf()

        override suspend fun enqueueMetadata(bookId: String, force: Boolean, refreshCover: Boolean) {
            requested += bookId
        }
    }

    private companion object {
        const val BOOK_ID = "doc:one"
    }
}
