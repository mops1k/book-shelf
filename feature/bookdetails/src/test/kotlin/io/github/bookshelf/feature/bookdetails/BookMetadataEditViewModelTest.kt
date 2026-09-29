package io.github.bookshelf.feature.bookdetails

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.data.metadata.BookMetadataEditor
import io.github.bookshelf.core.data.metadata.SaveBookMetadata
import io.github.bookshelf.core.data.model.Book
import io.github.bookshelf.core.metadata.writer.MetadataEdit
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
 * Экран правки метаданных (`BookMetadataEditViewModel`).
 *
 * Проверяются решения, за которые отвечает только экран: поля заполняются текущими значениями книги,
 * сохраняется лишь то, что человек изменил (иначе первое сохранение перекрыло бы метаданные файла),
 * а результат честно говорит, попала ли правка в сам файл.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BookMetadataEditViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeBookRepository()
    private val editor = FakeMetadataEditor()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `fields are prefilled with the current values`() = runTest(dispatcher) {
        repository.bookById = AppResult.success(book(series = null))
        val viewModel = viewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.title).isEqualTo("Книга")
        assertThat(state.authors).isEqualTo("Автор")
        // У EPUB серии часто нет — поле пустое, и это то, что человек хочет заполнить.
        assertThat(state.series).isEmpty()
        assertThat(state.writeToFile).isTrue()
    }

    @Test
    fun `save sends only the changed fields and reports the file result`() = runTest(dispatcher) {
        repository.bookById = AppResult.success(book(series = null))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onSeriesChange("Новая серия")
        viewModel.onSeriesIndexChange("3")
        viewModel.onSave()
        advanceUntilIdle()

        val patch = repository.metadataPatches.single()
        assertThat(patch.seriesName).isEqualTo("Новая серия")
        assertThat(patch.seriesIndex).isEqualTo(3.0)
        // Название и авторы человек не трогал: они не попадают в правку и остаются из файла.
        assertThat(patch.title).isNull()
        assertThat(patch.authorsText).isNull()
        assertThat(editor.edits.single().seriesName).isEqualTo("Новая серия")
        assertThat(viewModel.uiState.value.message).isEqualTo("Сохранено и записано в файл книги")
    }

    @Test
    fun `emptying a prefilled field clears the value`() = runTest(dispatcher) {
        repository.bookById = AppResult.success(book(series = "Старая серия"))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onSeriesChange("")
        viewModel.onSave()
        advanceUntilIdle()

        // Пустая строка, а не «поле не трогали»: именно так стирается серия и в приложении, и в файле.
        assertThat(repository.metadataPatches.single().seriesName).isEqualTo("")
        assertThat(editor.edits.single().seriesName).isEqualTo("")
    }

    @Test
    fun `emptying only the index clears the number and keeps the series`() = runTest(dispatcher) {
        repository.bookById = AppResult.success(book(series = "Серия", index = 3.0))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onSeriesIndexChange("")
        viewModel.onSave()
        advanceUntilIdle()

        val patch = repository.metadataPatches.single()
        assertThat(patch.clearSeriesIndex).isTrue()
        assertThat(patch.seriesIndex).isNull()
        assertThat(editor.edits.single().clearSeriesIndex).isTrue()
    }

    @Test
    fun `file failure is named on the screen`() = runTest(dispatcher) {
        repository.bookById = AppResult.success(book())
        editor.result = AppResult.failure(AppError.Io(message = "Нет права записи"))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onSeriesChange("Новая серия")
        viewModel.onSave()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.message)
            .isEqualTo("Сохранено в приложении. В файл записать не удалось: Нет права записи")
    }

    @Test
    fun `saving without changes says so instead of writing the file`() = runTest(dispatcher) {
        repository.bookById = AppResult.success(book())
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onSave()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.message).isEqualTo("Ничего не изменилось")
        assertThat(repository.metadataPatches).isEmpty()
        assertThat(editor.edits).isEmpty()
    }

    @Test
    fun `file is not written when the switch is off`() = runTest(dispatcher) {
        repository.bookById = AppResult.success(book())
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onSeriesChange("Новая серия")
        viewModel.onWriteToFileChange(false)
        viewModel.onSave()
        advanceUntilIdle()

        assertThat(editor.edits).isEmpty()
        assertThat(viewModel.uiState.value.message).isEqualTo("Сохранено в приложении")
    }

    private fun viewModel() = BookMetadataEditViewModel(
        bookRepository = repository,
        saveBookMetadata = SaveBookMetadata(repository, editor),
        savedStateHandle = SavedStateHandle(mapOf(BookDetailsRoute.ARG_BOOK_ID to BOOK_ID)),
    )

    private fun book(series: String? = "Серия", index: Double? = null) = Book(
        id = BOOK_ID,
        title = "Книга",
        authorsText = "Автор",
        seriesName = series,
        seriesIndex = index,
        extension = "epub",
        documentUri = "content://test/book.epub",
        sizeBytes = 10L,
        addedAt = 0L,
    )

    private class FakeMetadataEditor : BookMetadataEditor {

        var result: AppResult<Unit> = AppResult.success(Unit)
        val edits = mutableListOf<MetadataEdit>()

        override suspend fun writeToFile(book: Book, edit: MetadataEdit): AppResult<Unit> {
            edits += edit
            return result
        }
    }

    private companion object {
        const val BOOK_ID = "book-1"
    }
}
