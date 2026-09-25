package io.github.bookshelf.feature.scan

/**
 * Чистая арифметика прогресса сканирования (docs/eink-ux.md §6.6).
 *
 * Вынесена из ViewModel и Composable отдельным файлом: именно эти правила решают, что
 * покажет строка «Просканировано 4 300 из 12 000, продолжить», и их проверяет JUnit-тест
 * без устройства.
 */

/** Полная шкала процентов. */
const val PERCENT_MAX: Int = 100

/** Прогресс скана в процентах; при неизвестном общем числе файлов — 0. */
fun scanPercent(filesSeen: Int, filesTotal: Int?): Int {
    if (filesTotal == null || filesTotal <= 0) return 0
    val raw = (filesSeen.toLong() * PERCENT_MAX) / filesTotal
    return raw.coerceIn(0L, PERCENT_MAX.toLong()).toInt()
}

/** Прогресс скана долей 0..1 — в таком виде его ждёт `EinkProgress`. */
fun scanProgressFraction(filesSeen: Int, filesTotal: Int?): Float =
    scanPercent(filesSeen, filesTotal) / PERCENT_MAX.toFloat()

/**
 * Скан можно продолжить с сохранённого места: он не идёт и пройдены не все файлы.
 *
 * Возобновляемость — архитектурное требование (ADR-010): foreground-скан может быть
 * прерван системой в любой момент, и пользователь должен увидеть «продолжить».
 */
fun canResumeScan(isRunning: Boolean, filesSeen: Int, filesTotal: Int?): Boolean =
    !isRunning && filesTotal != null && filesSeen < filesTotal
