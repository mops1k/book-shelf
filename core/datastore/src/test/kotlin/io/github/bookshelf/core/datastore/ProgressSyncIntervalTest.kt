package io.github.bookshelf.core.datastore

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Интервалы опроса читалки (`ProgressSyncInterval`).
 *
 * Проверяется защита от значения, которого в вариантах нет: настройку могли править вручную или она
 * осталась от прежней версии, а планировщик поставил бы работу с нулевым интервалом — то есть
 * бесконечный опрос чужого сервиса.
 */
class ProgressSyncIntervalTest {

    @Test
    fun `documented choices are accepted as they are`() {
        ProgressSyncInterval.CHOICES_MINUTES.forEach { minutes ->
            assertThat(ProgressSyncInterval.minutesOrDefault(minutes)).isEqualTo(minutes)
        }
    }

    @Test
    fun `unknown value falls back to the documented default`() {
        assertThat(ProgressSyncInterval.minutesOrDefault(7))
            .isEqualTo(SettingsKeys.Defaults.PROGRESS_SYNC_INTERVAL_MINUTES)
    }

    @Test
    fun `zero and negative values fall back to the default`() {
        assertThat(ProgressSyncInterval.minutesOrDefault(0))
            .isEqualTo(SettingsKeys.Defaults.PROGRESS_SYNC_INTERVAL_MINUTES)
        assertThat(ProgressSyncInterval.minutesOrDefault(-5))
            .isEqualTo(SettingsKeys.Defaults.PROGRESS_SYNC_INTERVAL_MINUTES)
    }

    @Test
    fun `the default is one of the choices and choices grow`() {
        assertThat(ProgressSyncInterval.CHOICES_MINUTES)
            .contains(SettingsKeys.Defaults.PROGRESS_SYNC_INTERVAL_MINUTES)
        assertThat(ProgressSyncInterval.CHOICES_MINUTES).isInStrictOrder()
    }
}
