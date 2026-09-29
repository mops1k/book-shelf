package io.github.bookshelf.feature.settings

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.data.duplicates.DuplicateGroup
import io.github.bookshelf.core.data.duplicates.DuplicateReport
import io.github.bookshelf.core.data.model.Book
import io.github.bookshelf.core.data.model.LibraryQuery
import io.github.bookshelf.core.data.model.MetadataPatch
import io.github.bookshelf.core.data.model.ReadingStatus
import io.github.bookshelf.core.data.paging.PageResult
import io.github.bookshelf.core.data.repository.BookRepository
import io.github.bookshelf.core.data.repository.DeletionResult
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
 * Состояние блока «Дубли книг» (`DuplicatesViewModel.kt`).
 *
 * Проверяются повороты, которые нельзя увидеть в коде слоя данных: подтверждение показывается
 * только когда есть что удалять, отмена забывает найденное (а не оставляет «приговор» на экране),
 * а неудачное удаление честно говорит, сколько файлов осталось.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DuplicatesViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val books = FakeBookRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `no duplicates leaves no confirmation`() = runTest(dispatcher) {
        books.report = DuplicateReport()
        val viewModel = DuplicatesViewModel(books)

        viewModel.onCheck()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isConfirming).isFalse()
        assertThat(state.isConfirmingBroken).isFalse()
        assertThat(state.message).isEqualTo("Дубликатов не найдено")
    }

    @Test
    fun `found duplicates ask for confirmation before deleting anything`() = runTest(dispatcher) {
        books.report = report(duplicates = 1, broken = 1)
        val viewModel = DuplicatesViewModel(books)

        viewModel.onCheck()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        // Ничего не удалено, пока пользователь не подтвердил: проверка лишь предлагает.
        assertThat(books.deletedBatches).isEmpty()
        assertThat(state.isConfirming).isTrue()
        assertThat(state.isConfirmingBroken).isTrue()
        assertThat(state.toRemove).containsExactly("copy")
        assertThat(state.broken).containsExactly("broken")
        assertThat(state.message).contains("к удалению файлов: 1")
        assertThat(state.message).contains("битых файлов (0 байт): 1")
    }

    @Test
    fun `confirmation deletes only the redundant copies`() = runTest(dispatcher) {
        books.report = report(duplicates = 1, broken = 0)
        val viewModel = DuplicatesViewModel(books)
        viewModel.onCheck()
        advanceUntilIdle()

        viewModel.onConfirmDelete()
        advanceUntilIdle()

        assertThat(books.deletedBatches).containsExactly(listOf("copy"))
        val state = viewModel.uiState.value
        assertThat(state.isConfirming).isFalse()
        assertThat(state.toRemove).isEmpty()
        assertThat(state.message).isEqualTo("Удалено файлов: 1")
    }

    @Test
    fun `cancel forgets the found duplicates`() = runTest(dispatcher) {
        books.report = report(duplicates = 1, broken = 0)
        val viewModel = DuplicatesViewModel(books)
        viewModel.onCheck()
        advanceUntilIdle()

        viewModel.onCancelDelete()

        val state = viewModel.uiState.value
        // Список к удалению забыт: иначе отменённое подтверждение вернулось бы при следующем
        // касании строки, и пользователь удалил бы то, от чего только что отказался.
        assertThat(state.toRemove).isEmpty()
        assertThat(state.isConfirming).isFalse()
        assertThat(books.deletedBatches).isEmpty()
    }

    @Test
    fun `failed files are reported instead of silence`() = runTest(dispatcher) {
        books.report = report(duplicates = 1, broken = 0)
        books.deletionResult = DeletionResult(deleted = 1, failed = 2)
        val viewModel = DuplicatesViewModel(books)
        viewModel.onCheck()
        advanceUntilIdle()

        viewModel.onConfirmDelete()
        advanceUntilIdle()

        // Молчать нельзя: файлы остались на диске, и пользователь обязан об этом узнать.
        assertThat(viewModel.uiState.value.message).contains("не удалось удалить: 2")
    }

    private fun report(duplicates: Int, broken: Int): DuplicateReport {
        val keep = book(id = "keep", path = "Автор/Книга.fb2")
        val copies = (1..duplicates).map { index -> book(id = "copy", path = "Книга $index.fb2") }
        val brokenBooks = (1..broken).map { index -> book(id = "broken", path = "Битая $index.fb2", size = 0) }
        return DuplicateReport(
            groups = if (copies.isEmpty()) emptyList() else listOf(DuplicateGroup(keep = keep, remove = copies)),
            broken = brokenBooks,
        )
    }

    private fun book(id: String, path: String, size: Long = 100): Book = Book(
        id = id,
        title = id,
        authorsText = "",
        extension = "fb2",
        documentUri = "content://doc/$id",
        relativePath = path,
        sizeBytes = size,
        addedAt = 1L,
    )

    /** Репозиторий-запись: запоминает, что и в каком порядке у него просили. */
    private class FakeBookRepository(
        var report: DuplicateReport = DuplicateReport(),
        var deletionResult: DeletionResult = DeletionResult(deleted = 1, failed = 0),
    ) : BookRepository {

        val deletedBatches = mutableListOf<List<String>>()

        override fun observePage(query: LibraryQuery): Flow<PageResult<Book>> =
            flowOf(PageResult(items = emptyList(), totalCount = 0, offset = 0, limit = 0))

        override fun observeById(id: String): Flow<Book?> = flowOf(null)

        override suspend fun getById(id: String): AppResult<Book?> = AppResult.success(null)

        override suspend fun updateMetadata(bookId: String, patch: MetadataPatch): AppResult<Unit> =
            AppResult.success(Unit)

        override suspend fun setFavorite(bookId: String, favorite: Boolean): AppResult<Unit> =
            AppResult.success(Unit)

        override suspend fun setRating(bookId: String, rating: Int?): AppResult<Unit> =
            AppResult.success(Unit)

        override suspend fun markRead(bookId: String, status: ReadingStatus): AppResult<Unit> =
            AppResult.success(Unit)

        override suspend fun delete(bookId: String): AppResult<Unit> = AppResult.success(Unit)

        override suspend fun deletePermanently(bookIds: List<String>): AppResult<DeletionResult> {
            deletedBatches += bookIds
            return AppResult.success(deletionResult)
        }

        override suspend fun findDuplicates(): AppResult<DuplicateReport> = AppResult.success(report)

        override suspend fun rescanAll(): AppResult<Unit> = AppResult.success(Unit)
    }
}
