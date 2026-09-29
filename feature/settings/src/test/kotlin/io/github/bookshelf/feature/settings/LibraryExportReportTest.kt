package io.github.bookshelf.feature.settings

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.data.export.LibraryExportSummary
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

        assertThat(run.message).isEqualTo("Экспортировано книг: 74, полок: 3")
        assertThat(run.error).isNull()
    }

    @Test
    fun `failure carries the cause and no success message`() {
        val run = libraryExportReport(
            AppResult.failure(AppError.Io(message = "Нет места на диске")),
        )

        assertThat(run.message).isNull()
        assertThat(run.error).isEqualTo("Нет места на диске")
    }

    @Test
    fun `failure without a message is still reported honestly`() {
        val run = libraryExportReport(AppResult.failure(AppError.Unknown()))

        // Пустая причина не должна оставить человека без объяснения: «ничего не произошло» —
        // худший ответ на нажатие кнопки.
        assertThat(run.error).isEqualTo("Не удалось выгрузить библиотеку")
    }
}
