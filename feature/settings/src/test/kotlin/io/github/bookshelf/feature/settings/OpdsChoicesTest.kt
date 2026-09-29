package io.github.bookshelf.feature.settings

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.datastore.SettingsKeys
import io.github.bookshelf.core.opds.download.DEFAULT_DOWNLOAD_FILE_NAME_TEMPLATE
import io.github.bookshelf.core.opds.download.DOWNLOAD_FILE_NAME_TEMPLATES
import org.junit.Test

/**
 * Варианты настроек OPDS и их умолчания (`OpdsChoices.kt`).
 *
 * Проверяется согласованность списков, которую глазами не увидеть: чипы на экране строятся из
 * подписей, а значение сохраняет и применяет загрузчик — если списки разойдутся, пользователь
 * нажмёт чип, который не сохранится, или не увидит шаблон, который приложение понимает.
 */
class OpdsChoicesTest {

    @Test
    fun `every template the downloader understands has a human label`() {
        assertThat(DOWNLOAD_FILE_NAME_TEMPLATE_LABELS.keys)
            .containsExactlyElementsIn(DOWNLOAD_FILE_NAME_TEMPLATES)
    }

    @Test
    fun `the downloader default is the documented settings default`() {
        // Строка умолчания записана и в `:core:opds`, и в `:core:datastore` (модуль загрузчика не
        // зависит от настроек). Расхождение показало бы в настройках один шаблон, а в имени
        // файла — другой.
        assertThat(DEFAULT_DOWNLOAD_FILE_NAME_TEMPLATE)
            .isEqualTo(SettingsKeys.Defaults.DOWNLOAD_FILE_NAME_TEMPLATE)
        assertThat(DOWNLOAD_FILE_NAME_TEMPLATES).contains(DEFAULT_DOWNLOAD_FILE_NAME_TEMPLATE)
    }

    @Test
    fun `the cache age chips contain the documented default`() {
        // Иначе сброс настроек вернул бы значение, которого нет ни на одном чипе: экран показывал
        // бы вариант, который нельзя выбрать заново.
        assertThat(FEED_CACHE_CHOICES_MINUTES)
            .contains(SettingsKeys.Defaults.FEED_CACHE_MAX_AGE_MINUTES)
    }
}
