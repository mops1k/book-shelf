package io.github.bookshelf.feature.search

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.bookshelf.core.data.repository.SearchRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * ViewModel поиска по FTS (docs/eink-ux.md §6.5, Sprint 4).
 *
 * На этапе каркаса отдаёт только начальное состояние: репозиторий внедрён как контракт
 * и не вызывается. Ввод текста и очистка — чистое UI-состояние, они работают уже сейчас.
 *
 * После Sprint 4 появятся действия:
 * * [onSearch] — `SearchRepository.search(query)` по `books_fts` с подсветкой совпадений;
 * * [onQueryChange] — debounce и префиксный запрос подсказок (`term*`);
 * * [onClearHistory] — очистка истории последних запросов;
 * * фильтры результатов: только непрочитанные, только избранное, формат.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())

    /** Состояние экрана поиска для `collectAsStateWithLifecycle`. */
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    /** Ввод запроса: текст меняется сразу, подсказки появятся в Sprint 4. */
    fun onQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        // STUB(Sprint 4): debounce ~200 мс и префиксный запрос подсказок по books_fts
    }

    /** Выбор подсказки автодополнения. */
    fun onSelectSuggestion(suggestion: String) {
        _uiState.value = _uiState.value.copy(query = suggestion)
        // STUB(Sprint 4): сразу выполнить поиск по выбранной подсказке
    }

    /** Запустить поиск по введённому запросу. */
    fun onSearch() {
        // STUB(Sprint 4): FTS-запрос с unicode61 и подсветкой совпадений
        TODO("STUB(Sprint 4): searchRepository.search(query.trim())")
    }

    /** Очистить поле и результаты — чистое UI-состояние. */
    fun onClearQuery() {
        _uiState.value = _uiState.value.copy(
            query = "",
            suggestions = emptyList(),
            results = emptyList(),
            total = 0,
        )
    }

    /** Очистить историю последних запросов. */
    fun onClearHistory() {
        // STUB(Sprint 4): удалить историю запросов из DataStore
        TODO("STUB(Sprint 4): searchRepository.clearHistory()")
    }
}
