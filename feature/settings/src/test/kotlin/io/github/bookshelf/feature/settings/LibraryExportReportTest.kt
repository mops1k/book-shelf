package io.github.bookshelf.feature.settings

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.data.export.LibraryExportSummary
import io.github.bookshelf.core.designsystem.text.AppText
import io.github.bookshelf.core.designsystem.text.text
import org.junit.Test

/**
 * Текст результата «Экспорт библиотеки».
 *
 * Раньше на этом месте стояла заглушка «пока не поддерживается», поэтому проверяется именно то,
 * чего не было: успех называет число книг и полок (по нему человек видит, что выгрузка не пустая),
 * а сбой говорит причину и не выглядит успехом.
 */
class LibraryExportReportTest {

    @Test
    fun `success names how many books and shelves were written`() {
        val run = libraryExportReport(AppResult.success(LibraryExportSummary(books = 74, collections = 3)))

        assertThat(run.message).isEqualTo(AppText(R.string.settings_export_report, 74, 3))
        assertThat(run.error).isNull()
    }

    @Test
    fun `failure carries the cause and no success message`() {
        val failure = AppError.Io(message = "Нет места на диске")
        val run = libraryExportReport(AppResult.failure(failure))

        assertThat(run.message).isNull()
        // Причина — текст по типу ошибки: сообщение нижнего слоя человеку не показывают (docs/tdd.md §7).
        assertThat(run.error).isEqualTo(AppText(R.string.settings_export_failed, failure.text()))
    }

    @Test
    fun `failure without a message is still reported honestly`() {
        val run = libraryExportReport(AppResult.failure(AppError.Unknown()))

        // Пустая причина не должна оставить человека без объяснения: «ничего не произошло» —
        // худший ответ на нажатие кнопки.
        assertThat(run.error).isEqualTo(AppText(R.string.settings_export_failed, AppError.Unknown().text()))
    }
}
