package io.github.bookshelf.feature.settings

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.data.repository.BookOptimizationBatch
import io.github.bookshelf.core.designsystem.text.AppText
import io.github.bookshelf.core.designsystem.text.text
import io.github.bookshelf.core.testing.fake.FakeBookRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Оптимизация всех книг FB2 из настроек (`BookOptimizationViewModel`).
 *
 * Проверяются повороты, которых не видно в слое данных: полоса прогресса получает общее число книг
 * до первой из них, остановленная читалкой работа не выдаётся за «готово», а кнопка «Остановить»
 * действительно прерывает прогон, а не просто меняет подпись.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BookOptimizationViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val books = FakeBookRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `progress reaches the screen and the savings are named`() = runTest(dispatcher) {
        books.batchProgress = listOf(0 to 3, 1 to 3, 2 to 3, 3 to 3)
        books.optimizationBatch = AppResult.success(
            BookOptimizationBatch(
                total = 3,
                optimized = 2,
                skipped = 1,
                failed = 0,
                savedBytes = 2_097_152,
                stoppedByReader = false,
            ),
        )
        val viewModel = BookOptimizationViewModel(books)

        viewModel.onStart()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isRunning).isFalse()
        assertThat(state.done).isEqualTo(3)
        assertThat(state.total).isEqualTo(3)
        // Итог собран из переводимых частей: у каждой своё число (docs/tdd.md §5).
        assertThat(state.message).contains(AppText(R.string.settings_optimization_optimized, 2))
        assertThat(state.message).contains(
            AppText(R.string.settings_optimization_saved, freedBytesText(2_097_152)),
        )
        assertThat(state.message).contains(AppText(R.string.settings_optimization_already_best, 1))
    }

    @Test
    fun `a run stopped by the reader says so`() = runTest(dispatcher) {
        books.batchProgress = listOf(0 to 5, 1 to 5)
        val batch = BookOptimizationBatch(
            total = 5,
            optimized = 1,
            skipped = 0,
            failed = 0,
            savedBytes = 1024,
            stoppedByReader = true,
        )
        books.optimizationBatch = AppResult.success(batch)
        val viewModel = BookOptimizationViewModel(books)

        viewModel.onStart()
        advanceUntilIdle()

        // «Готово» здесь было бы ложью: обработана одна книга из пяти, и это сказано первой частью.
        assertThat(viewModel.uiState.value.message).contains(
            AppText(R.string.settings_optimization_stopped_by_reader, batch.processed, batch.total),
        )
    }

    @Test
    fun `a library without fb2 is reported honestly`() = runTest(dispatcher) {
        books.batchProgress = listOf(0 to 0)
        books.optimizationBatch = AppResult.success(
            BookOptimizationBatch(
                total = 0,
                optimized = 0,
                skipped = 0,
                failed = 0,
                savedBytes = 0,
                stoppedByReader = false,
            ),
        )
        val viewModel = BookOptimizationViewModel(books)

        viewModel.onStart()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.message)
            .containsExactly(AppText(R.string.settings_optimization_no_books))
    }

    @Test
    fun `stop really interrupts the run and keeps the progress it had`() = runTest(dispatcher) {
        books.batchProgress = listOf(0 to 4, 1 to 4, 2 to 4, 3 to 4, 4 to 4)
        books.batchGate = CompletableDeferred()
        val viewModel = BookOptimizationViewModel(books)

        viewModel.onStart()
        // Останавливаем на первой же паузе: прогон уже сообщил общее число книг и ждёт сигнала.
        runCurrent()
        viewModel.onStop()
        advanceUntilIdle()

        assertThat(books.batchCancelled).isTrue()
        val state = viewModel.uiState.value
        assertThat(state.isRunning).isFalse()
        assertThat(state.message).contains(AppText(R.string.settings_optimization_stopped, 0, 4))
        // Общее число книг сохраняется в сообщении: «обработано 0 из 4» честнее, чем «остановлено».
        assertThat(state.total).isEqualTo(4)
    }

    @Test
    fun `a second start while running does not begin a second run`() = runTest(dispatcher) {
        books.batchProgress = listOf(0 to 2, 1 to 2, 2 to 2)
        books.batchGate = CompletableDeferred()
        val viewModel = BookOptimizationViewModel(books)

        viewModel.onStart()
        runCurrent()
        viewModel.onStart()
        advanceUntilIdle()

        assertThat(books.batchRuns).isEqualTo(1)
    }

    @Test
    fun `books checked earlier are reported as skipped`() = runTest(dispatcher) {
        books.batchProgress = listOf(0 to 0)
        books.optimizationBatch = AppResult.success(
            BookOptimizationBatch(
                total = 0,
                optimized = 0,
                skipped = 0,
                failed = 0,
                savedBytes = 0,
                stoppedByReader = false,
                alreadyOptimized = 78,
            ),
        )
        val viewModel = BookOptimizationViewModel(books)

        viewModel.onStart()
        advanceUntilIdle()

        // «Книг FB2 нет» здесь было бы ложью: книги есть, их просто уже проверяли.
        assertThat(viewModel.uiState.value.message)
            .containsExactly(AppText(R.string.settings_optimization_all_checked))
    }

    @Test
    fun `stopping is visible immediately, before the current book is finished`() = runTest(dispatcher) {
        books.batchProgress = listOf(0 to 4, 1 to 4, 2 to 4, 3 to 4, 4 to 4)
        books.batchGate = CompletableDeferred()
        val viewModel = BookOptimizationViewModel(books)

        viewModel.onStart()
        runCurrent()
        viewModel.onStop()

        // Текущая книга дорабатывается в памяти, и мгновенно её не оборвать: человек обязан
        // видеть, что остановка принята, а не ждать молча минуту.
        assertThat(viewModel.uiState.value.isStopping).isTrue()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.isStopping).isFalse()
    }

    @Test
    fun `a failure is shown with its reason`() = runTest(dispatcher) {
        val failure = AppError.Unknown(message = "Не удалось прочитать файл")
        books.optimizationBatch = AppResult.failure(failure)
        val viewModel = BookOptimizationViewModel(books)

        viewModel.onStart()
        advanceUntilIdle()

        // Причина — текст по типу ошибки: сообщение нижнего слоя человеку не показывают (docs/tdd.md §7).
        assertThat(viewModel.uiState.value.error)
            .isEqualTo(AppText(R.string.settings_optimization_failed, failure.text()))
        assertThat(viewModel.uiState.value.isRunning).isFalse()
    }

    @Test
    fun `a failed book is counted but does not stop the run`() = runTest(dispatcher) {
        books.batchProgress = listOf(0 to 2, 1 to 2, 2 to 2)
        books.optimizationBatch = AppResult.success(
            BookOptimizationBatch(
                total = 2,
                optimized = 1,
                skipped = 0,
                failed = 1,
                savedBytes = 512,
                stoppedByReader = false,
            ),
        )
        val viewModel = BookOptimizationViewModel(books)

        viewModel.onStart()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.message)
            .contains(AppText(R.string.settings_optimization_failed_count, 1))
    }
}
