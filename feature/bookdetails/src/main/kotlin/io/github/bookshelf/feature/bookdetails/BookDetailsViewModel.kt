package io.github.bookshelf.feature.bookdetails

import android.database.SQLException
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.bookshelf.core.common.app.FolderOpener
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.common.error.errorOrNull
import io.github.bookshelf.core.common.error.getOrNull
import io.github.bookshelf.core.common.error.isFailure
import io.github.bookshelf.core.common.error.toAppError
import io.github.bookshelf.core.data.model.Book
import io.github.bookshelf.core.data.model.ReadingStatus
import io.github.bookshelf.core.data.repository.BookOptimizationResult
import io.github.bookshelf.core.data.repository.BookRepository
import io.github.bookshelf.core.data.repository.CollectionRepository
import io.github.bookshelf.core.data.repository.LibraryBrowseRequest
import io.github.bookshelf.core.data.repository.ReaderRepository
import io.github.bookshelf.core.data.repository.SourceRepository
import io.github.bookshelf.core.data.sync.MetadataScheduler
import io.github.bookshelf.core.designsystem.text.AppText
import io.github.bookshelf.core.designsystem.text.text
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
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
    /** Просьба к полке: показать книги автора или серии (тот же приём, что у фильтров). */
    private val browseRequest: LibraryBrowseRequest,
    /** Папки-источники: имя папки показывается в строке «Путь». */
    private val sourceRepository: SourceRepository,
    /** «Показать в папке»: открыть системный файловый менеджер на папке книги. */
    private val folderOpener: FolderOpener,
) : ViewModel() {

    /**
     * Спросить подтверждение удаления.
     *
     * Удаление необратимо: файл исчезает с устройства, вернуть книгу можно только повторным
     * скачиванием или копированием. Поэтому одно касание к нему не приводит.
     */
    fun onAskDelete() {
        _uiState.update { it.copy(isConfirmingDelete = true, error = null) }
    }

    /** Отказаться от удаления. */
    fun onCancelDelete() {
        _uiState.update { it.copy(isConfirmingDelete = false) }
    }

    /**
     * Удалить книгу вместе с файлом.
     *
     * Файл удаляется первым — это делает слой данных: если провайдер откажет, запись останется,
     * и книга не «воскреснет» следующим сканом. Успешное удаление закрывает карточку: показывать
     * её после исчезновения книги нечего.
     */
    fun onDeletePermanently() {
        if (_uiState.value.isDeleting) return
        _uiState.update { it.copy(isDeleting = true, error = null) }
        viewModelScope.launch {
            val outcome = bookRepository.deletePermanently(listOf(bookId))
            val failure = outcome.errorOrNull()
            val result = outcome.getOrNull()
            when {
                failure != null -> _uiState.update {
                    it.copy(
                        isDeleting = false,
                        isConfirmingDelete = false,
                        error = AppText(R.string.book_error_delete, failure.text()),
                    )
                }

                result != null && result.failed > 0 -> _uiState.update {
                    it.copy(
                        isDeleting = false,
                        isConfirmingDelete = false,
                        error = AppText(R.string.book_error_delete_file_access),
                    )
                }

                else -> {
                    // Книги больше нет: подписка на неё закрывается, состояние остаётся «удалено».
                    bookObservation?.cancel()
                    _uiState.update {
                        it.copy(isDeleting = false, isConfirmingDelete = false, isDeleted = true)
                    }
                }
            }
        }
    }

    private val bookId: String = savedStateHandle.get<String>(BookDetailsRoute.ARG_BOOK_ID).orEmpty()

    private val _uiState = MutableStateFlow(BookDetailsUiState(bookId = bookId))

    init {
        observeBook()
        observeCollections()
    }

    /**
     * Имя папки-источника для книги.
     *
     * Берётся точечно при маппинге, а не отдельной подпиской: подписка на папки могла прийти
     * раньше книги (тогда имя терялось) и не переиздаться после неё. Здесь ответ всегда есть
     * в момент, когда книга уже прочитана.
     *
     * Имя папки — подпись, а не данные: её отсутствие карточка переживает молча.
     */
    @Suppress("SwallowedException")
    private suspend fun sourceNameOf(sourceId: String): String {
        if (sourceId.isEmpty()) return ""
        return try {
            sourceRepository.observeSources().first()
                .firstOrNull { source -> source.id == sourceId }
                ?.displayName
                .orEmpty()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: SQLException) {
            // Ошибку чтения папок показывать нечем: без имени папки карточка остаётся рабочей.
            ""
        }
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
            onSuccess = { _uiState.update { it.copy(isPickingCollection = false) } },
        ) { collectionRepository.addBook(collectionId, bookId) }
    }

    /** Состояние карточки книги для `collectAsStateWithLifecycle`. */
    val uiState: StateFlow<BookDetailsUiState> = _uiState.asStateFlow()

    /** Открыть книгу в читалке по профилю, выбранному репозиторием. */
    /**
     * Открыть книги автора этой книги: на полке включится вид «Авторы» на нём, с его сериями.
     *
     * Просьба уходит на полку, а экран закрывается вызывающим кодом: карточка не открывает второй
     * экран полки, иначе стек навигации вырос бы вдвое на каждый такой переход.
     */
    fun onOpenAuthor() {
        browseRequest.openAuthor(_uiState.value.authors)
    }

    /**
     * Открыть книги серии этой книги.
     *
     * В просьбу уходит имя серии без номера: подпись в карточке («Приключения Флинкса #3») — это
     * формат для чтения, а фильтр серий сравнивает имя, и по подписи не нашлось бы ни одной книги.
     */
    fun onOpenSeries() {
        _uiState.value.seriesName?.let { name -> browseRequest.openSeries(name) }
    }

    fun onRead() {
        runBookCommand { readerRepository.openBook(bookId) }
    }

    /** Переключить избранное и дождаться обновления наблюдаемой книги. */
    fun onToggleFavorite() {
        val favorite = !_uiState.value.isFavorite
        runBookCommand { bookRepository.setFavorite(bookId, favorite) }
    }

    /**
     * Оптимизировать файл книги без потери данных.
     *
     * Работа тяжёлая (файл читается целиком, картинки пересобираются), поэтому она идёт в
     * [viewModelScope], а кнопка на это время блокируется. Успех показывается отдельным
     * сообщением, а не плашкой ошибки: «оптимизировано» и «не удалось» не должны выглядеть одинаково.
     */
    fun onOptimize() {
        if (_uiState.value.isOptimizing) return
        _uiState.update { it.copy(isOptimizing = true, optimizationMessage = null, error = null) }
        viewModelScope.launch {
            try {
                val outcome = bookRepository.optimizeBook(bookId)
                val failure = outcome.errorOrNull()
                val result = outcome.getOrNull()
                _uiState.update { state ->
                    when {
                        failure != null -> state.copy(
                            isOptimizing = false,
                            error = AppText(R.string.book_error_optimize, failure.text()),
                        )

                        result != null -> state.copy(
                            isOptimizing = false,
                            optimizationMessage = optimizationReport(result),
                        )

                        else -> state.copy(isOptimizing = false)
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: SQLException) {
                _uiState.update {
                    it.copy(
                        isOptimizing = false,
                        error = AppText(R.string.book_error_optimize, error.toAppError().text()),
                    )
                }
            }
        }
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

    /**
     * Перечитывание файла: работа ставится в очередь, разбор делает `MetadataWorker`.
     *
     * Синхронно парсить файл здесь нельзя — экран не должен блокироваться на файле в
     * десятки мегабайт. Поэтому команда возвращается сразу, а результат приезжает в
     * [uiState] через наблюдение за книгой.
     */
    fun onRefreshMetadata() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                // «Обновить из файла» — единственная команда, которая по явной просьбе берёт
                // обложку заново: обычный разбор кэш переиспользует. Поэтому здесь и `force`
                // (без него для уже разобранной книги работа ничего не сделала бы), и `refreshCover`.
                metadataScheduler.enqueueMetadata(bookId, force = true, refreshCover = true)
                _uiState.update { it.copy(isLoading = false, error = null) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: SQLException) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = AppText(R.string.book_error_schedule_parse, error.toAppError().text()),
                    )
                }
            }
        }
    }

    /**
     * Показать книгу в папке.
     *
     * Открывает системный файловый менеджер на папке книги: у SAF-документа нет пути на диске,
     * который можно было бы показать текстом. Отказ (нет обработчика, адрес не SAF) показывается
     * причиной — кнопка не должна молчать.
     */
    fun onShowInFolder() {
        viewModelScope.launch {
            val book = bookRepository.getById(bookId).getOrNull()
            if (book == null) {
                _uiState.update { it.copy(error = AppText(R.string.book_error_not_found)) }
                return@launch
            }
            folderOpener.openFolder(book.documentUri).errorOrNull()?.let { failure ->
                _uiState.update { state ->
                    state.copy(error = AppText(R.string.book_error_open_folder, failure.text()))
                }
            }
        }
    }

    /**
     * Подписка на строку книги: её гасим после удаления.
     *
     * Держим ссылку, потому что после успешного удаления наблюдать больше не за чем, а последнее
     * событие потока (книга исчезла) не должно переписывать состояние закрывающегося экрана.
     */
    private var bookObservation: Job? = null

    private fun observeBook() {
        bookObservation = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                var receivedBook = false
                bookRepository.observeById(bookId).collect { book ->
                    receivedBook = true
                    if (book == null) {
                        // Книгу удалили — в том числе прямо с этой карточки. Признак удаления
                        // сбрасывать нельзя: StateFlow склеивает значения, и промежуточный
                        // `isDeleted = true` мог не дойти до экрана, который по нему уходит назад.
                        // Тогда человек видел «книга не найдена» на удалённой книге вместо возврата.
                        _uiState.update { state ->
                            if (state.isDeleted) {
                                state
                            } else {
                                BookDetailsUiState(
                                    bookId = bookId,
                                    isLoading = false,
                                    error = AppText(R.string.book_error_not_found),
                                )
                            }
                        }
                    } else {
                        val sourceName = sourceNameOf(book.sourceId)
                        _uiState.update { state ->
                            state.withBook(book = book, sourceName = sourceName)
                        }
                    }
                }
                if (!receivedBook) {
                    _uiState.update {
                        it.copy(isLoading = false, error = AppText(R.string.book_error_not_found))
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: SQLException) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = AppText(R.string.book_error_load, error.toAppError().text()),
                    )
                }
            }
        }
    }

    private fun runBookCommand(
        onSuccess: () -> Unit = {},
        command: suspend () -> AppResult<Unit>,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val result = command()
                if (result.isFailure) {
                    val error = result.errorOrNull() ?: AppError.Unknown()
                    _uiState.update { it.copy(isLoading = false, error = error.text()) }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = null) }
                    onSuccess()
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: SQLException) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = AppText(R.string.book_error_action, error.toAppError().text()),
                    )
                }
            }
        }
    }

}

