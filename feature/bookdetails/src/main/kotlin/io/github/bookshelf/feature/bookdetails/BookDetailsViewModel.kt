package io.github.bookshelf.feature.bookdetails

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.common.error.errorOrNull
import io.github.bookshelf.core.common.error.isFailure
import io.github.bookshelf.core.data.model.Book
import io.github.bookshelf.core.data.model.ReadingStatus
import io.github.bookshelf.core.data.repository.BookRepository
import io.github.bookshelf.core.data.repository.CollectionRepository
import io.github.bookshelf.core.data.repository.ReaderRepository
import io.github.bookshelf.core.data.sync.MetadataScheduler
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel карточки книги (docs/eink-ux.md §6.4).
 *
 * Карточка наблюдает книгу через [BookRepository.observeById], а команды чтения и изменения
 * пользовательских полей выполняются в [viewModelScope]. Ошибки команд и потоков остаются в
 * [BookDetailsUiState.error], чтобы экран не получал исключение из ViewModel.
 */
@HiltViewModel
class BookDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val bookRepository: BookRepository,
    private val readerRepository: ReaderRepository,
    private val metadataScheduler: MetadataScheduler,
    private val collectionRepository: CollectionRepository,
) : ViewModel() {

    private val bookId: String = savedStateHandle.get<String>(BookDetailsRoute.ARG_BOOK_ID).orEmpty()

    private val _uiState = MutableStateFlow(BookDetailsUiState(bookId = bookId))

    init {
        observeBook()
        observeCollections()
    }

    /**
     * Полки для кнопки «В полку».
     *
     * Список нужен уже при открытии карточки, а не после нажатия: показывать пустое состояние
     * и только потом подгружать — значит лишний шаг, на котором пользователь решит, что полок
     * вообще нет.
     */
    private fun observeCollections() {
        viewModelScope.launch {
            collectionRepository.observeCollections().collect { collections ->
                _uiState.update { state ->
                    state.copy(
                        collections = collections.map { collection ->
                            CollectionItemUi(id = collection.id, name = collection.name)
                        },
                    )
                }
            }
        }
    }

    /** Показать список полок для выбора. */
    fun onOpenCollectionPicker() {
        _uiState.update { it.copy(isPickingCollection = true, error = null) }
    }

    /** Закрыть список полок, ничего не меняя. */
    fun onCloseCollectionPicker() {
        _uiState.update { it.copy(isPickingCollection = false) }
    }

    /**
     * Положить книгу на выбранную полку.
     *
     * Список закрывается только после успеха: при ошибке остаёмся в выборе, чтобы можно было
     * попробовать другую полку, не возвращаясь назад.
     */
    fun onAddToCollection(collectionId: String) {
        runBookCommand(
            successMessage = null,
            onSuccess = { _uiState.update { it.copy(isPickingCollection = false) } },
        ) { collectionRepository.addBook(collectionId, bookId) }
    }

    /** Состояние карточки книги для `collectAsStateWithLifecycle`. */
    val uiState: StateFlow<BookDetailsUiState> = _uiState.asStateFlow()

    /** Открыть книгу в читалке по профилю, выбранному репозиторием. */
    fun onRead() {
        runBookCommand { readerRepository.openBook(bookId) }
    }

    /** Переключить избранное и дождаться обновления наблюдаемой книги. */
    fun onToggleFavorite() {
        val favorite = !_uiState.value.isFavorite
        runBookCommand { bookRepository.setFavorite(bookId, favorite) }
    }

    /** Переключить статус «прочитано» и дождаться обновления наблюдаемой книги. */
    fun onToggleRead() {
        val targetStatus = if (_uiState.value.readingStatus == ReadingStatus.READ) {
            ReadingStatus.UNREAD
        } else {
            ReadingStatus.READ
        }
        runBookCommand { bookRepository.markRead(bookId, targetStatus) }
    }

    /** Форма редактирования метаданных ещё не подключена к этому ViewModel. */
    fun onEditMetadata() {
        showUnavailable("Редактирование метаданных пока недоступно")
    }

    /**
     * Перечитывание файла: работа ставится в очередь, разбор делает `MetadataWorker`.
     *
     * Синхронно парсить файл здесь нельзя — экран не должен блокироваться на файле в
     * десятки мегабайт. Поэтому команда возвращается сразу, а результат приезжает в
     * [uiState] через наблюдение за книгой.
     */
    @Suppress("TooGenericExceptionCaught")
    fun onRefreshMetadata() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                metadataScheduler.enqueueMetadata(bookId)
                _uiState.update { it.copy(isLoading = false, error = null) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = error.message ?: "Не удалось поставить книгу на разбор",
                    )
                }
            }
        }
    }

    /** Открытие папки требует Android-контекста, которого нет среди зависимостей карточки. */
    fun onShowInFolder() {
        showUnavailable("Открытие папки пока недоступно")
    }

    @Suppress("TooGenericExceptionCaught")
    private fun observeBook() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                var receivedBook = false
                bookRepository.observeById(bookId).collect { book ->
                    receivedBook = true
                    if (book == null) {
                        _uiState.value = BookDetailsUiState(
                            bookId = bookId,
                            isLoading = false,
                            error = "Книга не найдена",
                        )
                    } else {
                        _uiState.update { state -> state.withBook(book) }
                    }
                }
                if (!receivedBook) {
                    _uiState.update { it.copy(isLoading = false, error = "Книга не найдена") }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = error.message ?: "Не удалось загрузить карточку книги",
                    )
                }
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun runBookCommand(
        successMessage: String? = null,
        onSuccess: () -> Unit = {},
        command: suspend () -> AppResult<Unit>,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val result = command()
                if (result.isFailure) {
                    val error = result.errorOrNull() ?: AppError.Unknown()
                    _uiState.update { it.copy(isLoading = false, error = error.toUserMessage()) }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = successMessage) }
                    onSuccess()
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = error.message ?: "Не удалось выполнить действие с книгой",
                    )
                }
            }
        }
    }

    private fun showUnavailable(message: String) {
        _uiState.update { it.copy(isLoading = false, error = message) }
    }
}

