package io.github.bookshelf.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.bookshelf.core.data.model.Book
import io.github.bookshelf.core.data.repository.SearchRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel поиска по FTS (docs/eink-ux.md §6.5).
 *
 * Поиск и подсказки подписываются на потоки репозитория в [viewModelScope]. Введённый запрос
 * отменяет предыдущие подписки, поэтому результаты старого термина не появляются после нового ввода.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())

    private var suggestionsJob: Job? = null

    private var searchJob: Job? = null

    /** Состояние экрана поиска для `collectAsStateWithLifecycle`. */
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    /** Ввод запроса: подсказки запрашиваются с задержкой, чтобы не обращаться к БД на каждый символ. */
    @Suppress("TooGenericExceptionCaught")
    fun onQueryChange(query: String) {
        searchJob?.cancel()
        suggestionsJob?.cancel()
        _uiState.update {
            it.copy(
                query = query,
                suggestions = emptyList(),
                results = emptyList(),
                total = 0,
                isSearching = false,
                error = null,
            )
        }
        if (query.isBlank()) return

        suggestionsJob = viewModelScope.launch {
            delay(SUGGESTION_DELAY_MS)
            try {
                searchRepository.autocomplete(query.trim(), SUGGESTION_LIMIT).collect { suggestions ->
                    _uiState.update { state ->
                        if (state.query == query) state.copy(suggestions = suggestions) else state
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                _uiState.update { state ->
                    if (state.query == query) {
                        state.copy(error = error.message ?: "Не удалось получить подсказки")
                    } else {
                        state
                    }
                }
            }
        }
    }

    /** Выбор подсказки автодополнения сразу запускает поиск по выбранному термину. */
    fun onSelectSuggestion(suggestion: String) {
        _uiState.update { it.copy(query = suggestion, suggestions = emptyList(), error = null) }
        suggestionsJob?.cancel()
        onSearch()
    }

    /** Запустить поиск по введённому запросу. */
    @Suppress("TooGenericExceptionCaught")
    fun onSearch() {
        val term = _uiState.value.query.trim()
        suggestionsJob?.cancel()
        searchJob?.cancel()
        if (term.isEmpty()) {
            _uiState.update {
                it.copy(
                    suggestions = emptyList(),
                    results = emptyList(),
                    total = 0,
                    isSearching = false,
                    error = null,
                )
            }
            return
        }

        _uiState.update { it.copy(isSearching = true, error = null) }
        searchJob = viewModelScope.launch {
            try {
                searchRepository.search(term, SEARCH_LIMIT).collect { books ->
                    val results = books.map { book -> book.toResult(term) }
                    _uiState.update { state ->
                        if (state.query.trim() == term) {
                            state.copy(
                                results = results,
                                total = results.size,
                                isSearching = false,
                                error = null,
                                recentQueries = state.recentQueries.withRecent(term),
                            )
                        } else {
                            state
                        }
                    }
                }
                _uiState.update { state ->
                    if (state.query.trim() == term) state.copy(isSearching = false) else state
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                _uiState.update { state ->
                    if (state.query.trim() == term) {
                        state.copy(
                            isSearching = false,
                            error = error.message ?: "Не удалось выполнить поиск",
                        )
                    } else {
                        state
                    }
                }
            }
        }
    }

    /** Очистить поле и результаты, сохранив локальную историю запросов. */
    fun onClearQuery() {
        suggestionsJob?.cancel()
        searchJob?.cancel()
        _uiState.update {
            it.copy(
                query = "",
                suggestions = emptyList(),
                results = emptyList(),
                total = 0,
                isSearching = false,
                error = null,
            )
        }
    }

    /** Очистить историю последних запросов, хранящуюся в состоянии ViewModel. */
    fun onClearHistory() {
        _uiState.update { it.copy(recentQueries = emptyList()) }
    }

    private fun Book.toResult(term: String): SearchResultUi = SearchResultUi(
        id = id,
        title = title,
        authors = authorsText.ifBlank { authors.joinToString(", ") },
        snippet = description?.take(SNIPPET_LENGTH),
        // Где нашлось совпадение: FTS этого не сообщает, а в строке результата видно, почему
        // книга попала в выдачу (подпись не выдумывается, если поля её не подтверждают).
        matchedField = matchedFieldLabel(term),
    )

    private fun List<String>.withRecent(term: String): List<String> =
        (listOf(term) + filterNot { it.equals(term, ignoreCase = true) })
            .take(RECENT_QUERY_LIMIT)

    private companion object {
        const val SUGGESTION_DELAY_MS = 200L
        const val SUGGESTION_LIMIT = 10
        const val SEARCH_LIMIT = 50
        const val RECENT_QUERY_LIMIT = 10
        const val SNIPPET_LENGTH = 200
    }
}
