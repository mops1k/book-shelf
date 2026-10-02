package io.github.bookshelf.feature.settings

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.designsystem.text.AppText
import io.github.bookshelf.core.designsystem.text.text
import io.github.bookshelf.core.common.time.AppClock
import io.github.bookshelf.core.data.export.LibraryExportContent
import io.github.bookshelf.core.data.export.LibraryExportRepository
import io.github.bookshelf.core.data.export.LibraryExportSummary
import io.mockk.mockk
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Выгрузка библиотеки из настроек (`LibraryExportViewModel`).
 *
 * Проверяется то, чего до этой задачи не было вовсе: нажатие строки «Экспорт библиотеки» доводит
 * дело до записи файла **в выбранный человеком документ**, а строка настройки называет числа книг
 * и полок. Сбой обязан выглядеть сбоем, а не «выгружено 0 книг».
 *
 * Собственный интерфейс подменён фейком (правило проекта), `mockk` применён только к
 * `android.net.Uri` — типу Android SDK, который в обычном JVM-тесте не создать.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryExportViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val export = FakeLibraryExport()
    private val clock = object : AppClock {
        override fun nowMillis(): Long = NOW
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `export writes into the picked document and reports the counts`() = runTest(dispatcher) {
        val viewModel = viewModel()
        val destination = mockk<Uri>()
        export.result = AppResult.success(LibraryExportSummary(books = 74, collections = 3))

        viewModel.onExport(destination)
        advanceUntilIdle()

        assertThat(export.destinations).containsExactly(destination)
        assertThat(viewModel.uiState.value.message).isEqualTo(AppText(R.string.settings_export_report, 74, 3))
        assertThat(viewModel.uiState.value.isExporting).isFalse()
        assertThat(viewModel.uiState.value.error).isNull()
    }

    @Test
    fun `failed export is an error, not a finished export`() = runTest(dispatcher) {
        val viewModel = viewModel()
        val failure = AppError.Io(message = "Нет места на диске")
        export.result = AppResult.failure(failure)

        viewModel.onExport(mockk<Uri>())
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.message).isNull()
        assertThat(viewModel.uiState.value.error)
            .isEqualTo(AppText(R.string.settings_export_failed, failure.text()))
        assertThat(viewModel.uiState.value.isExporting).isFalse()
    }

    @Test
    fun `second tap while exporting does not write the file twice`() = runTest(dispatcher) {
        val viewModel = viewModel()
        val destination = mockk<Uri>()

        viewModel.onExport(destination)
        // Первый запуск ещё не дошёл до репозитория: повторное нажатие не должно начать вторую
        // запись в тот же документ — два потока на один файл дали бы битый JSON.
        viewModel.onExport(destination)
        advanceUntilIdle()

        assertThat(export.destinations).hasSize(1)
    }

    @Test
    fun `suggested file name carries the date of the export`() {
        val expectedDate = Instant.ofEpochMilli(NOW).atZone(ZoneId.systemDefault()).toLocalDate()

        assertThat(viewModel().suggestedFileName())
            .isEqualTo("bookshelf-export-$expectedDate.json")
    }

    private fun viewModel() = LibraryExportViewModel(libraryExport = export, clock = clock)

    /** Выгрузка: запоминает документ и отдаёт заранее заданный итог. */
    private class FakeLibraryExport : LibraryExportRepository {

        var result: AppResult<LibraryExportSummary> =
            AppResult.success(LibraryExportSummary(books = 0, collections = 0))

        val destinations = mutableListOf<Uri>()

        override suspend fun snapshot(): LibraryExportContent =
            LibraryExportContent(books = emptyList(), collections = emptyList())

        override suspend fun exportTo(destination: Uri): AppResult<LibraryExportSummary> {
            destinations += destination
            return result
        }
    }

    private companion object {
        const val NOW = 1_790_000_000_000L
    }
}
