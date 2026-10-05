package io.github.bookshelf.core.data.sync

/**
 * Свести исходы источников к одному итогу для журнала и диагностики.
 *
 * Источников несколько, а рассказать о проходе надо одним предложением, поэтому важен порядок
 * приоритетов:
 * 1. **сохранённый прогресс** — самое важное: человек читал, данные обновились;
 * 2. **недоступная читалка** — то, что человек может исправить сам (включить доступ, обновить
 *    читалку), и о ней надо сказать;
 * 3. **неизвестная книга** — скорее всего, читали не из нашей полки; полезно в журнале, но не
 *    требует действий;
 * 4. **ничего не открыто** — самый тихий исход: он бывает на каждом срабатывании по таймеру и не
 *    должен выглядеть как проблема.
 *
 * Пустой список означает «ни один источник не включён»: спрашивать было некого.
 */
fun summarizeSyncOutcomes(outcomes: List<ProgressSyncOutcome>): ProgressSyncOutcome {
    if (outcomes.isEmpty()) return ProgressSyncOutcome.Disabled
    outcomes.firstOrNull { it is ProgressSyncOutcome.Saved }?.let { return it }
    outcomes.firstOrNull { it is ProgressSyncOutcome.ReaderUnavailable }?.let { return it }
    outcomes.firstOrNull { it is ProgressSyncOutcome.BookUnknown }?.let { return it }
    return ProgressSyncOutcome.NoBookOpen
}
