package io.github.bookshelf.feature.bookdetails

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.bookshelf.core.data.repository.BookRepository
import io.github.bookshelf.core.data.repository.ReaderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * ViewModel карточки книги (docs/eink-ux.md §6.4, Sprint 4).
 *
 * `bookId` приходит из аргумента маршрута через `SavedStateHandle`. На этапе каркаса
 * отдаётся только начальное состояние: репозитории внедрены как контракты и не вызываются.
 *
 * После Sprint 4–7 появятся действия:
 * * [onRead] — резолв читалки и запуск Intent (Sprint 5);
 * * [onToggleFavorite] / [onToggleRead] — запись `favorite` и `readingStatus`;
 * * [onAddToCollection] — добавление в ручную полку;
 * * [onEditMetadata] / [onRefreshMetadata] — ручное редактирование и перечитывание файла;
 * * [onShowInFolder] — открытие системного файлового менеджера, если доступно.
 */
@HiltViewModel
class BookDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val bookRepository: BookRepository,
    private val readerRepository: ReaderRepository,
) : ViewModel() {

    private val bookId: String = savedStateHandle.get<String>(BookDetailsRoute.ARG_BOOK_ID).orEmpty()

    private val _uiState = MutableStateFlow(BookDetailsUiState(bookId = bookId))

    /** Состояние карточки книги для `collectAsStateWithLifecycle`. */
    val uiState: StateFlow<BookDetailsUiState> = _uiState.asStateFlow()

    /** Открыть книгу в читалке по умолчанию (кнопка «Читать»). */
    fun onRead() {
        // STUB(Sprint 5): резолв профиля читалки и запуск Intent с правильным URI
        TODO("STUB(Sprint 5): readerRepository.resolve(bookId) → Intent")
    }

    /** Переключить избранное. */
    fun onToggleFavorite() {
        // STUB(Sprint 7): записать favorite и обновить состояние
        TODO("STUB(Sprint 7): bookRepository.setFavorite(bookId, !isFavorite)")
    }

    /** Переключить статус «прочитано». */
    fun onToggleRead() {
        // STUB(Sprint 7): записать readingStatus и обновить состояние
        TODO("STUB(Sprint 7): bookRepository.setReadingStatus(bookId, ReadingStatus.READ)")
    }

    /** Добавить книгу в ручную коллекцию. */
    fun onAddToCollection() {
        // STUB(Sprint 7): показать список коллекций и записать выбор
        TODO("STUB(Sprint 7): collectionRepository.addBook(bookId)")
    }

    /** Ручное редактирование метаданных. */
    fun onEditMetadata() {
        // STUB(Sprint 7): перейти на форму редактирования метаданных
        TODO("STUB(Sprint 7): навигация на форму редактирования метаданных")
    }

    /** Перечитать метаданные из файла (приоритет встроенных). */
    fun onRefreshMetadata() {
        // STUB(Sprint 7): поставить книгу в очередь MetadataWorker
        TODO("STUB(Sprint 7): metadataOverrides.refresh(bookId)")
    }

    /** Показать файл книги в системном менеджере (если доступно). */
    fun onShowInFolder() {
        // STUB(Sprint 7): построить Intent ACTION_VIEW для родительской папки
        TODO("STUB(Sprint 7): показать документ в файловом менеджере")
    }
}
