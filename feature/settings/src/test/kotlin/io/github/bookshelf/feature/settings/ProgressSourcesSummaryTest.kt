package io.github.bookshelf.feature.settings

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.designsystem.text.AppText
import org.junit.Test

/**
 * Сводка строки «Прогресс чтения» на первом уровне экрана «Читалки».
 *
 * Блок переключателей переехал на отдельный подэкран (требование Александра 05.10.2026 — «а то экран
 * сильно разрастается»), а на первом уровне осталась одна строка. Её подпись должна отвечать на
 * вопрос «включено ли что-нибудь и что именно», иначе строка выглядит как пустая настройка.
 *
 * Текст строится вне Composable, поэтому это [AppText]: ресурс плюс аргументы, а не готовая строка —
 * так подпись переводится вместе с остальным интерфейсом.
 */
class ProgressSourcesSummaryTest {

    @Test
    fun `nothing switched on says so`() {
        val summary = progressSourcesSummary(AppSettingsUi())

        assertThat(summary.res).isEqualTo(R.string.settings_readers_progress_none)
        assertThat(summary.arg1).isNull()
    }

    @Test
    fun `one switched-on reader is named`() {
        val summary = progressSourcesSummary(AppSettingsUi(isKoReaderProgressEnabled = true))

        assertThat(summary.res).isEqualTo(R.string.settings_readers_progress_one)
        assertThat(summary.arg1).isEqualTo(AppText(R.string.settings_readers_progress_source_koreader))
    }

    @Test
    fun `several readers are named by the first one and counted`() {
        // По порядку опроса (ProgressSourceId): FBReader → KOReader → CoolReader → PocketBook →
        // Librera, чтобы подпись не менялась от порядка включения.
        val summary = progressSourcesSummary(
            AppSettingsUi(
                isKoReaderProgressEnabled = true,
                isPocketBookProgressEnabled = true,
                isCoolReaderProgressEnabled = true,
            ),
        )

        assertThat(summary.res).isEqualTo(R.string.settings_readers_progress_many)
        assertThat(summary.arg1).isEqualTo(AppText(R.string.settings_readers_progress_source_koreader))
        assertThat(summary.arg2).isEqualTo(2)
    }

    @Test
    fun `the fbReader chain starts the summary`() {
        val summary = progressSourcesSummary(
            AppSettingsUi(isFbReaderProgressEnabled = true, isLibreraProgressEnabled = true),
        )

        assertThat(summary.arg1).isEqualTo(AppText(R.string.settings_readers_progress_source_fbreader))
        assertThat(summary.arg2).isEqualTo(1)
    }
}
