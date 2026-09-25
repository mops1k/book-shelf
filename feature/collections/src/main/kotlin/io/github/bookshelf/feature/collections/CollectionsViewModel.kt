package io.github.bookshelf.feature.collections

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.bookshelf.core.data.repository.BookRepository
import io.github.bookshelf.core.data.repository.CollectionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * ViewModel коллекций (docs/eink-ux.md §6.3, Sprint 7).
 *
 * На этапе каркаса отдаёт только начальное состояние: репозитории внедрены как контракты
 * и не вызываются. Выделение полки — чистое UI-состояние, оно работает уже сейчас.
 *
 * После Sprint 7 появятся действия:
 * * [onCreate] — создание ручной полки;
 * * [onRename] / [onDelete] — переименование и удаление;
 * * [onEditRules] — настройка правил умной полки (тот же формат, что у фильтров);
 * * [onRefresh] — перечитывание списка полок и счётчиков книг.
 */
@HiltViewModel
class CollectionsViewModel @Inject constructor(
    private val collectionRepository: CollectionRepository,
    private val bookRepository: BookRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CollectionsUiState())

    /** Состояние экрана коллекций для `collectAsStateWithLifecycle`. */
    val uiState: StateFlow<CollectionsUiState> = _uiState.asStateFlow()

    /** Выделить полку — цель действия «Открыть» с D-pad и аппаратных кнопок. */
    fun onSelect(collectionId: String) {
        _uiState.value = _uiState.value.copy(selectedId = collectionId)
    }

    /** Создать новую ручную полку. */
    fun onCreate() {
        // STUB(Sprint 7): запросить имя и создать полку
        TODO("STUB(Sprint 7): collectionRepository.create(name)")
    }

    /** Переименовать выделенную полку. */
    fun onRename(collectionId: String) {
        // STUB(Sprint 7): диалог переименования
        TODO("STUB(Sprint 7): collectionRepository.rename(collectionId = $collectionId)")
    }

    /** Удалить полку (книги при этом остаются в библиотеке). */
    fun onDelete(collectionId: String) {
        // STUB(Sprint 7): подтверждение и удаление полки
        TODO("STUB(Sprint 7): collectionRepository.delete(collectionId = $collectionId)")
    }

    /** Настроить правила умной полки. */
    fun onEditRules(collectionId: String) {
        // STUB(Sprint 7): редактор правил на общем предикате с фильтрами
        TODO("STUB(Sprint 7): collectionRepository.updateRules(collectionId = $collectionId)")
    }

    /** Перечитать список полок и счётчики книг. */
    fun onRefresh() {
        // STUB(Sprint 7): перечитать коллекции из репозитория
        TODO("STUB(Sprint 7): collectionRepository.observeAll()")
    }
}
