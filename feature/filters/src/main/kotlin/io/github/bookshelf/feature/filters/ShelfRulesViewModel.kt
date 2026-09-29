package io.github.bookshelf.feature.filters

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.fold
import io.github.bookshelf.core.common.error.uiText
import io.github.bookshelf.core.data.model.LibraryQuery
import io.github.bookshelf.core.data.model.SmartRules
import io.github.bookshelf.core.data.repository.BookRepository
import io.github.bookshelf.core.data.repository.CollectionRepository
import io.github.bookshelf.core.data.repository.FilterRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import android.database.SQLException

/**
 * ViewModel редактора правил умной полки.
 *
 * Условия — **локальное** состояние экрана, а не общий `FilterSelection` библиотеки: редактор
 * правил не должен менять фильтр полки, из которой его открыли. Фасеты и число найденных книг,
 * наоборот, считает тот же `FilterRepository`, поэтому «правило по жанру» даёт ровно тот же
 * состав, что и фильтр по этому жанру (ADR-008).
 */
@HiltViewModel
class ShelfRulesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val collectionRepository: CollectionRepository,
    private val filterRepository: FilterRepository,
    private val bookRepository: BookRepository,
) : ViewModel() {

    private val collectionId: String =
        savedStateHandle.get<String>(ShelfRulesRoute.ARG_COLLECTION_ID).orEmpty()

    private val _uiState = MutableStateFlow(ShelfRulesUiState())

    /** Состояние редактора для `collectAsStateWithLifecycle`. */
    val uiState: StateFlow<ShelfRulesUiState> = _uiState.asStateFlow()

    private var observationJob: Job? = null

    /** Правила полки прочитаны один раз: подписка на полки не должна затирать правки пользователя. */
    private var rulesLoaded = false

    init {
        observeShelf()
    }

    /** Переключить значение фасета: повторный тап снимает выбор. */
    fun onToggleFacet(kind: FacetKind, facetId: String) {
        val selection = _uiState.value.selected
        val updated = when (kind) {
            FacetKind.AUTHORS -> selection.copy(authors = selection.authors.toggle(facetId))
            FacetKind.SERIES -> selection.copy(series = selection.series.toggle(facetId))
            FacetKind.TAGS -> selection.copy(tags = selection.tags.toggle(facetId))
            FacetKind.FORMATS -> selection.copy(formats = selection.formats.toggle(facetId))
            FacetKind.STATUSES -> selection.copy(statuses = selection.statuses.toggle(facetId))
        }
        _uiState.update { it.copy(selected = updated) }
        observeSelection()
    }

    /** Снять все условия: полка без условий не должна показывать всю библиотеку, поэтому это черновик. */
    fun onReset() {
        _uiState.update { it.copy(selected = FilterSelectionUi()) }
        observeSelection()
    }

    /** Сохранить условия как правила полки. */
    fun onSave() {
        val rules = SmartRules.fromQuery(_uiState.value.selected.toBookQuery())
        if (rules.isEmpty) {
            // Пустые правила означают пустую полку: показать 72 книги вместо подборки — соврать.
            showError(
                "Не удалось сохранить правила",
                AppError.InvalidArgument("rules", "выберите хотя бы одно условие"),
            )
            return
        }
        viewModelScope.launch {
            collectionRepository.updateSmartRules(collectionId = collectionId, rulesJson = rules.toJson())
                .fold(
                    onSuccess = { _uiState.update { it.copy(saved = true, error = null) } },
                    onFailure = { showError("Не удалось сохранить правила", it) },
                )
        }
    }

    /** Перечитать фасеты после ошибки. */
    fun onLoadFacets() {
        observeSelection()
    }

    /**
     * Прочитать полку: имя для заголовка и текущие правила в качестве начального выбора.
     *
     * Правила читаются один раз: дальше список полок может обновиться (например, изменилось число
     * книг), и повторное применение правил стёрло бы то, что пользователь уже выбрал в редакторе.
     */
    private fun observeShelf() {
        viewModelScope.launch {
            collectionRepository.observeCollections().collect { shelves ->
                val shelf = shelves.firstOrNull { collection -> collection.id == collectionId }
                if (shelf == null) {
                    _uiState.update { state ->
                        state.copy(isLoading = false, error = "Полка не найдена")
                    }
                    return@collect
                }
                if (rulesLoaded) {
                    _uiState.update { state -> state.copy(shelfName = shelf.name) }
                    return@collect
                }
                rulesLoaded = true
                val rules = SmartRules.fromJson(shelf.rulesJson)?.takeIf { !it.isEmpty }
                val selected = rules
                    ?.let { filterSelectionFromQuery(it.toBookQuery()) }
                    ?: FilterSelectionUi()
                _uiState.update { state ->
                    state.copy(shelfName = shelf.name, selected = selected, isLoading = true, error = null)
                }
                observeSelection()
            }
        }
    }

    /**
     * Пересчитать фасеты и число книг по черновику условий.
     *
     * Подписка одна: переключение чипа перезапускает её, иначе на каждый тап оставался бы
     * лишний collector со старым запросом.
     */
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
                            it.copy(facets = facets, resultCount = count, isLoading = false, error = null)
                        }
                    }
                _uiState.update { it.copy(isLoading = false) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: SQLException) {
                _uiState.update {
                    it.copy(isLoading = false, error = error.message ?: "Не удалось загрузить условия")
                }
            }
        }
    }

    /** Показать ошибку действия: что не получилось и почему, в терминах [AppError]. */
    private fun showError(action: String, error: AppError) {
        _uiState.update { it.copy(error = "$action: ${error.uiText()}") }
    }

    private companion object {
        /** Для счётчика достаточно одной строки: нужен только `totalCount` страницы. */
        const val COUNT_PAGE_SIZE = 1
    }
}
