package io.github.bookshelf.core.data.sync

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Итог прохода по источникам (`summarizeSyncOutcomes`).
 *
 * Источников стало несколько, а в журнал и в диагностику попадает один итог. Что именно показать,
 * когда одна читалка отдала прогресс, а другая промолчала, — не вкусовое решение:
 * * «сохранено» важнее всего: человек читал, прогресс обновился, остальное неинтересно;
 * * недоступная читалка важнее неизвестной книги: это то, что человек может исправить сам
 *   (включить доступ, обновить читалку), и о ней надо сказать;
 * * «ничего не открыто» — самый тихий исход: он бывает на каждом срабатывании по таймеру и не
 *   должен выглядеть как проблема.
 */
class ProgressSyncSummaryTest {

    @Test
    fun `saved progress wins over everything else`() {
        val saved = ProgressSyncOutcome.Saved(bookId = "book-1", percentPercent = 42f)

        val summary = summarizeSyncOutcomes(
            listOf(ProgressSyncOutcome.ReaderUnavailable, saved, ProgressSyncOutcome.NoBookOpen),
        )

        assertThat(summary).isEqualTo(saved)
    }

    @Test
    fun `unavailable reader is reported before an unknown book`() {
        val summary = summarizeSyncOutcomes(
            listOf(ProgressSyncOutcome.BookUnknown, ProgressSyncOutcome.ReaderUnavailable),
        )

        assertThat(summary).isEqualTo(ProgressSyncOutcome.ReaderUnavailable)
    }

    @Test
    fun `an unknown book is reported before nothing open`() {
        val summary = summarizeSyncOutcomes(
            listOf(ProgressSyncOutcome.NoBookOpen, ProgressSyncOutcome.BookUnknown),
        )

        assertThat(summary).isEqualTo(ProgressSyncOutcome.BookUnknown)
    }

    @Test
    fun `nothing to do stays quiet`() {
        val summary = summarizeSyncOutcomes(
            listOf(ProgressSyncOutcome.NoBookOpen, ProgressSyncOutcome.NoBookOpen),
        )

        assertThat(summary).isEqualTo(ProgressSyncOutcome.NoBookOpen)
    }

    @Test
    fun `an empty pass is disabled`() {
        // Пустой список означает «ни один источник не включён»: спрашивать было некого.
        assertThat(summarizeSyncOutcomes(emptyList())).isEqualTo(ProgressSyncOutcome.Disabled)
    }

    @Test
    fun `the first saved progress wins when two readers report`() {
        val first = ProgressSyncOutcome.Saved(bookId = "book-1", percentPercent = 10f)
        val second = ProgressSyncOutcome.Saved(bookId = "book-2", percentPercent = 20f)

        // Порядок источников детерминированный (по ProgressSourceId), поэтому и итог устойчив.
        assertThat(summarizeSyncOutcomes(listOf(first, second))).isEqualTo(first)
    }
}
