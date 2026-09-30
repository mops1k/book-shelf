package io.github.bookshelf.feature.scan

import io.github.bookshelf.core.data.model.ScanStatus
import io.github.bookshelf.core.designsystem.text.AppText
import io.github.bookshelf.feature.scan.R

/**
 * Что показывает и предлагает экран сканирования.
 *
 * Вынесено из Composable и ViewModel отдельными чистыми функциями: это те же правила, что и
 * арифметика прогресса, — «идёт скан или стоит», «можно продолжить», «какая подпись у строки
 * прогресса», — и их проверяет JUnit без устройства.
 */

/** Что можно нажать в текущем состоянии скана. */
data class ScanActionsUi(
    /** Идёт прогон — доступна «Пауза». */
    val canPause: Boolean = false,
    /** Прогон стоит на паузе с сохранённым курсором — доступно «Продолжить». */
    val canResume: Boolean = false,
    /** Уйти на полку можно всегда: скан идёт в фоне, экран не держит его. */
    val canOpenLibrary: Boolean = true,
)

/**
 * Кнопки по состоянию прогона.
 *
 * Отмены в интерфейсе нет намеренно (решение Александра, 28.09.2026): скан прерываемый, и
 * единственное осмысленное действие — «Пауза». Она мягкая: найденное и курсор сохраняются,
 * «Продолжить» возвращается к тому же месту.
 */
fun scanActionsOf(status: ScanStatus): ScanActionsUi = when (status) {
    ScanStatus.RUNNING -> ScanActionsUi(canPause = true)
    ScanStatus.PAUSED -> ScanActionsUi(canResume = true)
    else -> ScanActionsUi()
}

/**
 * Показывать ли кнопку «К отчёту».
 *
 * Отчёт есть — значит, прогон завершён, и смотреть там есть что: сколько добавлено и какие файлы
 * не разобрались. Раньше отчёт открывался только клавишей MENU, и с сенсорного экрана входа к
 * нему не было вовсе — раздел «Проблемные файлы» оставался недостижимым (проверено на устройстве
 * 30.09.2026).
 */
fun canOpenReport(report: ScanReportUi?, isRunning: Boolean): Boolean = report != null && !isRunning

/**
 * Подпись состояния прогона для строки под заголовком.
 *
 * Возвращается ресурс, а не текст: слова живут в `res/values` (ru) и `values-en`, а выбор подписи
 * остаётся чистой функцией и проверяется без Compose.
 */
fun scanStatusLabel(status: ScanStatus): Int = when (status) {
    ScanStatus.IDLE -> R.string.scan_status_idle
    ScanStatus.RUNNING -> R.string.scan_status_running
    ScanStatus.PAUSED -> R.string.scan_status_paused
    ScanStatus.CANCELLED -> R.string.scan_status_cancelled
    ScanStatus.FINISHED -> R.string.scan_status_finished
    ScanStatus.FAILED -> R.string.scan_status_failed
}

/**
 * Подпись строки прогресса.
 *
 * Общее число файлов показывается только тогда, когда оно известно, и с тильдой: это оценка по
 * прошлому прогону (`sources.documentCount`), а не посчитанное заранее число файлов. Раньше на
 * этом месте стояло «58 из 0» — цифра, которой не существует.
 */
fun scanProgressCaption(progress: ScanProgressUi?): AppText {
    if (progress == null) return AppText(R.string.scan_caption_none)
    val total = progress.filesTotal
    return if (total != null && total > 0) {
        AppText(R.string.scan_caption_seen_total, progress.filesSeen, total)
    } else {
        AppText(R.string.scan_caption_seen, progress.filesSeen)
    }
}
