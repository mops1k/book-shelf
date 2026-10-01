package io.github.bookshelf.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.bookshelf.core.common.error.errorOrNull
import io.github.bookshelf.core.common.error.getOrNull
import io.github.bookshelf.core.data.repository.BookRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Копия книги в списке «Копии книг на устройстве».
 *
 * @param copyDocumentUri адрес файла: по нему копия удаляется.
 * @param title название книги, которую копия повторяет.
 * @param sizeLabel размер файла словами (ресурс + число, правило локализации).
 */
data class BookCopyUi(
    val copyDocumentUri: String,
    val title: String,
    val sizeLabel: SettingsText,
)

/** Состояние блока «Копии книг на устройстве». */
data class BookCopiesUiState(
    val copies: List<BookCopyUi> = emptyList(),
    /** Размер всех копий словами: единица — в ресурсах, число считает `freedBytesText`. */
    val totalLabel: SettingsText? = null,
    val isWorking: Boolean = false,
    val isConfirming: Boolean = false,
    val message: SettingsText? = null,
    val error: SettingsText? = null,
) {
    /** Ничего лишнего на диске нет: блок не занимает внимания. */
    val isEmpty: Boolean get() = copies.isEmpty()
}

/**
 * Список копий, созданных не нами (в первую очередь читалкой), и их удаление.
 *
 * Отдельная ViewModel, а не методы в [SettingsViewModel]: там уже 29 методов при пороге detekt 30.
 *
 * Почему копии вообще нужны на экране: файл занимает место и лежит внутри библиотеки, а книгой не
 * считается (дефект 01.10.2026 — копия читалки появлялась в полке второй книгой). Скрыть её молча и
 * никогда не удалять значило бы копить мусор; удалять самим — терять книгу читалки. Поэтому решение
 * принимает человек, а экран показывает, сколько места занято.
 */
@HiltViewModel
class BookCopiesViewModel @Inject constructor(
    private val books: BookRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookCopiesUiState())

    /** Состояние блока «Копии книг на устройстве». */
    val uiState: StateFlow<BookCopiesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            books.copies().collect { found ->
                _uiState.update { state ->
                    state.copy(
                        copies = found.map { copy ->
                            BookCopyUi(
                                copyDocumentUri = copy.copyDocumentUri,
                                title = copy.bookTitle,
                                sizeLabel = freedBytesText(copy.sizeBytes),
                            )
                        },
                        totalLabel = found.sumOf { copy -> copy.sizeBytes }
                            .takeIf { bytes -> bytes > 0L }
                            ?.let(::freedBytesText),
                    )
                }
            }
        }
    }

    /** Спросить подтверждение: удаление файла необратимо, и без вопроса оно было бы сюрпризом. */
    fun onAskRemove() {
        if (_uiState.value.isWorking || _uiState.value.isEmpty) return
        _uiState.update { it.copy(isConfirming = true, message = null, error = null) }
    }

    /** Отменить подтверждение. */
    fun onCancelRemove() {
        _uiState.update { it.copy(isConfirming = false) }
    }

    /** Удалить файлы копий. Отказ провайдера не мешает остальным и остаётся видимым для повтора. */
    fun onConfirmRemove() {
        val state = _uiState.value
        if (state.isWorking || state.isEmpty) return
        _uiState.update { it.copy(isWorking = true, isConfirming = false, message = null, error = null) }
        viewModelScope.launch {
            val result = books.removeCopies(state.copies.map { copy -> copy.copyDocumentUri })
            val error = result.errorOrNull()
            if (error != null) {
                _uiState.update {
                    it.copy(isWorking = false, error = SettingsText(R.string.settings_copies_failed))
                }
                return@launch
            }
            val deletion = result.getOrNull()
            val failed = deletion?.failed ?: 0
            _uiState.update {
                it.copy(
                    isWorking = false,
                    message = if (failed > 0) {
                        SettingsText(R.string.settings_copies_partly_removed, deletion?.deleted ?: 0, failed)
                    } else {
                        SettingsText(R.string.settings_copies_removed, deletion?.deleted ?: 0)
                    },
                )
            }
        }
    }
}