private fun BookDetailsUiState.withBook(book: Book, sourceName: String): BookDetailsUiState = copy(
    title = book.title,
    authors = book.authorsText.ifBlank { book.authors.joinToString(", ") },
    series = book.seriesLabel(),
    seriesName = book.seriesName,
    // Путь и папка: строки «Путь» и «Документ» в карточке были пустыми всегда, потому что
    // состояние объявляло поля, а маппинг их не заполнял.
    relativePath = book.relativePath,
    sourceName = sourceName,
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
    parseFailure = book.parseFailure?.text(),
    canOptimize = book.extension.lowercase() in OPTIMIZABLE_EXTENSIONS,
    isLoading = false,
    error = null,
)

/**
 * Расширения, которые оптимизируются: только формат FB2, в том числе в архиве.
 *
 * Список продублирован рядом с экраном: карточка не должна зависеть от `:core:fb2opt` ради двух
 * строк, а `BookRepository.optimizeBook` всё равно отвергнет всё остальное — здесь решается лишь
 * то, показывать ли кнопку.
 */
private val OPTIMIZABLE_EXTENSIONS = setOf("fb2", "fb2.zip")

/**
 * Отчёт об оптимизации словами.
 *
 * Размеры называются до и после, а не только экономия: «сэкономлено 2,2 МБ» без исходного размера
 * ничего не говорит о том, была ли книга большой. Отсутствие выигрыша — тоже результат, и он
 * формулируется как «заменять нечего», а не как пустая строка.
 */
