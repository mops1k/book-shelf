package io.github.bookshelf.feature.scan

import io.github.bookshelf.core.data.model.ScanStatus

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
 * Скан можно продолжить с сохранённого места: прогон стоит на паузе.
 *
 * Признак берётся из состояния прогона, а не из «пройдены не все файлы»: общее число файлов
 * заранее неизвестно (`totalKnown` в базе остаётся пустым), поэтому прежнее правило с `filesTotal`
 * не давало нажать «Продолжить» никогда — кнопка была отключена всегда (найдено на устройстве
 * 28.09.2026).
 */
fun canResumeScan(status: ScanStatus, filesSeen: Int): Boolean =
    status == ScanStatus.PAUSED && filesSeen >= 0
