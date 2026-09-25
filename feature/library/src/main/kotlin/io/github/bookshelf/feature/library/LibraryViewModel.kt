package io.github.bookshelf.feature.library

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.bookshelf.core.data.model.Grouping
import io.github.bookshelf.core.data.model.SortOrder
import io.github.bookshelf.core.data.repository.BookRepository
import io.github.bookshelf.core.datastore.SettingsRepository
import io.github.bookshelf.feature.library.model.ViewMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * ViewModel полки (docs/eink-ux.md §6.2, Sprint 4).
 *
 * На этапе каркаса отдаёт только начальное состояние: репозитории внедрены как контракты,
 * но их методы не вызываются. Листание страниц — чистая арифметика [clampPage], остальное
 * требует запроса к БД и появится в Sprint 4.
 *
 * После Sprint 4 появятся действия:
 * * [onRefresh] — `BookRepository.page(query = LibraryQuery(...))` → `PageResult<Book>`;
 * * [onSetSort] / [onSetGrouping] — пересборка `LibraryQuery` и новая страница;
 * * [onClearFilters] — сброс чипов и перезапрос;
 * * [onNextPage] / [onPreviousPage] — `LIMIT/OFFSET` без загрузки всей библиотеки в память.
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())

    /** Состояние полки для `collectAsStateWithLifecycle`. */
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    /** Следующая страница; на последней остаётся на месте. */
    fun onNextPage() {
        val state = _uiState.value
        _uiState.value = state.copy(page = clampPage(state.page + 1, state.total, state.pageSize))
    }

    /** Предыдущая страница; на первой остаётся на месте. */
    fun onPreviousPage() {
        val state = _uiState.value
        _uiState.value = state.copy(page = clampPage(state.page - 1, state.total, state.pageSize))
    }

    /** Переключение режима отображения — чистое UI-состояние, меняется сразу. */
    fun onSetMode(mode: ViewMode) {
        _uiState.value = _uiState.value.copy(mode = mode)
    }

    /** Смена сортировки: требует нового запроса к БД. */
    fun onSetSort(sort: SortOrder) {
        // STUB(Sprint 4): пересобрать LibraryQuery и запросить страницу заново
        TODO("STUB(Sprint 4): bookRepository.page(query.copy(sort = $sort))")
    }

    /** Смена группировки: требует нового запроса к БД. */
    fun onSetGrouping(grouping: Grouping) {
        // STUB(Sprint 4): пересобрать LibraryQuery с новой группировкой
        TODO("STUB(Sprint 4): bookRepository.page(query.copy(grouping = $grouping))")
    }

    /** Сброс активных фильтров над полкой. */
    fun onClearFilters() {
        // STUB(Sprint 4): очистить фильтры и перезапросить первую страницу
        TODO("STUB(Sprint 4): сбросить FilterChipsUi и загрузить страницу заново")
    }

    /** Перезагрузка текущей страницы (pull-to-refresh заменён кнопкой меню). */
    fun onRefresh() {
        // STUB(Sprint 4): перечитать страницу из BookRepository
        TODO("STUB(Sprint 4): bookRepository.page(currentQuery)")
    }
}
