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
 * Установленные читалки приходят аргументом: удалённой читалки в приложении быть не должно
 * (требование Александра 06.10.2026), и подпись не должна называть то, чего в системе нет.
 *
 * Текст строится вне Composable, поэтому это [AppText]: ресурс плюс аргументы, а не готовая строка —
 * так подпись переводится вместе с остальным интерфейсом.
 */
class ProgressSourcesSummaryTest {

    @Test
    fun `nothing switched on says so`() {
        val summary = progressSourcesSummary(AppSettingsUi(), emptySet())

        assertThat(summary.res).isEqualTo(R.string.settings_readers_progress_none)
        assertThat(summary.arg1).isNull()
    }

    @Test
    fun `one switched-on reader is named`() {
        val summary = progressSourcesSummary(
            AppSettingsUi(isKoReaderProgressEnabled = true),
            setOf(KOREADER),
        )

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
            setOf(KOREADER, POCKETBOOK, COOLREADER),
        )

        assertThat(summary.res).isEqualTo(R.string.settings_readers_progress_many)
        assertThat(summary.arg1).isEqualTo(AppText(R.string.settings_readers_progress_source_koreader))
        assertThat(summary.arg2).isEqualTo(2)
    }

    @Test
    fun `the fbReader chain starts the summary`() {
        val summary = progressSourcesSummary(
            AppSettingsUi(isFbReaderProgressEnabled = true, isLibreraProgressEnabled = true),
            setOf(FBREADER, LIBRERA),
        )

        assertThat(summary.arg1).isEqualTo(AppText(R.string.settings_readers_progress_source_fbreader))
        assertThat(summary.arg2).isEqualTo(1)
    }

    @Test
    fun `a reader that is gone is not named`() {
        // Требование Александра (06.10.2026): удалённой читалки в приложении не видно нигде — значит,
        // включённый когда-то FBReader не должен попадать в подпись, если его нет в системе.
        val summary = progressSourcesSummary(
            AppSettingsUi(isFbReaderProgressEnabled = true, isCoolReaderProgressEnabled = true),
            setOf(COOLREADER),
        )

        assertThat(summary.res).isEqualTo(R.string.settings_readers_progress_one)
        assertThat(summary.arg1).isEqualTo(AppText(R.string.settings_readers_progress_source_coolreader))
    }

    @Test
    fun `an enabled reader that is not installed leaves nothing`() {
        val summary = progressSourcesSummary(
            AppSettingsUi(isFbReaderProgressEnabled = true),
            setOf(COOLREADER),
        )

        assertThat(summary.res).isEqualTo(R.string.settings_readers_progress_none)
    }

    private companion object {
        const val FBREADER = "com.fbreader"
        const val KOREADER = "org.koreader.launcher"
        const val COOLREADER = "org.coolreader"
        const val POCKETBOOK = "com.obreey.reader"
        const val LIBRERA = "com.foobnix.pro.pdf.reader"
    }
}
