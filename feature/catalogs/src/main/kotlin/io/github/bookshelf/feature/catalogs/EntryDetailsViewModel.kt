package io.github.bookshelf.feature.catalogs

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.bookshelf.core.common.error.fold
import io.github.bookshelf.core.data.model.OpdsEntry
import io.github.bookshelf.core.data.repository.OpdsRepository
import io.github.bookshelf.core.data.repository.SourceRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Папка-получатель загрузки (UI-модель). */
data class FolderOptionUi(
    /** `sources.id`. */
    val id: String,
    /** Имя папки. */
    val name: String,
)

/** Состояние карточки записи каталога. */
data class EntryDetailsUiState(
    /** Название; пустое — запись не найдена в фиде. */
    val title: String = "",
    /** Авторы одной строкой. */
    val authors: String = "",
    /** Описание из `<content type="text">` — у Флибусты именно там, а не в `<summary>`. */
    val summary: String? = null,
    /** Адрес обложки из фида. */
    val coverUrl: String? = null,
    /** Форматы: «FB2+ZIP, PDF». */
    val formatsLabel: String = "",
    /** Подпись размера файла. */
    val sizeLabel: String? = null,
    /** Язык записи. */
    val language: String? = null,
    /** Дата обновления записи. */
    val updated: String? = null,
    /** Есть ли прямая ссылка на файл. */
    val canDownload: Boolean = false,
    /** Папки, куда можно сохранить книгу; пусто — папок в приложении нет. */
    val folders: List<FolderOptionUi> = emptyList(),
    /** Выбранная папка; `null` — ещё не выбрана. */
    val selectedFolderId: String? = null,
    /** Идёт загрузка. */
    val isLoading: Boolean = false,
    /** Текст ошибки. */
    val error: String? = null,
)

/**
 * ViewModel карточки записи каталога.
 *
 * Запись берётся повторным чтением фида по id каталога: держать в памяти загруженные записи
 * через несколько экранов навигации нельзя — экран пересоздаётся, а список фида может уже
 * быть другой страницей.
 */
@HiltViewModel
class EntryDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val opdsRepository: OpdsRepository,
    private val sourceRepository: SourceRepository,
) : ViewModel() {

    private val catalogId: String =
        savedStateHandle.get<String>(EntryRoute.ARG_CATALOG_ID).orEmpty()
    private val entryId: String =
        savedStateHandle.get<String>(EntryRoute.ARG_ENTRY_ID).orEmpty()
    private val feedUrl: String =
        savedStateHandle.get<String>(EntryRoute.ARG_FEED_URL).orEmpty()

    private val _uiState = MutableStateFlow(EntryDetailsUiState())

    /** Найденная запись целиком: очередь ставится по объекту, а не по id. */
    private var entry: OpdsEntry? = null

    /** Состояние карточки для `collectAsStateWithLifecycle`. */
    val uiState: StateFlow<EntryDetailsUiState> = _uiState.asStateFlow()

    init {
        onRefresh()
        observeFolders()
    }

    /**
     * Папки-получатели.
     *
     * Загрузке папка нужна обязательно, и без выбора она падала с «выберите папку» — при том,
     * что папка в приложении была ровно одна. Теперь список живой, а при единственной папке
     * она выбирается сразу.
     */
    private fun observeFolders() {
        viewModelScope.launch {
            sourceRepository.observeSources().collect { sources ->
                val enabled = sources.filter { it.enabled }
                _uiState.update { state ->
                    state.copy(
                        folders = enabled.map { source ->
                            FolderOptionUi(id = source.id, name = source.displayName)
                        },
                        selectedFolderId = state.selectedFolderId
                            ?: enabled.firstOrNull()?.id,
                    )
                }
            }
        }
    }

    /** Выбрать папку-получатель. */
    fun onSelectFolder(sourceId: String) {
        _uiState.update { it.copy(selectedFolderId = sourceId, error = null) }
    }

    /** Перечитать фид и найти запись по id. */
    fun onRefresh() {
        if (catalogId.isEmpty()) {
            _uiState.update { it.copy(error = "Каталог не указан") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = opdsRepository.browse(catalogId, feedUrl.takeIf { it.isNotBlank() })
            result.fold(
                onSuccess = { entries ->
                    val found = entries.firstOrNull { it.id == entryId }
                    entry = found
                    _uiState.value = if (found == null) {
                        EntryDetailsUiState(error = "Запись больше не входит в этот фид")
                    } else {
                        // Список папок не теряется: refresh перезаписывает состояние целиком,
                        // а папки приходят отдельной подпиской — иначе карточка показывала
                        // «Папка не выбрана», хотя папка в приложении есть.
                        EntryDetailsUiState(
                            folders = _uiState.value.folders,
                            selectedFolderId = _uiState.value.selectedFolderId,
                            title = found.title,
                            authors = found.authorsText.orEmpty(),
                            summary = null,
                            coverUrl = found.coverUrl,
                            formatsLabel = found.mimeType?.substringAfter('/').orEmpty(),
                            sizeLabel = null,
                            language = null,
                            updated = null,
                            canDownload = !found.acquisitionUrl.isNullOrBlank(),
                            isLoading = false,
                        )
                    }
                },
                onFailure = {
                    _uiState.update { state ->
                        state.copy(isLoading = false, error = "Не удалось загрузить запись: $it")
                    }
                },
            )
        }
    }

    /**
     * Поставить книгу в очередь.
     *
     * Экран закрывает вызывающий код после успеха: возвращаться к списку нужно осмысленно,
     * а не оставаться на карточке уже скачанной книги.
     */
    fun onDownload(onDone: () -> Unit = {}) {
        val target = entry
        val folderId = _uiState.value.selectedFolderId
        if (target == null || !_uiState.value.canDownload) {
            _uiState.update { it.copy(error = "У записи нет прямой ссылки на файл") }
            return
        }
        if (folderId == null) {
            _uiState.update { it.copy(error = "Сначала добавьте папку в приложении") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            opdsRepository.enqueueDownload(
                entry = target,
                catalogId = catalogId,
                targetSourceId = folderId,
            ).fold(
                onSuccess = { onDone() },
                onFailure = {
                    _uiState.update { state ->
                        state.copy(isLoading = false, error = "Не удалось поставить в очередь: $it")
                    }
                },
            )
        }
    }
}
