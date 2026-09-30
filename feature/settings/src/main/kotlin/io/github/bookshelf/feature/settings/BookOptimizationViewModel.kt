package io.github.bookshelf.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.bookshelf.core.common.error.errorOrNull
import io.github.bookshelf.core.common.error.getOrNull
import io.github.bookshelf.core.data.repository.BookOptimizationBatch
import io.github.bookshelf.core.data.repository.BookRepository
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
    val message: String? = null,
    val error: String? = null,
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
            it.copy(isRunning = true, isStopping = false, done = 0, total = 0, message = null, error = null)
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
                            error = failure.message ?: "Оптимизировать книги не удалось",
                        )
                    }
                    return@launch
                }
                val batch = outcome.getOrNull()
                _uiState.update { state ->
                    state.copy(
                        isRunning = false,
                        isStopping = false,
                        message = batch?.let(::report) ?: "Оптимизация завершена",
                    )
                }
            } catch (cancellation: CancellationException) {
                // Отмену обрабатываем и пробрасываем: сообщение об остановке обязан увидеть человек,
                // а корутина — остаться отменённой, иначе `viewModelScope` посчитал бы её живой.
                _uiState.update { state ->
                    state.copy(
                        isRunning = false,
                        isStopping = false,
                        message = "Оптимизация остановлена: обработано ${state.done} из ${state.total}",
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
 * Отдельно называются три разные вещи, которые легко слить в одно «готово»: сколько книг
 * уменьшено, сколько проверено без выигрыша и сколько не удалось. Остановка читалкой называется
 * прямо и с числами: без этого «оптимизировано 1» при пяти книгах читалось бы как полный успех.
 */
private fun report(batch: BookOptimizationBatch): String {
    if (batch.total == 0) {
        return if (batch.alreadyOptimized > 0) {
            "Все книги FB2 уже проверены — новых нет"
        } else {
            "Книг FB2 в библиотеке нет"
        }
    }
    val parts = mutableListOf("Оптимизировано книг: ${batch.optimized}")
    if (batch.savedBytes > 0) parts += "сэкономлено ${freedBytesLabel(batch.savedBytes)}"
    if (batch.skipped > 0) parts += "уже оптимальных: ${batch.skipped}"
    if (batch.failed > 0) parts += "не удалось: ${batch.failed}"
    if (batch.alreadyOptimized > 0) parts += "пропущено проверенных раньше: ${batch.alreadyOptimized}"
    val summary = parts.joinToString(", ")
    return if (batch.stoppedByReader) {
        "Остановлено: открыта читалка (обработано ${batch.processed} из ${batch.total}). $summary"
    } else {
        summary
    }
}
