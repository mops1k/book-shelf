package io.github.bookshelf.feature.settings

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.data.sync.ProgressSourceStatus
import io.github.bookshelf.core.data.sync.ProgressSourcesReport
import io.github.bookshelf.core.readerlink.progress.ProgressSourceKeys
import org.junit.Test

/**
 * Имена источников для строки диагностики прогресса.
 *
 * Строка одна на весь блок (замечание Александра 05.10.2026: «надо бы одной строкой, а то опять куча
 * однотипного растягивает экран»), и в ней называются **все** источники — и читаемые, и нет: без
 * читаемых непонятно, с чем приложение вообще может работать.
 */
class ProgressSourcesNamesTest {

    @Test
    fun `every source is named with its state`() {
        val names = progressSourcesNames(
            report(
                ProgressSourceStatus(ProgressSourceKeys.KOREADER_SIDECAR, available = true),
                ProgressSourceStatus(ProgressSourceKeys.COOLREADER_DB, available = false),
            ),
        )

        assertThat(names).containsExactly(
            R.string.settings_readers_progress_source_koreader to true,
            R.string.settings_readers_progress_source_coolreader to false,
        ).inOrder()
    }

    @Test
    fun `the order is the order of the sources asked`() {
        // Так же, как на подэкране прогресса: FBReader → KOReader → CoolReader → … → XReader.
        val names = progressSourcesNames(
            report(
                ProgressSourceStatus(ProgressSourceKeys.KOREADER_SIDECAR, available = true),
                ProgressSourceStatus(ProgressSourceKeys.COOLREADER_DB, available = true),
                ProgressSourceStatus(ProgressSourceKeys.XREADER_CACHE, available = false),
            ),
        )

        assertThat(names.map { it.first }).containsExactly(
            R.string.settings_readers_progress_source_koreader,
            R.string.settings_readers_progress_source_coolreader,
            R.string.settings_readers_progress_source_xreader,
        ).inOrder()
    }

    @Test
    fun `an unknown source is shown instead of dropped`() {
        val names = progressSourcesNames(report(ProgressSourceStatus("UNKNOWN_SOURCE", available = true)))

        assertThat(names).containsExactly(R.string.settings_about_progress_source_unknown to true)
    }

    @Test
    fun `an empty report gives no names`() {
        assertThat(progressSourcesNames(report())).isEmpty()
    }

    private fun report(vararg statuses: ProgressSourceStatus) = ProgressSourcesReport(
        sources = statuses.toList(),
        lastSourceKey = null,
        lastBookTitle = null,
        lastUpdatedAt = null,
    )
}
