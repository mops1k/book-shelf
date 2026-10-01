package io.github.bookshelf.feature.settings

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.data.model.BookCopy
import io.github.bookshelf.core.testing.fake.FakeBookRepository
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
 * Состояние блока «Копии книг на устройстве» (`BookCopiesViewModel.kt`).
 *
 * Проверяются повороты, которые видит человек: список называет книги, а не адреса; удаление
 * спрашивает подтверждение (файл исчезает безвозвратно); частичный отказ честно называет число
 * неудач, а полный — показывает ошибку; когда копий нет, кнопка ничего не делает.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BookCopiesViewModelTest {

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
    fun `copies are named by their book and measured`() = runTest(dispatcher) {
        books.copies = listOf(
            copy(uri = "content://doc/copy-1", title = "Предыстория. Книга 1", size = 514_015L),
        )

        val viewModel = BookCopiesViewModel(books)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.copies.map { it.title }).containsExactly("Предыстория. Книга 1")
        assertThat(state.isEmpty).isFalse()
        // Размер — ресурс с числом: «514 КБ», а не готовая строка в коде.
        assertThat(state.copies.first().sizeLabel).isEqualTo(freedBytesText(514_015L))
        assertThat(state.totalLabel).isEqualTo(freedBytesText(514_015L))
    }

    @Test
    fun `removing asks for confirmation first`() = runTest(dispatcher) {
        books.copies = listOf(copy(uri = "content://doc/copy-1", title = "Книга", size = 1_024L))
        val viewModel = BookCopiesViewModel(books)
        advanceUntilIdle()

        viewModel.onAskRemove()

        assertThat(viewModel.uiState.value.isConfirming).isTrue()
        // До подтверждения ничего не удаляется: файл исчезает безвозвратно.
        assertThat(books.copiesRemoved).isEqualTo(0)
    }

    @Test
    fun `cancelling forgets the confirmation`() = runTest(dispatcher) {
        books.copies = listOf(copy(uri = "content://doc/copy-1", title = "Книга", size = 1_024L))
        val viewModel = BookCopiesViewModel(books)
        advanceUntilIdle()

        viewModel.onAskRemove()
        viewModel.onCancelRemove()

        assertThat(viewModel.uiState.value.isConfirming).isFalse()
        assertThat(books.copiesRemoved).isEqualTo(0)
    }

    @Test
    fun `confirming removes every copy and reports the number`() = runTest(dispatcher) {
        books.copies = listOf(
            copy(uri = "content://doc/copy-1", title = "Первая", size = 1_024L),
            copy(uri = "content://doc/copy-2", title = "Вторая", size = 2_048L),
        )
        val viewModel = BookCopiesViewModel(books)
        advanceUntilIdle()

        viewModel.onConfirmRemove()
        advanceUntilIdle()

        assertThat(books.copiesRemoved).isEqualTo(2)
        assertThat(viewModel.uiState.value.message)
            .isEqualTo(SettingsText(R.string.settings_copies_removed, 2))
        assertThat(viewModel.uiState.value.isWorking).isFalse()
    }

    @Test
    fun `a partly refused removal says how many are left`() = runTest(dispatcher) {
        books.copies = listOf(
            copy(uri = "content://doc/copy-1", title = "Первая", size = 1_024L),
            copy(uri = "content://doc/copy-2", title = "Вторая", size = 2_048L),
        )
        books.copiesFailed = 1
        val viewModel = BookCopiesViewModel(books)
        advanceUntilIdle()

        viewModel.onConfirmRemove()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.message)
            .isEqualTo(SettingsText(R.string.settings_copies_partly_removed, 1, 1))
    }

    @Test
    fun `a refused removal is reported as an error`() = runTest(dispatcher) {
        books.copies = listOf(copy(uri = "content://doc/copy-1", title = "Книга", size = 1_024L))
        books.copiesError = AppError.PermissionDenied(message = "нет доступа к папке")
        val viewModel = BookCopiesViewModel(books)
        advanceUntilIdle()

        viewModel.onConfirmRemove()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.error)
            .isEqualTo(SettingsText(R.string.settings_copies_failed))
    }

    @Test
    fun `with no copies nothing happens`() = runTest(dispatcher) {
        val viewModel = BookCopiesViewModel(books)
        advanceUntilIdle()

        viewModel.onAskRemove()
        viewModel.onConfirmRemove()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isConfirming).isFalse()
        assertThat(books.copiesRemoved).isEqualTo(0)
    }

    private fun copy(uri: String, title: String, size: Long) = BookCopy(
        copyDocumentUri = uri,
        bookId = "doc:book",
        bookTitle = title,
        sizeBytes = size,
    )
}
