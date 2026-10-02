package io.github.bookshelf.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.bookshelf.core.common.error.errorOrNull
import io.github.bookshelf.core.common.error.getOrNull
import io.github.bookshelf.core.data.repository.BookOptimizationBatch
import io.github.bookshelf.core.data.repository.BookRepository
import io.github.bookshelf.core.designsystem.text.AppText
import io.github.bookshelf.core.designsystem.text.text
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Состояние блока «Оптимизировать все книги FB2» в разделе «Хранилище и кэш».
 *
 * @param isRunning идёт перебор книг.
 * @param done сколько книг уже обработано.
 * @param total сколько книг подходит под оптимизацию (известно до первой книги).
 * @param message итог словами: сколько оптимизировано, сколько сэкономлено.
 * @param error текст ошибки для баннера.
 */
data class BookOptimizationUiState(
    val isRunning: Boolean = false,
    /** Нажали «Остановить»: текущая книга дорабатывается, новая не начнётся. */
    val isStopping: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    /** Итог словами: переводимые части, экран склеивает их через `resolveJoined` («TDD» §5). */
    val message: List<AppText> = emptyList(),
    /** Ошибка: текст по типу причины, а не `AppError.message` («TDD» §7). */
    val error: AppText? = null,
)

/**
 * Оптимизация всех книг FB2 без потери данных.
 *
 * Отдельная ViewModel, а не методы в [SettingsViewModel]: там уже 29 методов при пороге detekt
 * `TooManyFunctions` 30, и добавлять сюда ещё несколько означало бы упереться в порог.
 *
 * Сама работа живёт в слое данных (`BookRepository.optimizeAllFb2`): там же правило «уступаем
 * читалке». Здесь только то, что относится к экрану, — прогресс, отмена и итог словами.
 */
@HiltViewModel
class BookOptimizationViewModel @Inject constructor(
    private val books: BookRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookOptimizationUiState())

    /** Состояние блока оптимизации. */
    val uiState: StateFlow<BookOptimizationUiState> = _uiState.asStateFlow()

    /** Текущий прогон: по нему кнопка «Остановить» действительно прерывает работу. */
    private var job: Job? = null

    /** Запустить оптимизацию. Повторное нажатие во время работы ничего не делает. */
    fun onStart() {
        if (job?.isActive == true) return
        _uiState.update {
            it.copy(
                isRunning = true,
                isStopping = false,
                done = 0,
                total = 0,
                message = emptyList(),
                error = null,
            )
        }
        job = viewModelScope.launch {
            try {
                val outcome = books.optimizeAllFb2 { done, total ->
                    _uiState.update { it.copy(done = done, total = total) }
                }
                val failure = outcome.errorOrNull()
                if (failure != null) {
                    _uiState.update {
                        it.copy(
                            isRunning = false,
                            isStopping = false,
                            error = AppText(R.string.settings_optimization_failed, failure.text()),
                        )
                    }
                    return@launch
                }
                val batch = outcome.getOrNull()
                _uiState.update { state ->
                    state.copy(
                        isRunning = false,
                        isStopping = false,
                        message = batch?.let(::report)
                            ?: listOf(AppText(R.string.settings_optimization_done)),
                    )
                }
            } catch (cancellation: CancellationException) {
                // Отмену обрабатываем и пробрасываем: сообщение об остановке обязан увидеть человек,
                // а корутина — остаться отменённой, иначе `viewModelScope` посчитал бы её живой.
                _uiState.update { state ->
                    state.copy(
                        isRunning = false,
                        isStopping = false,
                        message = listOf(
                            AppText(R.string.settings_optimization_stopped, state.done, state.total),
                        ),
                    )
                }
                throw cancellation
            }
        }
    }

    /**
     * Остановить прогон.
     *
     * Файл на середине записи не бросается: отмена проверяется до очередной картинки и до чтения
     * следующей книги, поэтому незавершённой замены быть не может. Уже обработанные книги остаются
     * оптимизированными — возвращать их к прежнему размеру нечем и незачем, а повторный запуск
     * их и не тронет: проверенные книги помечены.
     */
    fun onStop() {
        if (job?.isActive != true) return
        // Подпись меняется сразу, хотя текущая книга ещё дорабатывается: картинки пересобираются
        // в памяти, и мгновенно оборвать их нельзя. Честнее показать «останавливаем», чем молчать
        // целую минуту (на e-ink книга в 13 МБ считается именно столько).
        _uiState.update { it.copy(isStopping = true) }
        job?.cancel()
    }
}

/**
 * Итог прогона словами.
 *
 * Отдельно называются разные вещи, которые легко слить в одно «готово»: сколько книг уменьшено,
 * сколько проверено без выигрыша, сколько не удалось и сколько было проверено раньше. Остановка
 * читалкой называется первой частью и с числами: без этого «оптимизировано 1» при пяти книгах
 * читалось бы как полный успех.
 *
 * Части — переводимые тексты ([AppText]), а не готовая строка: в итоге бывает до пяти чисел, и
 * экран склеивает части разделителем из ресурса (`resolveJoined`).
 */
private fun report(batch: BookOptimizationBatch): List<AppText> {
    if (batch.total == 0) {
        return listOf(
            if (batch.alreadyOptimized > 0) {
                AppText(R.string.settings_optimization_all_checked)
            } else {
                AppText(R.string.settings_optimization_no_books)
            },
        )
    }
    return buildList {
        if (batch.stoppedByReader) {
            add(AppText(R.string.settings_optimization_stopped_by_reader, batch.processed, batch.total))
        }
        add(AppText(R.string.settings_optimization_optimized, batch.optimized))
        if (batch.savedBytes > 0) {
            add(AppText(R.string.settings_optimization_saved, freedBytesText(batch.savedBytes)))
        }
        if (batch.skipped > 0) {
            add(AppText(R.string.settings_optimization_already_best, batch.skipped))
        }
        if (batch.failed > 0) {
            add(AppText(R.string.settings_optimization_failed_count, batch.failed))
        }
        if (batch.alreadyOptimized > 0) {
            add(AppText(R.string.settings_optimization_skipped_checked, batch.alreadyOptimized))
        }
    }
}
