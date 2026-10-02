package io.github.bookshelf.feature.collections

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.bookshelf.core.data.model.BookQuery
import io.github.bookshelf.core.data.model.LibraryQuery
import io.github.bookshelf.core.data.repository.BookRepository
import io.github.bookshelf.core.data.repository.SourceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Строка книги на полке съёмного носителя. */
data class VolumeBookUi(
    /** `books.id` — ключ навигации в карточку книги. */
    val id: String,
    /** Название книги. */
    val title: String,
    /** Авторы одной строкой; если пусто — формат файла. */
    val authors: String,
)

/**
 * Состояние полки съёмного носителя.
 *
 * Состав — книги одного источника: [title] это имя источника (метка тома), а не переводимый текст.
 */
data class VolumeShelfUiState(
    val title: String = "",
    val books: List<VolumeBookUi> = emptyList(),
    val isLoading: Boolean = true,
)

/**
 * Полка съёмного носителя: что лежит на одном томе.
 *
 * Правило Александра 02.10.2026: любая полка — отдельный экран с кнопкой «назад», а не фильтр
 * основной библиотеки (иначе из полки не вернуться к остальным книгам). Поэтому здесь свой экран и
 * свой запрос по источнику, а глобальный фильтр библиотеки не трогается вовсе.
 */
@HiltViewModel
class VolumeShelfViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val bookRepository: BookRepository,
    private val sourceRepository: SourceRepository,
) : ViewModel() {

    /** Источник тома из маршрута; пустая строка означает «полка без источника». */
    private val sourceId: String =
        savedStateHandle.get<String>(VolumeShelfRoute.ARG_SOURCE_ID).orEmpty()

    private val _uiState = MutableStateFlow(VolumeShelfUiState())

    /** Состояние экрана для `collectAsStateWithLifecycle`. */
    val uiState: StateFlow<VolumeShelfUiState> = _uiState.asStateFlow()

    init {
        observe()
    }

    /** Перечитать состав: подписка создаётся заново (после скана появились книги). */
    fun onRefresh() {
        observe()
    }

    private fun observe() {
        viewModelScope.launch {
            combine(
                sourceRepository.observeSources(),
                bookRepository.observePage(
                    LibraryQuery(bookQuery = BookQuery(sourceIds = setOf(sourceId))),
                ),
            ) { sources, page ->
                VolumeShelfUiState(
                    title = sources.firstOrNull { source -> source.id == sourceId }?.displayName.orEmpty(),
                    books = page.items.map { book ->
                        VolumeBookUi(
                            id = book.id,
                            title = book.title,
                            authors = book.authorsText.ifBlank { book.extension.uppercase() },
                        )
                    },
                    isLoading = false,
                )
            }.collect { state -> _uiState.value = state }
        }
    }
}
