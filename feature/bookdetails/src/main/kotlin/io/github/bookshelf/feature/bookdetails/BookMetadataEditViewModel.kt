package io.github.bookshelf.feature.bookdetails

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.bookshelf.core.common.error.errorOrNull
import io.github.bookshelf.core.common.error.getOrNull
import io.github.bookshelf.core.data.metadata.MetadataSaveResult
import io.github.bookshelf.core.data.metadata.MetadataWriteFailure
import io.github.bookshelf.core.data.metadata.SaveBookMetadata
import io.github.bookshelf.core.data.model.Book
import io.github.bookshelf.core.data.model.MetadataPatch
import io.github.bookshelf.core.data.repository.BookRepository
import io.github.bookshelf.core.designsystem.text.AppText
import io.github.bookshelf.core.designsystem.text.text
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Состояние экрана правки метаданных.
 *
 * @param writeToFile записать правку ещё и в сам файл книги: по умолчанию включено, потому что
 *   именно ради этого правка и делается (серия должна пережить перенос файла). Выключить можно —
 *   тогда файл пользователя не трогается вовсе.
 * @param message итог сохранения словами: что записано и что не удалось.
 */
data class BookMetadataEditUiState(
    val isLoading: Boolean = true,
    val title: String = "",
    val authors: String = "",
    val series: String = "",
    val seriesIndex: String = "",
    val description: String = "",
    val writeToFile: Boolean = true,
    val isSaving: Boolean = false,
    val message: AppText? = null,
    val error: AppText? = null,
) {

    /** Название обязательно: без него книга потерялась бы в библиотеке. */
    val canSave: Boolean get() = title.isNotBlank() && !isSaving
}

/**
 * Правка метаданных книги: название, авторы, серия, номер в серии, описание.
 *
 * Поля заполняются текущими значениями книги (включая прежние ручные правки — их отдаёт
 * `BookRepository.getById`), а сохраняется только то, что человек изменил: иначе первое же
 * сохранение пометило бы все поля как «правленные вручную» и перекрыло бы метаданные файла.
 */
@HiltViewModel
class BookMetadataEditViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val saveBookMetadata: SaveBookMetadata,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val bookId: String = savedStateHandle.get<String>(BookDetailsRoute.ARG_BOOK_ID).orEmpty()

    /** Значения на момент открытия экрана: по ним видно, что именно изменил человек. */
    private var original: Book? = null

    private val _uiState = MutableStateFlow(BookMetadataEditUiState())
    val uiState: StateFlow<BookMetadataEditUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val book = bookRepository.getById(bookId).getOrNull()
            if (book == null) {
                _uiState.update { state ->
                    state.copy(isLoading = false, error = AppText(R.string.book_error_not_found))
                }
                return@launch
            }
            original = book
            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    title = book.title,
                    authors = book.authorsText,
                    series = book.seriesName.orEmpty(),
                    seriesIndex = book.seriesIndex?.let(::formatIndex).orEmpty(),
                    description = book.description.orEmpty(),
                )
            }
        }
    }

    fun onTitleChange(value: String) = _uiState.update { it.copy(title = value, message = null) }

    fun onAuthorsChange(value: String) = _uiState.update { it.copy(authors = value, message = null) }

    fun onSeriesChange(value: String) = _uiState.update { it.copy(series = value, message = null) }

    fun onSeriesIndexChange(value: String) =
        _uiState.update { it.copy(seriesIndex = value, message = null) }

    fun onDescriptionChange(value: String) =
        _uiState.update { it.copy(description = value, message = null) }

    fun onWriteToFileChange(value: Boolean) = _uiState.update { it.copy(writeToFile = value) }

    /** Сохранить правку; результат показывается на экране, а не закрывает его молча. */
    fun onSave() {
        val state = _uiState.value
        if (!state.canSave) return
        val patch = patchOf(state)
        if (patch == null) {
            _uiState.update { it.copy(message = AppText(R.string.book_edit_nothing_changed)) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, message = null, error = null) }
            val result = saveBookMetadata.save(bookId, patch, state.writeToFile)
            val failure = result.errorOrNull()
            if (failure != null) {
                _uiState.update { current ->
                    current.copy(
                        isSaving = false,
                        error = AppText(R.string.book_error_save_edit, failure.text()),
                    )
                }
                return@launch
            }
            val saved = result.getOrNull() ?: MetadataSaveResult(fileWritten = false)
            original = bookRepository.getById(bookId).getOrNull() ?: original
            _uiState.update { current ->
                current.copy(isSaving = false, message = saved.describe())
            }
        }
    }

    /**
     * Патч только из изменившихся полей; `null` — менять нечего.
     *
     * Пустое поле поверх заполненного означает «убрать значение» (серия, описание, авторы),
     * а пустое поле, которое и было пустым, — «не трогали».
     */
    private fun patchOf(state: BookMetadataEditUiState): MetadataPatch? {
        val book = original
        val title = state.title.trim()
        val authors = state.authors.trim()
        val series = state.series.trim()
        val index = state.seriesIndex.trim().replace(',', '.').toDoubleOrNull()
        val description = state.description.trim()
        val patch = MetadataPatch(
            // Пустая строка в заполненном поле — «убрать значение»: поля заполнены текущими
            // значениями книги, поэтому стереть их можно только осознанно.
            title = title.takeIf { it.isNotEmpty() && it != book?.title },
            authorsText = authors.takeIf { it != book?.authorsText.orEmpty() },
            seriesName = series.takeIf { it != book?.seriesName.orEmpty() },
            seriesIndex = index?.takeIf { it != book?.seriesIndex },
            // Номер убран, а название серии осталось — значит, убираем только номер.
            clearSeriesIndex = index == null && book?.seriesIndex != null,
            description = description.takeIf { it != book?.description.orEmpty() },
        )
        return patch.takeIf { patch.hasChanges }
    }

    private fun formatIndex(index: Double): String =
        if (index % 1.0 == 0.0) index.toLong().toString() else index.toString()

    private val MetadataPatch.hasChanges: Boolean
        get() = title != null || authorsText != null || seriesName != null ||
            seriesIndex != null || description != null || clearSeriesIndex

    /**
     * Итог правки словами.
     *
     * Отказ записи приходит кодом (`MetadataWriteFailure`) и называется по типу ошибки
     * (docs/tdd.md §7): «сохранено в приложении, в файл записать не удалось: нет доступа» —
     * факт и причина, оба переводимые.
     */
    private fun MetadataSaveResult.describe(): AppText = when (val failure = fileFailure) {
        null -> if (fileWritten) {
            AppText(R.string.book_edit_saved_to_file)
        } else {
            AppText(R.string.book_edit_saved_app_only)
        }

        MetadataWriteFailure.BookNotFound -> AppText(R.string.book_edit_saved_not_found)
        is MetadataWriteFailure.WriteFailed -> AppText(
            R.string.book_edit_saved_file_failed_reason,
            failure.error.text(),
        )
    }
}
