package io.github.bookshelf.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.errorOrNull
import io.github.bookshelf.core.common.error.isFailure
import io.github.bookshelf.core.data.model.ReaderProfileSummary
import io.github.bookshelf.core.data.model.Source
import io.github.bookshelf.core.data.repository.ReaderRepository
import io.github.bookshelf.core.data.repository.SourceRepository
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * ViewModel онбординга (docs/eink-ux.md §6.1, Sprint 2).
 *
 * Экран получает URI из системного SAF-пикера, а ViewModel передаёт его в
 * [SourceRepository.addSafSource]. Это важно: persistable-разрешение и первичный скан
 * должны выполняться внутри data-слоя, а не в Compose-экране.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val sourceRepository: SourceRepository,
    private val readerRepository: ReaderRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())

    /** Состояние экрана онбординга для `collectAsStateWithLifecycle`. */
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private val _sourcePickerRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Событие открытия системного SAF-пикера; URI приходит обратно в [onSourcePicked]. */
    val pickerRequests: SharedFlow<Unit> = _sourcePickerRequests.asSharedFlow()

    init {
        observeSources()
        observeReaders()
    }

    /** Переход на следующий шаг; на последнем шаге остаётся на месте. */
    fun onNextStep() {
        val current = _uiState.value
        _uiState.value = current.copy(step = current.step.next())
    }

    /** Возврат на предыдущий шаг (кнопка «Назад», BACK и PAGE_UP). */
    fun onPreviousStep() {
        val current = _uiState.value
        _uiState.value = current.copy(step = current.step.previous())
    }

    /** Запросить открытие системного выбора папки SAF. */
    fun onAddSource() {
        _uiState.value = _uiState.value.copy(error = null)
        if (!_sourcePickerRequests.tryEmit(Unit)) {
            showError("Не удалось открыть выбор папки")
        }
    }

    /**
     * Сохранить папку, выбранную системным SAF-пикером.
     *
     * Разрешение на URI и первичный скан находятся внутри [SourceRepository], поэтому экран
     * передаёт только URI и имя, а не пытается самостоятельно работать с ContentResolver.
     */
    fun onSourcePicked(uri: String, displayName: String) {
        val safeUri = uri.trim()
        if (safeUri.isEmpty()) {
            showError("Выбранная папка не содержит URI")
            return
        }
        val safeName = displayName.trim().ifBlank {
            safeUri.substringAfterLast('/').ifBlank { DEFAULT_SOURCE_NAME }
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true, error = null)
            try {
                val result = sourceRepository.addSafSource(safeUri, safeName)
                if (result.isFailure) {
                    showError(
                        appErrorMessage(
                            result.errorOrNull(),
                            "Не удалось добавить папку.",
                        ),
                    )
                } else {
                    _uiState.value = _uiState.value.copy(isBusy = false, error = null)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                showError(error.userMessage("Не удалось добавить папку."))
            }
        }
    }

    /** Убирает папку из списка источников вместе с разрешением. */
    fun onRemoveSource(sourceId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true, error = null)
            try {
                val result = sourceRepository.removeSource(sourceId)
                if (result.isFailure) {
                    showError(
                        appErrorMessage(
                            result.errorOrNull(),
                            "Не удалось убрать папку.",
                        ),
                    )
                } else {
                    _uiState.value = _uiState.value.copy(isBusy = false, error = null)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                showError(error.userMessage("Не удалось убрать папку."))
            }
        }
    }

    /**
     * Назначает читалку по умолчанию для формата.
     *
     * Текущий экран ещё не выбирает формат, поэтому для обратной совместимости используется
     * `fb2`; после появления выбора формата метод можно будет расширить параметром.
     */
    fun onSelectReader(readerId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true, error = null)
            try {
                val result = readerRepository.setDefault(DEFAULT_READER_FORMAT, readerId)
                if (result.isFailure) {
                    showError(
                        appErrorMessage(
                            result.errorOrNull(),
                            "Не удалось назначить читалку по умолчанию.",
                        ),
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        readers = _uiState.value.readers.map { reader ->
                            reader.copy(isDefault = reader.id == readerId || reader.isDefault)
                        },
                        isBusy = false,
                        error = null,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                showError(error.userMessage("Не удалось назначить читалку по умолчанию."))
            }
        }
    }

    /**
     * Открывает тестовый файл в выбранной читалке для проверки.
     *
     * В контракте [ReaderRepository] нет `openTestFile`; до его появления действие сообщает
     * ограничение и не выбрасывает исключение из обработчика Compose.
     */
    fun onCheckReader(readerId: String) {
        showError("Открытие тестового файла читалки пока не поддерживается")
    }

    /**
     * Завершить мастер.
     *
     * Переход к сканированию принадлежит `onFinished` в экране, поэтому ViewModel
     * не знает о навигации. Здесь только снимается занятость и проверяется обязательный источник.
     */
    fun onFinish() {
        val current = _uiState.value
        val error = if (current.sources.isEmpty()) {
            "Сначала добавьте папку с книгами"
        } else {
            null
        }
        _uiState.value = current.copy(isBusy = false, error = error)
    }

    private fun observeSources() {
        viewModelScope.launch {
            try {
                sourceRepository.observeSources().collect { sources ->
                    val items = sources.map(::toSourceItem)
                    _uiState.value = _uiState.value.copy(
                        sources = items,
                        canFinish = canFinishOnboarding(items),
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                showError(error.userMessage("Не удалось получить список папок."))
            }
        }
    }

    private fun observeReaders() {
        viewModelScope.launch {
            try {
                readerRepository.installedReaders().collect { readers ->
                    _uiState.value = _uiState.value.copy(
                        readers = readers.map(::toReaderItem),
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                showError(error.userMessage("Не удалось получить список читалок."))
            }
        }
    }

    private fun toSourceItem(source: Source): SourceListItem = SourceListItem(
        id = source.id,
        displayName = source.displayName,
        uri = source.uri,
        documentCount = source.documentCount.takeIf { it > 0 },
        accessLost = source.accessLost,
    )

    private fun toReaderItem(reader: ReaderProfileSummary): ReaderListItem = ReaderListItem(
        id = reader.id,
        name = reader.name,
        packageName = reader.packageName,
        isDefault = _uiState.value.readers.any { current ->
            current.id == reader.id && current.isDefault
        },
    )

    private fun showError(message: String) {
        _uiState.value = _uiState.value.copy(isBusy = false, error = message)
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
        const val DEFAULT_READER_FORMAT = "fb2"
        const val DEFAULT_SOURCE_NAME = "Папка"
    }
}
