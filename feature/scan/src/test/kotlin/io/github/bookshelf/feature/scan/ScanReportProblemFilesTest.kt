package io.github.bookshelf.feature.scan

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.common.error.ParseFailure
import io.github.bookshelf.core.data.model.Book
import io.github.bookshelf.core.data.model.ParseState
import io.github.bookshelf.core.data.model.ScanReport
import io.github.bookshelf.core.designsystem.text.AppText
import io.github.bookshelf.core.testing.fake.FakeBookRepository
import io.github.bookshelf.core.testing.fake.FakeScanRepository
import io.github.bookshelf.core.testing.fake.FakeSourceRepository
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
 * Проблемные файлы в отчёте сканирования (`ScanViewModel`).
 *
 * Дефект, ради которого это проверяется: раздел «Проблемные файлы» рисовался, но данные в него
 * никто не клал — экран всегда сообщал «Проблемных файлов нет», даже когда в библиотеке было
 * 19 книг с `parseState = FAILED` (проверено на устройстве 30.09.2026). Причины отказа разбора
 * приходят не из статистики прогона (там только число), а из строк книг.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScanReportProblemFilesTest {

    private val dispatcher = StandardTestDispatcher()
    private val scans = FakeScanRepository()
    private val sources = FakeSourceRepository()
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
    fun `the report names the files that failed to parse`() = runTest(dispatcher) {
        scans.reports = listOf(
            ScanReport(runId = "run-1", sourceId = "source-1", startedAt = 1L, added = 3, failedCount = 2),
        )
        books.parseErrors = AppResult.success(
            listOf(
                book(id = "book-1", name = "Проверка_1.docx", reason = ParseFailure.FORMAT_UNSUPPORTED),
                book(id = "book-2", name = "Уткин.fb2", reason = ParseFailure.ARCHIVE_LIMIT),
            ),
        )

        val viewModel = ScanViewModel(scans, sources, books)
        advanceUntilIdle()

        val files = viewModel.uiState.value.report?.problemFiles.orEmpty()
        assertThat(files).hasSize(2)
        assertThat(files.map { file -> file.fileName })
            .containsExactly("Проверка_1.docx", "Уткин.fb2")
        // Причина — код из базы, слова выбирает экран (docs/tdd.md §8).
        assertThat(files.map { file -> file.parseFailure }).containsExactly(
            AppText(io.github.bookshelf.core.designsystem.R.string.parse_failure_format_unsupported),
            AppText(io.github.bookshelf.core.designsystem.R.string.parse_failure_archive_limit),
        )
    }

    @Test
    fun `a book without a recorded reason is still shown`() = runTest(dispatcher) {
        scans.reports = listOf(ScanReport(runId = "run-1", sourceId = "source-1", startedAt = 1L, failedCount = 1))
        books.parseErrors = AppResult.success(
            listOf(book(id = "book-1", name = "Странный.mobi", reason = null)),
        )

        val viewModel = ScanViewModel(scans, sources, books)
        advanceUntilIdle()

        val file = viewModel.uiState.value.report?.problemFiles?.single()
        // Молчать о такой книге нельзя: она в списке. Причина не записана — значит `null`,
        // а переводимый текст подставляет экран (docs/tdd.md §5); хранить готовую строку нельзя.
        assertThat(file?.fileName).isEqualTo("Странный.mobi")
        assertThat(file?.parseFailure).isNull()
    }

    @Test
    fun `no report means no problem files section`() = runTest(dispatcher) {
        scans.reports = emptyList()

        val viewModel = ScanViewModel(scans, sources, books)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.report).isNull()
    }

    private fun book(id: String, name: String, reason: ParseFailure?) = Book(
        id = id,
        title = name,
        authorsText = "",
        extension = "docx",
        sizeBytes = 10L,
        addedAt = 1L,
        parseState = ParseState.FAILED,
        parseFailure = reason,
    )
}
