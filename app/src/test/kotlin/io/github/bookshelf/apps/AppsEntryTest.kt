package io.github.bookshelf.apps

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Точка входа экрана «Приложения» (ADR-015, фаза 6).
 *
 * Отдельная активити нужна ради аппаратной кнопки устройства: вендорский список показывает
 * приложения, а не компоненты, поэтому у нас должен быть собственный пункт запуска. Разбор
 * входа — чистая функция: интент пришёл с нашей action или с категорией LAUNCHER, значит это
 * отдельный запуск; всё остальное — вкладка внутри основного приложения.
 */
class AppsEntryTest {

    @Test
    fun show_apps_action_is_a_standalone_entry() {
        assertThat(AppsEntry.modeOf(action = AppsEntry.ACTION_SHOW_APPS, hasLauncherCategory = false))
            .isEqualTo(AppsEntryMode.STANDALONE)
    }

    @Test
    fun launcher_category_is_a_standalone_entry() {
        assertThat(AppsEntry.modeOf(action = null, hasLauncherCategory = true))
            .isEqualTo(AppsEntryMode.STANDALONE)
    }

    @Test
    fun in_app_navigation_stays_a_tab() {
        assertThat(AppsEntry.modeOf(action = null, hasLauncherCategory = false))
            .isEqualTo(AppsEntryMode.TAB)
    }

    @Test
    fun foreign_action_without_launcher_category_stays_a_tab() {
        assertThat(AppsEntry.modeOf(action = "android.intent.action.VIEW", hasLauncherCategory = false))
            .isEqualTo(AppsEntryMode.TAB)
    }

    @Test
    fun show_apps_action_value_is_stable() {
        // Строка дублируется в манифесте (alias и активити) и в TESTING.md — менять нельзя.
        assertThat(AppsEntry.ACTION_SHOW_APPS).isEqualTo("io.github.bookshelf.action.SHOW_APPS")
    }
}
