package io.github.bookshelf.feature.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.errorOrNull
import io.github.bookshelf.core.common.error.isFailure
import io.github.bookshelf.core.data.model.ScanProgress
import io.github.bookshelf.core.data.model.ScanReport
import io.github.bookshelf.core.data.model.ScanStatus
import io.github.bookshelf.core.data.repository.ScanRepository
import io.github.bookshelf.core.data.repository.SourceRepository
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

/**
 * ViewModel сканирования и отчёта (docs/eink-ux.md §6.6–6.7, Sprint 2).
 *
 * Экран работает с одним выбранным источником: его прогресс подписывается отдельно, а
 * повторный [ScanRepository.startScan] после паузы продолжает сохранённый координатором курсор.
 */
@HiltViewModel
class ScanViewModel @Inject constructor(
    private val scanRepository: ScanRepository,
    private val sourceRepository: SourceRepository,
) : ViewModel() {

    /** Выход с экрана: кнопка «Отменить» работает как «Назад», когда отменять нечего. */
    private var onExit: () -> Unit = {}

    /** Задать выход с экрана: кнопка «Отменить» работает как «Назад», когда отменять нечего. */
    fun setOnExit(callback: () -> Unit) {
        onExit = callback
    }

    private val _uiState = MutableStateFlow(ScanUiState())

    /** Состояние экрана сканирования для `collectAsStateWithLifecycle`. */
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    private val activeSourceId = MutableStateFlow<String?>(null)
    private val sourceNames = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Оценка числа файлов по прошлому прогону источника — для строки прогресса. */
    private val sourceDocumentCounts = MutableStateFlow<Map<String, Int>>(emptyMap())

    init {
        observeSources()
        observeProgress()
        observeReports()
    }

    /** Запустить сканирование источника. */
    fun onStartScan() {
        startOrResumeScan()
    }

    /**
     * Поставить скан на паузу: найденное сохраняется, прогон остаётся возобновляемым.
     *
     * Кнопки «Отменить» в интерфейсе нет (решение Александра, 28.09.2026): скан прерываемый, и
     * отдельная жёсткая отмена только запутывала бы — уйти с экрана можно стрелкой назад.
     */
    fun onPause() {
        viewModelScope.launch {
            val sourceId = activeSourceId.value ?: firstAvailableSourceId()
            if (sourceId == null) {
                showError("Нет источника для сканирования")
                return@launch
            }
            activeSourceId.value = sourceId
            try {
                scanRepository.pause(sourceId)
                _uiState.value = _uiState.value.copy(status = ScanStatus.PAUSED, error = null)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _uiState.value = _uiState.value.copy(
                    error = error.userMessage("Не удалось поставить скан на паузу."),
                )
            }
        }
    }

    /** Продолжить прерванный скан с сохранённого курсора. */
    fun onResume() {
        startOrResumeScan()
    }

    /** Уйти на полку: скан идёт в фоне, экран его не держит. */
    fun onOpenLibrary() {
        onExit()
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
        showError("Разрешение дубликатов пока не поддерживается")
    }

    /** Убрать лишние записи группы из библиотеки (файлы не удаляются никогда). */
    fun onRemoveDuplicates(groupId: String) {
        showError("Удаление дубликатов пока не поддерживается")
    }

    /** Перечитать прогресс и отчёт. */
    fun onRefresh() {
        viewModelScope.launch {
            try {
                val reports = scanRepository.observeReports().firstOrNull().orEmpty()
                applyReport(reports.lastOrNull())

                val sourceId = activeSourceId.value ?: firstAvailableSourceId()
                if (sourceId != null) {
                    activeSourceId.value = sourceId
                    scanRepository.observeProgress(sourceId).firstOrNull()?.let(::applyProgress)
                }
                _uiState.value = _uiState.value.copy(error = null)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                showError(error.userMessage("Не удалось обновить сканирование."))
            }
        }
    }

    private fun startOrResumeScan() {
        viewModelScope.launch {
            try {
                val sourceId = activeSourceId.value ?: firstAvailableSourceId()
                if (sourceId == null) {
                    showError("Нет источника для сканирования")
                    return@launch
                }
                activeSourceId.value = sourceId
                _uiState.value = _uiState.value.copy(status = ScanStatus.RUNNING, error = null)
                val result = scanRepository.startScan(sourceId)
                if (result is io.github.bookshelf.core.common.error.AppResult.Success) {
                    // Старт прошёл, но прогресса нет — значит, работа не встала в очередь.
                    // Молчание выглядело как «всё идёт»: пользователь ждал зря.
                    viewModelScope.launch {
                        kotlinx.coroutines.delay(NO_PROGRESS_TIMEOUT_MS)
                        val stillIdle = _uiState.value.progress?.filesSeen == 0 && !_uiState.value.isRunning
                        if (stillIdle) {
                            showError("Не удалось просканировать: работа не запустилась")
                        }
                    }
                }
                if (result.isFailure) {
                    _uiState.value = _uiState.value.copy(
                        status = ScanStatus.IDLE,
                        error = appErrorMessage(
                            result.errorOrNull(),
                            "Не удалось запустить сканирование.",
                        ),
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _uiState.value = _uiState.value.copy(
                    status = ScanStatus.IDLE,
                    error = error.userMessage("Не удалось запустить сканирование."),
                )
            }
        }
    }

    private fun observeSources() {
        viewModelScope.launch {
            try {
                sourceRepository.observeSources().collect { sources ->
                    val names = sources.associate { it.id to it.displayName }
                    sourceNames.value = names
                    sourceDocumentCounts.value = sources.associate { it.id to it.documentCount }
                    val selected = activeSourceId.value?.takeIf { it in names }
                        ?: sources.firstOrNull { it.enabled && !it.accessLost }?.id
                    activeSourceId.value = selected
                    if (selected == null) {
                        _uiState.value = _uiState.value.copy(
                            progress = null,
                            status = ScanStatus.IDLE,
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                showError(error.userMessage("Не удалось получить список источников."))
            }
        }
    }

    private fun observeProgress() {
        viewModelScope.launch {
            try {
                activeSourceId
                    .filterNotNull()
                    .distinctUntilChanged()
                    .flatMapLatest { sourceId ->
                        try {
                            scanRepository.observeProgress(sourceId)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Throwable) {
                            showError(error.userMessage("Не удалось получить прогресс сканирования."))
                            emptyFlow()
                        }
                    }
                    .collect(::applyProgress)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                showError(error.userMessage("Не удалось получить прогресс сканирования."))
            }
        }
    }

    private fun observeReports() {
        viewModelScope.launch {
            try {
                scanRepository.observeReports().collect { reports ->
                    applyReport(reports.lastOrNull())
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                showError(error.userMessage("Не удалось получить отчёты сканирования."))
            }
        }
    }

    private suspend fun firstAvailableSourceId(): String? = try {
        sourceRepository.observeSources().firstOrNull()
            ?.firstOrNull { it.enabled && !it.accessLost }
            ?.id
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        showError(error.userMessage("Не удалось получить список источников."))
        null
    }

    private fun applyProgress(progress: ScanProgress) {
        val uiProgress = ScanProgressUi(
            sourceName = sourceNames.value[progress.sourceId] ?: progress.sourceId,
            filesSeen = progress.filesSeen,
            // Своё число файлов воркер не считает (`totalKnown` никто не заполняет), поэтому
            // берётся оценка по прошлому прогону источника. Она показывается с тильдой — это
            // ориентир, а не точное число файлов в дереве.
            filesTotal = progress.totalKnown ?: sourceDocumentCounts.value[progress.sourceId],
            added = progress.added,
            updated = progress.updated,
            removed = progress.removed,
            failed = progress.failed,
        )
        _uiState.value = _uiState.value.copy(
            progress = uiProgress,
            status = progress.status,
        )
    }

    private fun applyReport(report: ScanReport?) {
        _uiState.value = _uiState.value.copy(
            report = report?.let {
                ScanReportUi(
                    added = it.added,
                    updated = it.updated,
                    removed = it.removed,
                    failed = it.failedCount,
                    durationLabel = reportDuration(it),
                )
            },
        )
    }

    private fun reportDuration(report: ScanReport): String? {
        val finishedAt = report.finishedAt ?: return null
        val seconds = ((finishedAt - report.startedAt) / MILLIS_IN_SECOND).coerceAtLeast(0L)
        return "$seconds с"
    }

    private fun showError(message: String) {
        _uiState.value = _uiState.value.copy(error = message)
    }

    private fun appErrorMessage(error: AppError?, fallback: String): String = when {
        error == null -> fallback
        error is AppError.AccessLost -> "Доступ к папке потерян, выберите её заново."
        error is AppError.Unsupported -> error.message ?: "Операция пока не поддерживается."
        !error.message.isNullOrBlank() -> error.message.orEmpty()
        else -> fallback
    }

    private fun Throwable.userMessage(fallback: String): String =
        message?.takeIf { it.isNotBlank() } ?: fallback

    private companion object {
        /** Столько ждём первого прогресса, прежде чем сказать, что работа не запустилась. */
        const val NO_PROGRESS_TIMEOUT_MS = 5_000L

        /** Длительность скана приходит в миллисекундах, а показывается в секундах. */
        const val MILLIS_IN_SECOND = 1_000L
    }
}
