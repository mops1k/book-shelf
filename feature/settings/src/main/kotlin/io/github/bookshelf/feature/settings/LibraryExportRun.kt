package io.github.bookshelf.feature.settings

import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.common.error.errorOrNull
import io.github.bookshelf.core.common.error.getOrNull
import io.github.bookshelf.core.data.export.LibraryExportSummary

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
    val message: String? = null,
    val error: String? = null,
)

/** Итог выгрузки словами: сколько книг и полок записано, либо причина сбоя. */
internal fun libraryExportReport(result: AppResult<LibraryExportSummary>): LibraryExportRun {
    val summary = result.getOrNull()
        ?: return LibraryExportRun(
            error = result.errorOrNull()?.message?.takeIf { message -> message.isNotBlank() }
                ?: "Не удалось выгрузить библиотеку",
        )
    return LibraryExportRun(
        message = "Экспортировано книг: ${summary.books}, полок: ${summary.collections}",
    )
}
