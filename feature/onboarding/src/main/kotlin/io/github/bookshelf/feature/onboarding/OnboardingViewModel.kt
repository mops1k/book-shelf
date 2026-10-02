package io.github.bookshelf.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.errorOrNull
import io.github.bookshelf.core.common.error.isFailure
import io.github.bookshelf.core.common.error.toAppError
import io.github.bookshelf.core.data.model.ReaderProfileSummary
import io.github.bookshelf.core.data.model.Source
import io.github.bookshelf.core.data.repository.ReaderRepository
import io.github.bookshelf.core.data.repository.SourceRepository
import io.github.bookshelf.core.designsystem.text.AppText
import io.github.bookshelf.core.designsystem.text.text
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
            showError(AppText(R.string.onboarding_error_open_picker))
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
            showError(AppText(R.string.onboarding_error_empty_uri))
            return
        }
        // Имя папки — данные: берём то, что дала система, иначе последний сегмент адреса, иначе
        // ПУСТО. Слова («Папка») подставит экран (`settings_sources_default_name`): в базе русского
        // текста быть не должно, иначе он не переводится (docs/tdd.md §8).
        val safeName = displayName.trim().ifBlank { safeUri.substringAfterLast('/') }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true, error = null)
            try {
                val result = sourceRepository.addSafSource(safeUri, safeName)
                if (result.isFailure) {
                    showError(
                        AppText(
                            R.string.onboarding_error_add_source,
                            (result.errorOrNull() ?: AppError.Unknown()).text(),
                        ),
                    )
                } else {
                    _uiState.value = _uiState.value.copy(isBusy = false, error = null)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                showError(AppText(R.string.onboarding_error_add_source, error.toAppError().text()))
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
                        AppText(
                            R.string.onboarding_error_remove_source,
                            (result.errorOrNull() ?: AppError.Unknown()).text(),
                        ),
                    )
                } else {
                    _uiState.value = _uiState.value.copy(isBusy = false, error = null)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                showError(AppText(R.string.onboarding_error_remove_source, error.toAppError().text()))
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
                        AppText(
                            R.string.onboarding_error_default_reader,
                            (result.errorOrNull() ?: AppError.Unknown()).text(),
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
                showError(
                    AppText(R.string.onboarding_error_default_reader, error.toAppError().text()),
                )
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
        showError(AppText(R.string.onboarding_error_test_file))
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
            AppText(R.string.onboarding_error_no_source)
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
                showError(AppText(R.string.onboarding_error_sources, error.toAppError().text()))
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
                showError(AppText(R.string.onboarding_error_readers, error.toAppError().text()))
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

    private fun showError(message: AppText) {
        _uiState.value = _uiState.value.copy(isBusy = false, error = message)
    }

    private companion object {
        const val DEFAULT_READER_FORMAT = "fb2"
    }
}
