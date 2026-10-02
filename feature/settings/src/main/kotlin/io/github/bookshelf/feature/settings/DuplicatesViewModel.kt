package io.github.bookshelf.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.bookshelf.core.common.error.errorOrNull
import io.github.bookshelf.core.common.error.getOrNull
import io.github.bookshelf.core.data.repository.BookRepository
import io.github.bookshelf.core.designsystem.text.AppText
import io.github.bookshelf.core.designsystem.text.text
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Состояние блока «Дубли книг» в разделе «Библиотека и сканирование».
 *
 * @param isChecking идёт сравнение содержимого файлов (чтение всех книг).
 * @param isDeleting идёт удаление подтверждённых файлов.
 * @param message результат для строки настроек: переводимые части («дубликатов не найдено», «удалено N файлов»)
 * @param toRemove идентификаторы копий, предложенных к удалению как дубли.
 * @param broken идентификаторы файлов нулевого размера (их отдельно предлагается удалить).
 * @param isConfirming показывается подтверждение удаления дублей.
 * @param isConfirmingBroken показывается подтверждение удаления битых файлов.
 * @param error ошибка для баннера: текст по типу причины, а не `AppError.message` («TDD» §7).
 */
data class DuplicatesUiState(
    val isChecking: Boolean = false,
    val isDeleting: Boolean = false,
    val message: List<AppText> = emptyList(),
    val toRemove: List<String> = emptyList(),
    val broken: List<String> = emptyList(),
    val isConfirming: Boolean = false,
    val isConfirmingBroken: Boolean = false,
    val error: AppText? = null,
)

/**
 * Поиск и удаление дублей книг.
 *
 * Отдельная ViewModel, а не методы в [SettingsViewModel]: там уже 29 методов при пороге detekt
 * `TooManyFunctions` 30, и добавлять шесть означало бы упереться в порог. Здесь же собирается всё,
 * что нужно этому блоку: проверка, подтверждение и удаление — вместе с состоянием.
 *
 * Логика поиска и удаления живёт в слое данных (`BookRepository.findDuplicates` /
 * `deletePermanently`), поэтому в этой ViewModel нечего проверять отдельно от потока состояний.
 */
