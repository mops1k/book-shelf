package io.github.bookshelf.feature.collections

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.bookshelf.core.data.model.Book
import io.github.bookshelf.core.data.model.CollectionType
import io.github.bookshelf.core.data.model.ReadingStatus
import io.github.bookshelf.core.data.repository.CollectionRepository
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
import kotlin.math.roundToInt

/** Книга в списке полки (UI-модель). */
data class CollectionItemUi(
    /** `books.id` — ключ навигации. */
    val id: String,
    /** Название книги. */
    val title: String,
    /** Авторы одной строкой; если пусто — показывается расширение. */
    val authors: String,
    /** Формат файла. */
    val extension: String,
    /** Путь к обложке в локальном кэше; `null` — плитка рисует заглушку. */
    val coverPath: String? = null,
    /** Статус чтения — бейджем внизу обложки, как на главной полке. */
    val readingStatus: ReadingStatus = ReadingStatus.UNREAD,
    /** Прогресс чтения 0..100; `null` — читалка его не отдаёт. */
    val progressPercent: Int? = null,
)

/**
 * Книга домена в элемент полки.
 *
 * Отдельной функцией, а не кодом внутри `combine`: это единственное преобразование данных
 * в экране содержимого полки, и его поведение (пустые авторы, отсутствующая обложка)
 * проверяется тестом, а не глазами на устройстве.
 */
internal fun Book.toCollectionItemUi(): CollectionItemUi = CollectionItemUi(
    id = id,
    title = title,
    authors = authorsText,
    extension = extension,
    coverPath = coverPath,
    readingStatus = readingStatus,
    // Проценты приводит к целому тот же, кто собирает карточку полки: читалка отдаёт дробь.
    progressPercent = progressPercent?.let { percent ->
        percent.roundToInt().coerceIn(MIN_PROGRESS_PERCENT, MAX_PROGRESS_PERCENT)
    },
)

/** Границы прогресса чтения: читалка может отдать и −1, и 150. */
private const val MIN_PROGRESS_PERCENT = 0
private const val MAX_PROGRESS_PERCENT = 100

/** Состояние экрана содержимого полки. */
data class CollectionContentUiState(
    /** Название полки — в заголовке экрана. */
    val name: String = "",
    /** «Ручная полка» или «Умная полка · правила». */
    val subtitle: String? = null,
    /** Книги полки. */
    val books: List<CollectionItemUi> = emptyList(),
    /** Идёт загрузка. */
    val isLoading: Boolean = false,
    /** Умная ли полка: от этого зависит пустое состояние. */
    val isSmart: Boolean = false,
    /** Текст ошибки. */
    val error: String? = null,
)

/**
 * ViewModel содержимого полки.
 *
 * Список приходит из [CollectionRepository.observeBooks], а название и тип — из общего
 * списка полок: отдельного запроса «одна полка» в репозитории нет, и заводить его ради
 * заголовка незачем — список всё равно читается целиком.
 */
@HiltViewModel
class CollectionContentViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val collectionRepository: CollectionRepository,
) : ViewModel() {

    private val collectionId: String =
        savedStateHandle.get<String>(CollectionContentRoute.ARG_COLLECTION_ID).orEmpty()

    private val _uiState = MutableStateFlow(CollectionContentUiState())

    /** Состояние экрана для `collectAsStateWithLifecycle`. */
    val uiState: StateFlow<CollectionContentUiState> = _uiState.asStateFlow()

    private var booksJob: Job? = null

    init {
        observeContent()
    }

    /** Перезагрузить содержимое: подписка на книги пересоздаётся целиком. */
    fun onRefresh() {
        observeContent()
    }

    @Suppress("TooGenericExceptionCaught")
    private fun observeContent() {
        booksJob?.cancel()
        booksJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                combine(
                    collectionRepository.observeCollections(),
                    collectionRepository.observeBooks(collectionId),
                ) { collections, books ->
                    val collection = collections.firstOrNull { it.id == collectionId }
                    val isSmart = collection?.type == CollectionType.SMART
                    CollectionContentUiState(
                        name = collection?.name.orEmpty(),
                        subtitle = when {
                            collection == null -> null
                            isSmart -> "Умная полка"
                            else -> "Ручная полка"
                        },
                        books = books.map { book -> book.toCollectionItemUi() },
                        isLoading = false,
                        isSmart = isSmart,
                        error = null,
                    )
                }.collect { state -> _uiState.value = state }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = error.message ?: "Не удалось загрузить полку",
                    )
                }
            }
        }
    }
}