private fun optimizationReport(result: BookOptimizationResult): AppText = when {
    result.alreadyOptimized -> AppText(R.string.book_optimized_earlier)
    result.changed -> AppText(
        R.string.book_optimized_saved,
        formatSize(result.savedBytes.toLong()),
        AppText(
            R.string.book_optimized_sizes,
            formatSize(result.beforeBytes.toLong()),
            formatSize(result.afterBytes.toLong()),
        ),
    )

    else -> AppText(R.string.book_optimized_nothing)
}

private fun Book.seriesLabel(): String? = seriesName?.let { name ->
    seriesIndex?.let { index ->
        val wholeIndex = index.toInt().toDouble()
        val formattedIndex = if (index == wholeIndex) wholeIndex.toInt().toString() else index.toString()
        "$name #$formattedIndex"
    } ?: name
}

private fun formatSize(bytes: Long): AppText = when {
    bytes < KIBIBYTE -> AppText(R.string.book_size_bytes, bytes)
    bytes < MEBIBYTE -> AppText(R.string.book_size_kb, decimal(bytes.toDouble() / KIBIBYTE))
    bytes < GIBIBYTE -> AppText(R.string.book_size_mb, decimal(bytes.toDouble() / MEBIBYTE))
    else -> AppText(R.string.book_size_gb, decimal(bytes.toDouble() / GIBIBYTE))
}

/** Число с одной десятой по локали: число считает код, единицу измерения даёт ресурс. */
private fun decimal(value: Double): String = String.format(Locale.getDefault(), "%.1f", value)

private const val KIBIBYTE = 1024L
private const val MEBIBYTE = KIBIBYTE * 1024L
private const val GIBIBYTE = MEBIBYTE * 1024L

private fun formatDate(timestamp: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()).format(Date(timestamp))

