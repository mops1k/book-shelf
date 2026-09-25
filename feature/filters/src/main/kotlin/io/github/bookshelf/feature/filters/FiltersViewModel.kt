package io.github.bookshelf.feature.filters

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.bookshelf.core.data.repository.BookRepository
import io.github.bookshelf.core.data.repository.FilterRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * ViewModel фасетных фильтров (docs/architecture.md §3.5, ADR-008, Sprint 4).
 *
 * На этапе каркаса отдаёт только начальное состояние: репозитории внедрены как контракты
 * и не вызываются. Выбор чипов и сброс — чистые функции из [FilterChipState.kt],
 * они работают уже сейчас.
 *
 * После Sprint 4 появятся действия:
 * * [onLoadFacets] — `FilterRepository.facets(sourceId, selection)` → `FacetSet`;
 * * [onToggleFacet] — пересчёт фасетов и `resultCount` с учётом нового выбора;
 * * [onReset] — сброс выбора и пересчёт;
 * * [onRefreshCount] — отдельный `COUNT(*)` по текущему выбору.
 */
@HiltViewModel
class FiltersViewModel @Inject constructor(
    private val filterRepository: FilterRepository,
    private val bookRepository: BookRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FiltersUiState())

    /** Состояние экрана фильтров для `collectAsStateWithLifecycle`. */
    val uiState: StateFlow<FiltersUiState> = _uiState.asStateFlow()

    /**
     * Переключить значение фасета: повторный тап снимает выбор.
     *
     * Логика «ИЛИ внутри фасета, И между фасетами» живёт в запросе (ADR-008),
     * здесь только само множество выбора.
     */
    fun onToggleFacet(kind: FacetKind, facetId: String) {
        val selection = _uiState.value.selected
        val updated = when (kind) {
            FacetKind.AUTHORS -> selection.copy(authors = selection.authors.toggle(facetId))
            FacetKind.SERIES -> selection.copy(series = selection.series.toggle(facetId))
            FacetKind.TAGS -> selection.copy(tags = selection.tags.toggle(facetId))
            FacetKind.FORMATS -> selection.copy(formats = selection.formats.toggle(facetId))
            FacetKind.STATUSES -> selection.copy(statuses = selection.statuses.toggle(facetId))
        }
        _uiState.value = _uiState.value.copy(selected = updated)
        // STUB(Sprint 4): пересчитать resultCount и числа на чипах с учётом нового выбора
    }

    /** Сбросить все фильтры. */
    fun onReset() {
        _uiState.value = _uiState.value.copy(selected = FilterSelectionUi())
        // STUB(Sprint 4): пересчитать фасеты без активных фильтров
    }

    /** Загрузить фасеты с числами книг. */
    fun onLoadFacets() {
        // STUB(Sprint 4): фасеты считаются SQL-запросами с индексами, не в памяти
        TODO("STUB(Sprint 4): filterRepository.facets(sourceId, selection)")
    }

    /** Пересчитать число книг по текущему выбору. */
    fun onRefreshCount() {
        // STUB(Sprint 4): COUNT(*) по текущему выбору
        TODO("STUB(Sprint 4): bookRepository.count(selection)")
    }
}
