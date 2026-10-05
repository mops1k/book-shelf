package io.github.bookshelf.feature.settings

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.data.sync.ProgressSourceStatus
import io.github.bookshelf.core.data.sync.ProgressSourcesReport
import io.github.bookshelf.core.readerlink.progress.ProgressSourceKeys
import org.junit.Test

/**
 * Строки диагностики источников прогресса.
 *
 * Читаемые и нечитаемые идут **разными строками** (просьба Александра 05.10.2026: «пускай будут на
 * разных строках, одна под другой, иначе не умещается на экран»). Порядок имён внутри строки — тот
 * же, что у переключателей на подэкране прогресса: FBReader → KOReader → CoolReader → … → XReader.
 */
class ProgressSourcesLinesTest {

    @Test
    fun `readable and unreadable sources go to different lines`() {
        val lines = progressSourcesLines(
            report(
                ProgressSourceStatus(ProgressSourceKeys.KOREADER_SIDECAR, available = true),
                ProgressSourceStatus(ProgressSourceKeys.COOLREADER_DB, available = false),
            ),
        )

        assertThat(lines).hasSize(2)
        assertThat(lines[0].titleRes).isEqualTo(R.string.settings_about_progress_sources_read)
        assertThat(lines[0].names).containsExactly(R.string.settings_readers_progress_source_koreader)
        assertThat(lines[1].titleRes).isEqualTo(R.string.settings_about_progress_sources_failed)
        assertThat(lines[1].names).containsExactly(R.string.settings_readers_progress_source_coolreader)
    }

    @Test
    fun `when everything is readable there is no second line`() {
        // Пустая строка «Не читается» выглядела бы как поломка: её просто нет.
        val lines = progressSourcesLines(
            report(
                ProgressSourceStatus(ProgressSourceKeys.KOREADER_SIDECAR, available = true),
                ProgressSourceStatus(ProgressSourceKeys.XREADER_CACHE, available = true),
            ),
        )

        assertThat(lines).hasSize(1)
        assertThat(lines.single().titleRes).isEqualTo(R.string.settings_about_progress_sources_read)
        assertThat(lines.single().names).containsExactly(
            R.string.settings_readers_progress_source_koreader,
            R.string.settings_readers_progress_source_xreader,
        ).inOrder()
    }

    @Test
    fun `when nothing is readable only the failure line remains`() {
        val lines = progressSourcesLines(
            report(ProgressSourceStatus(ProgressSourceKeys.POCKETBOOK_DB, available = false)),
        )

        assertThat(lines.single().titleRes).isEqualTo(R.string.settings_about_progress_sources_failed)
    }

    @Test
    fun `an unknown source is shown instead of dropped`() {
        val lines = progressSourcesLines(report(ProgressSourceStatus("UNKNOWN_SOURCE", available = true)))

        assertThat(lines.single().names)
            .containsExactly(R.string.settings_about_progress_source_unknown)
    }

    @Test
    fun `an empty report gives no lines`() {
        assertThat(progressSourcesLines(report())).isEmpty()
    }

    private fun report(vararg statuses: ProgressSourceStatus) = ProgressSourcesReport(
        sources = statuses.toList(),
        lastSourceKey = null,
        lastBookTitle = null,
        lastUpdatedAt = null,
    )
}
