package io.github.bookshelf.feature.filters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.bookshelf.core.common.error.toAppError
import io.github.bookshelf.core.data.model.LibraryQuery
import io.github.bookshelf.core.data.repository.BookRepository
import io.github.bookshelf.core.data.repository.FilterRepository
import io.github.bookshelf.core.data.repository.FilterSelection
import io.github.bookshelf.core.designsystem.text.AppText
import io.github.bookshelf.core.designsystem.text.text
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel фасетных фильтров (docs/architecture.md §3.5, ADR-008).
 *
 * Фасеты и число найденных книг наблюдаются одновременно в [viewModelScope]. Оба запроса
 * используют одну и ту же [BookQuery], поэтому цифры на чипах и итоговый счётчик не расходятся.
 */
@HiltViewModel
class FiltersViewModel @Inject constructor(
    private val filterRepository: FilterRepository,
    private val bookRepository: BookRepository,
    private val filterSelection: FilterSelection,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FiltersUiState())

    private var observationJob: Job? = null

    init {
        observeSelection()
    }

    /** Состояние экрана фильтров для `collectAsStateWithLifecycle`. */
    val uiState: StateFlow<FiltersUiState> = _uiState.asStateFlow()

    /**
     * Переключить значение фасета: повторный тап снимает выбор.
     *
     * Логика «ИЛИ внутри фасета, И между фасетами» живёт в запросе (ADR-008), здесь обновляется
     * только множество выбора, после чего пересчитываются фасеты и результат.
     */
    fun onToggleFacet(kind: FacetKind, facetId: String) {
        val selection = _uiState.value.selected
        val updated = when (kind) {
            FacetKind.AUTHORS -> selection.copy(authors = selection.authors.toggle(facetId))
            FacetKind.SERIES -> selection.copy(series = selection.series.toggle(facetId))
            FacetKind.TAGS -> selection.copy(tags = selection.tags.toggle(facetId))
            FacetKind.FORMATS -> selection.copy(formats = selection.formats.toggle(facetId))
            FacetKind.STATUSES -> selection.copy(statuses = selection.statuses.toggle(facetId))
            FacetKind.SOURCES -> selection.copy(sources = selection.sources.toggle(facetId))
        }
        _uiState.update { it.copy(selected = updated) }
        filterSelection.apply(updated.toBookQuery())
        observeSelection()
    }

    /** Сбросить все фильтры и пересчитать результат без активных условий. */
    fun onReset() {
        _uiState.update { it.copy(selected = FilterSelectionUi()) }
        filterSelection.clear()
        observeSelection()
    }

    /** Загрузить фасеты с числами книг. */
    fun onLoadFacets() {
        observeSelection()
    }

    /** Пересчитать число книг по текущему выбору. */
    fun onRefreshCount() {
        observeSelection()
    }

    init {
        // Экран открывается с тем выбором, который полка уже применяет: иначе после «Назад»
        // чипы сбрасывались бы и показывали бы не те счётчики, что на полке.
        val current = filterSelection.query.value
        if (!current.isEmptyQuery()) {
            _uiState.update { it.copy(selected = filterSelectionFromQuery(current)) }
            observeSelection()
        }
    }

    private fun observeSelection() {
        observationJob?.cancel()
        val query = _uiState.value.selected.toBookQuery()
        observationJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                combine(
                    filterRepository.observeFacets(query),
                    bookRepository.observePage(
                        LibraryQuery(bookQuery = query, pageSize = COUNT_PAGE_SIZE),
                    ),
                ) { facets, page -> facets to page.totalCount }
                    .collect { (facets, count) ->
                        _uiState.update {
                            it.copy(
                                facets = facets,
                                resultCount = count,
                                isLoading = false,
                                error = null,
                            )
                        }
                    }
                _uiState.update { it.copy(isLoading = false) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        // Причина — текст по типу ошибки: `message` нижнего слоя человеку не показывают
                        // (docs/tdd.md §7), иначе в баннер попадал бы технический текст, в том числе английский.
                        error = AppText(R.string.filters_error_load, error.toAppError().text()),
                    )
                }
            }
        }
    }

    private companion object {
        const val COUNT_PAGE_SIZE = 1
    }
}
