package io.github.bookshelf.feature.settings

import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.common.error.errorOrNull
import io.github.bookshelf.core.common.error.getOrNull
import io.github.bookshelf.core.data.export.LibraryExportSummary
import io.github.bookshelf.core.designsystem.text.AppText
import io.github.bookshelf.core.designsystem.text.text

/**
 * Что показать человеку после выгрузки библиотеки.
 *
 * Успех и сбой — разные поля, а не один текст: строка настройки показывает результат, а баннер
 * ошибки берёт своё поле, и «не удалось выгрузить» не должно выглядеть как сообщение о выгрузке.
 *
 * Вынесено отдельной чистой функцией по той же причине, что и подсчёт освобождённого кэша
 * обложек: текст результата проверяется без корутин, `Uri` и Android-диалогов — то есть тестом,
 * который падает по нужной причине, а не по «не удалось собрать экран».
 */
internal data class LibraryExportRun(
    val message: AppText? = null,
    val error: AppText? = null,
)

/** Итог выгрузки словами: сколько книг и полок записано, либо причина сбоя. */
internal fun libraryExportReport(result: AppResult<LibraryExportSummary>): LibraryExportRun {
    val summary = result.getOrNull()
        ?: return LibraryExportRun(
            // Причина — текст по типу ошибки: `AppError.message` человеку не показывается («TDD» §7).
            error = AppText(
                R.string.settings_export_failed,
                (result.errorOrNull() ?: AppError.Unknown()).text(),
            ),
        )
    return LibraryExportRun(
        message = AppText(R.string.settings_export_report, summary.books, summary.collections),
    )
}