private fun BookDetailsUiState.withBook(book: Book): BookDetailsUiState = copy(
    title = book.title,
    authors = book.authorsText.ifBlank { book.authors.joinToString(", ") },
    series = book.seriesLabel(),
    publishedYear = book.publishedYear,
    language = book.language,
    publisher = book.publisher,
    pageCount = book.pageCount,
    sizeLabel = formatSize(book.sizeBytes),
    addedAtLabel = formatDate(book.addedAt),
    description = book.description,
    coverPath = book.coverPath,
    readingStatus = book.readingStatus,
    isFavorite = book.favorite,
    isLoading = false,
    error = null,
)

private fun Book.seriesLabel(): String? = seriesName?.let { name ->
    seriesIndex?.let { index ->
        val wholeIndex = index.toInt().toDouble()
        val formattedIndex = if (index == wholeIndex) wholeIndex.toInt().toString() else index.toString()
        "$name #$formattedIndex"
    } ?: name
}

private fun formatSize(bytes: Long): String = when {
    bytes < KIBIBYTE -> "$bytes Б"
    bytes < MEBIBYTE -> {
        String.format(Locale.getDefault(), "%.1f КБ", bytes.toDouble() / KIBIBYTE)
    }
    bytes < GIBIBYTE -> {
        String.format(Locale.getDefault(), "%.1f МБ", bytes.toDouble() / MEBIBYTE)
    }
    else -> String.format(Locale.getDefault(), "%.1f ГБ", bytes.toDouble() / GIBIBYTE)
}

private const val KIBIBYTE = 1024L
private const val MEBIBYTE = KIBIBYTE * 1024L
private const val GIBIBYTE = MEBIBYTE * 1024L

private fun formatDate(timestamp: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()).format(Date(timestamp))

private fun AppError.toUserMessage(): String = message ?: when (this) {
    is AppError.NotFound -> "Не найдено: $what"
    is AppError.InvalidArgument -> "Некорректное значение: $what"
    is AppError.PermissionDenied -> "Нет доступа к ресурсу"
    AppError.AccessLost -> "Доступ к источнику потерян"
    is AppError.Io -> "Ошибка чтения или записи данных"
    is AppError.Parse -> "Не удалось разобрать файл ($format)"
    is AppError.Network -> "Ошибка сети"
    is AppError.Unauthorized -> "Требуется авторизация"
    is AppError.Unsupported -> "Операция не поддерживается: $what"
    is AppError.Unknown -> "Неизвестная ошибка"
}