@HiltViewModel
class DuplicatesViewModel @Inject constructor(
    private val books: BookRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DuplicatesUiState())

    /** Состояние блока «Дубли книг». */
    val uiState: StateFlow<DuplicatesUiState> = _uiState.asStateFlow()

    /** Найти дубли и битые файлы. Повторное нажатие во время работы игнорируется. */
    fun onCheck() {
        if (_uiState.value.isChecking || _uiState.value.isDeleting) return
        _uiState.update {
            it.copy(
                isChecking = true,
                message = emptyList(),
                error = null,
                toRemove = emptyList(),
                broken = emptyList(),
                isConfirming = false,
                isConfirmingBroken = false,
            )
        }
        viewModelScope.launch {
            val report = books.findDuplicates()
            val error = report.errorOrNull()
            if (error != null) {
                _uiState.update {
                    it.copy(
                        isChecking = false,
                        error = AppText(R.string.settings_duplicates_check_failed, error.text()),
                    )
                }
                return@launch
            }
            val found = report.getOrNull()
            val toRemove = found?.groups.orEmpty().flatMap { group -> group.remove.map { book -> book.id } }
            val broken = found?.broken.orEmpty().map { book -> book.id }
            _uiState.update { state ->
                state.copy(
                    isChecking = false,
                    toRemove = toRemove,
                    broken = broken,
                    isConfirming = toRemove.isNotEmpty(),
                    isConfirmingBroken = broken.isNotEmpty(),
                    message = resultMessage(
                        groups = found?.groups?.size ?: 0,
                        duplicates = toRemove.size,
                        broken = broken.size,
                        stopped = found?.stopped == true,
                    ),
                )
            }
        }
    }

    /** Удалить подтверждённые копии-дубли вместе с файлами. */
    fun onConfirmDelete() {
        val ids = _uiState.value.toRemove
        if (ids.isEmpty()) return
        delete(ids) { state, deleted, failed ->
            state.copy(
                toRemove = emptyList(),
                isConfirming = false,
                message = listOf(AppText(R.string.settings_duplicates_deleted, deleted)) +
                    failedPart(failed),
            )
        }
    }

    /** Отказаться от удаления дублей: список к удалению забывается, повторный поиск его найдёт снова. */
    fun onCancelDelete() {
        _uiState.update {
            it.copy(
                toRemove = emptyList(),
                isConfirming = false,
                message = listOf(AppText(R.string.settings_duplicates_cancel)),
            )
        }
    }

    /** Удалить подтверждённые битые файлы (нулевого размера). */
    fun onConfirmDeleteBroken() {
        val ids = _uiState.value.broken
        if (ids.isEmpty()) return
        delete(ids) { state, deleted, failed ->
            state.copy(
                broken = emptyList(),
                isConfirmingBroken = false,
                message = listOf(AppText(R.string.settings_duplicates_broken_deleted, deleted)) +
                    failedPart(failed),
            )
        }
    }

    /** Отказаться от удаления битых файлов. */
    fun onCancelDeleteBroken() {
        _uiState.update {
            it.copy(
                broken = emptyList(),
                isConfirmingBroken = false,
                message = listOf(AppText(R.string.settings_duplicates_broken_cancel)),
            )
        }
    }

    /**
     * Общее удаление для обоих подтверждений.
     *
     * @param update как изменить состояние после удаления; текст сообщения собирает вызывающий.
     */
    private fun delete(
        ids: List<String>,
        update: (DuplicatesUiState, deleted: Int, failed: Int) -> DuplicatesUiState,
    ) {
        if (_uiState.value.isDeleting) return
        _uiState.update { it.copy(isDeleting = true, error = null) }
        viewModelScope.launch {
            val outcome = books.deletePermanently(ids)
            val error = outcome.errorOrNull()
            if (error != null) {
                _uiState.update {
                    it.copy(
                        isDeleting = false,
                        error = AppText(R.string.settings_duplicates_delete_error, error.text()),
                    )
                }
                return@launch
            }
            val result = outcome.getOrNull()
            _uiState.update { state ->
                update(state, result?.deleted ?: 0, result?.failed ?: 0).copy(isDeleting = false)
            }
        }
    }
}

/**
 * Строка результата проверки: сколько групп и файлов нашлось.
 *
 * Прерванный поиск нельзя выдавать за «дубликатов не найдено»: файлы ещё не сравнены, и человек
 * сделал бы вывод, что копий нет. Причина остановки — открытая читалка, которой поиск уступает
 * процессор (ANR читалки 30.09.2026). Части — переводимые тексты, экран склеивает их сам.
 */
private fun resultMessage(groups: Int, duplicates: Int, broken: Int, stopped: Boolean): List<AppText> {
    if (stopped) {
        val found = if (groups == 0 && broken == 0) {
            AppText(R.string.settings_duplicates_nothing_before_stop)
        } else {
            AppText(R.string.settings_duplicates_found_before_stop, groups, broken)
        }
        return listOf(AppText(R.string.settings_duplicates_stopped, found))
    }
    if (groups == 0 && broken == 0) return listOf(AppText(R.string.settings_duplicates_none))
    return buildList {
        if (groups > 0) add(AppText(R.string.settings_duplicates_groups, groups, duplicates))
        if (broken > 0) add(AppText(R.string.settings_duplicates_broken, broken))
    }
}

/** Часть «часть файлов осталась на диске»: причина отказа — нет доступа к папке. */
private fun failedPart(failed: Int): List<AppText> = if (failed == 0) {
    emptyList()
} else {
    listOf(AppText(R.string.settings_duplicates_delete_failed, failed))
}
