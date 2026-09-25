package io.github.bookshelf.feature.onboarding

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.bookshelf.core.data.repository.ReaderRepository
import io.github.bookshelf.core.data.repository.SourceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * ViewModel онбординга (docs/eink-ux.md §6.1, Sprint 2).
 *
 * На этапе каркаса отдаёт только начальное состояние и не делает ни одного запроса:
 * репозитории внедрены как контракты, но их методы не вызываются (бизнес-логики нет).
 *
 * После Sprint 2 появятся действия:
 * * [onAddSource] — системный SAF-пикер `ACTION_OPEN_DOCUMENT_TREE`, `takePersistableUriPermission`
 *   и сохранение источника;
 * * [onRemoveSource] — удаление источника вместе с persistable-разрешением;
 * * [onSelectReader] — назначение читалки по умолчанию для форматов;
 * * [onCheckReader] — открытие тестового файла в выбранной читалке;
 * * [onFinish] — сохранение источников и профилей и передача управления сканированию.
 *
 * Переходы между шагами — чистая логика [next] / [previous], тестируется отдельно.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val sourceRepository: SourceRepository,
    private val readerRepository: ReaderRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())

    /** Состояние экрана онбординга для `collectAsStateWithLifecycle`. */
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

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

    /** Открывает системный выбор папки с книгами. */
    fun onAddSource() {
        // STUB(Sprint 2): запустить ACTION_OPEN_DOCUMENT_TREE и сохранить persistable-разрешение
        TODO("STUB(Sprint 2): SAF-пикер папки → sourceRepository.addSource(uri)")
    }

    /** Убирает папку из списка источников вместе с разрешением. */
    fun onRemoveSource(sourceId: String) {
        // STUB(Sprint 2): удалить источник и освободить persistable-разрешение
        TODO("STUB(Sprint 2): sourceRepository.removeSource(sourceId = $sourceId)")
    }

    /** Назначает читалку по умолчанию для всех её форматов. */
    fun onSelectReader(readerId: String) {
        // STUB(Sprint 2): записать format_defaults для форматов выбранного профиля
        TODO("STUB(Sprint 2): readerRepository.setDefault(readerId = $readerId)")
    }

    /** Открывает тестовый файл в выбранной читалке для проверки. */
    fun onCheckReader(readerId: String) {
        // STUB(Sprint 5): собрать Intent и открыть тестовый файл, показать результат проверки
        TODO("STUB(Sprint 5): readerRepository.openTestFile(readerId = $readerId)")
    }

    /** Сохраняет выбор и передаёт управление экрану сканирования. */
    fun onFinish() {
        // STUB(Sprint 2): сохранить источники и профили, затем запустить первое сканирование
        TODO("STUB(Sprint 2): persist источников и профилей → onFinished()")
    }
}
