package io.github.bookshelf.feature.scan

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.bookshelf.core.data.repository.ScanRepository
import io.github.bookshelf.core.data.repository.SourceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * ViewModel сканирования и отчёта (docs/eink-ux.md §6.6–6.7, Sprint 2).
 *
 * На этапе каркаса отдаёт только начальное состояние: репозитории внедрены как контракты
 * и не вызываются. Переключение экранов «прогресс / отчёт / дубликаты» — чистое UI-состояние,
 * проценты и «можно продолжить» считаются чистыми функциями из [ScanProgressMath.kt].
 *
 * После Sprint 2 появятся действия:
 * * [onStartScan] — запуск `ScanRepository.startScan(sourceId)`;
 * * [onCancel] — мягкая отмена: уже найденное сохраняется;
 * * [onResume] — продолжение с курсора, сохранённого в `scan_runs` (ADR-010);
 * * [onRefresh] — перечитывание прогресса и отчёта.
 */
@HiltViewModel
class ScanViewModel @Inject constructor(
    private val scanRepository: ScanRepository,
    private val sourceRepository: SourceRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScanUiState())

    /** Состояние экрана сканирования для `collectAsStateWithLifecycle`. */
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    /** Запустить сканирование источника. */
    fun onStartScan() {
        // STUB(Sprint 2): уникальная работа на источник, прогресс виден в UI
        TODO("STUB(Sprint 2): scanRepository.startScan(sourceId)")
    }

    /** Мягко отменить скан: найденное сохраняется. */
    fun onCancel() {
        // STUB(Sprint 2): отмена через scanRepository.cancel(sourceId)
        TODO("STUB(Sprint 2): scanRepository.cancel(sourceId)")
    }

    /** Продолжить прерванный скан с сохранённого курсора. */
    fun onResume() {
        // STUB(Sprint 2): продолжить с последнего documentId в scan_runs
        TODO("STUB(Sprint 2): продолжить скан с сохранённого курсора")
    }

    /** Показать прогресс сканирования. */
    fun onShowProgress() {
        _uiState.value = _uiState.value.copy(mode = ScanMode.PROGRESS)
    }

    /** Показать отчёт последнего скана. */
    fun onShowReport() {
        _uiState.value = _uiState.value.copy(mode = ScanMode.REPORT)
    }

    /** Показать группы дубликатов. */
    fun onShowDuplicates() {
        _uiState.value = _uiState.value.copy(mode = ScanMode.DUPLICATES)
    }

    /** Оставить выбранную копию, остальные скрыть. */
    fun onKeepCopy(groupId: String, bookId: String) {
        // STUB(Sprint 7): пометить keeper группы, остальные скрыть из полки
        TODO("STUB(Sprint 7): оставить копию $bookId в группе $groupId")
    }

    /** Убрать лишние записи группы из библиотеки (файлы не удаляются никогда). */
    fun onRemoveDuplicates(groupId: String) {
        // STUB(Sprint 7): удалить только записи БД, файлы на диске не трогать
        TODO("STUB(Sprint 7): удалить лишние записи группы $groupId")
    }

    /** Перечитать прогресс и отчёт. */
    fun onRefresh() {
        // STUB(Sprint 2): перечитать ScanProgress/ScanReport
        TODO("STUB(Sprint 2): scanRepository.observeProgress(sourceId)")
    }
}
