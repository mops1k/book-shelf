package io.github.bookshelf.feature.scan

import io.github.bookshelf.core.data.model.ScanStatus

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

/** Подпись состояния прогона для строки под заголовком. */
fun scanStatusLabel(status: ScanStatus): String = when (status) {
    ScanStatus.IDLE -> "Скан не запущен"
    ScanStatus.RUNNING -> "Идёт сканирование"
    ScanStatus.PAUSED -> "Пауза — найденное сохранено"
    ScanStatus.CANCELLED -> "Скан остановлен"
    ScanStatus.FINISHED -> "Скан завершён"
    ScanStatus.FAILED -> "Скан прерван ошибкой"
}

/**
 * Подпись строки прогресса.
 *
 * Общее число файлов показывается только тогда, когда оно известно, и с тильдой: это оценка по
 * прошлому прогону (`sources.documentCount`), а не посчитанное заранее число файлов. Раньше на
 * этом месте стояло «58 из 0» — цифра, которой не существует.
 */
fun scanProgressCaption(progress: ScanProgressUi?): String {
    if (progress == null) return "Сканирование ещё не запускалось"
    val total = progress.filesTotal
    return if (total != null && total > 0) {
        "Просмотрено ${progress.filesSeen} из ~$total"
    } else {
        "Просмотрено файлов: ${progress.filesSeen}"
    }
}
