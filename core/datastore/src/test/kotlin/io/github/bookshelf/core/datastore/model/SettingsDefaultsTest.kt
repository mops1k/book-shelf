package io.github.bookshelf.core.datastore.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Тест значений по умолчанию [AppSettings] («E-ink UX» §2, §4, §6.8).
 *
 * Значения зафиксированы здесь буквально, а не ссылками на `SettingsKeys.Defaults`: смысл теста —
 * поймать случайное изменение документально согласованного дефолта (плотность «Крупный», кегль
 * 100 %, полный refresh при смене экрана/страницы/диалога, тап-зоны 25/25, параллелизм парсеров 2,
 * лимит временной копии 100 МБ, лимит кэша обложек 200 МБ, очистка пропавших через 30 дней).
 */
class SettingsDefaultsTest {

    @Test
    fun appearance_defaults_match_documented_values() {
        val appearance = AppSettings().appearance

        assertThat(appearance.themeMode).isEqualTo(ThemeMode.LIGHT)
        assertThat(appearance.density).isEqualTo(EinkDensityLike.LARGE)
        assertThat(appearance.fontScale).isEqualTo(100)
        assertThat(appearance.invertCovers).isFalse()
        assertThat(appearance.textOnlyMode).isFalse()
    }

    @Test
    fun eink_defaults_refresh_every_documented_event() {
        val eink = AppSettings().eink

        assertThat(eink.refreshMode).isEqualTo(RefreshModeLike.AUTO)
        assertThat(eink.refreshOnScreenChange).isTrue()
        assertThat(eink.refreshOnDialog).isTrue()
        assertThat(eink.refreshOnPageChange).isTrue()
        assertThat(eink.frontLightLevel).isNull()
        assertThat(eink.volumeKeysAsPageTurn).isFalse()
        assertThat(eink.tapZoneLeftPercent).isEqualTo(25)
        assertThat(eink.tapZoneRightPercent).isEqualTo(25)
    }

    @Test
    fun library_defaults_match_documented_limits() {
        val library = AppSettings().library

        assertThat(library.gridColumns).isEqualTo(2)
        // Полка по умолчанию открывается сеткой обложек: обзор по категориям — осознанный выбор.
        assertThat(library.viewPreset).isEqualTo(ShelfViewPreset.GRID)
        assertThat(library.sortOrder).isEqualTo(SortOrder.TITLE)
        assertThat(library.sortDescending).isFalse()
        assertThat(library.grouping).isEqualTo(Grouping.NONE)
        assertThat(library.scanSchedule).isEqualTo(ScanSchedule.MANUAL)
        assertThat(library.missingCleanupDays).isEqualTo(30)
        assertThat(library.metadataParallelism).isEqualTo(2)
        assertThat(library.tempCopyLimitMb).isEqualTo(100)
        assertThat(library.coverCacheLimitMb).isEqualTo(200)
    }

    @Test
    fun reader_and_opds_defaults_are_conservative() {
        val settings = AppSettings()

        assertThat(settings.reader.defaultReaderByFormat).isEmpty()
        assertThat(settings.reader.lastUsedReaderByFormat).isEmpty()
        assertThat(settings.reader.enableFbReaderProgress).isFalse()
        assertThat(settings.opds.enableOpds).isTrue()
        assertThat(settings.opds.allowHttp).isFalse()
        assertThat(settings.advanced.dbBackupEnabled).isTrue()
    }
}
