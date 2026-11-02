package io.github.bookshelf

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.RequiresDevice
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.applist.catalog.AndroidAppCatalog
import io.github.bookshelf.core.applist.rule.AppListRules
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import io.github.bookshelf.core.common.testing.TestAppDispatchers

/**
 * Каталог приложений на живом устройстве (ADR-015).
 *
 * Тест сверяет наш список с тем, что система отдаёт по сигнатуре `MAIN`/`LAUNCHER` напрямую:
 * он не повторяет логику каталога, а проверяет, что `<queries>` работает и все приложения видны.
 * `@RequiresDevice`: на чистом эмуляторе установлено два-три приложения, и проверка «список не
 * пуст, иконки читаются» доказывала бы только наличие эмулятора.
 */
@RunWith(AndroidJUnit4::class)
@RequiresDevice
class AppListInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun catalog() = AndroidAppCatalog(context, TestAppDispatchers())

    @Test
    fun catalog_matches_launcher_apps_api() {
        val found = runBlocking { catalog().apps() }
            .map { app -> app.packageName }
            .toSet()

        // Эталон — тот же API, которым строят список системные лаунчеры, включая Google:
        // `queryIntentActivities` отдаёт лишние служебные активности, LauncherApps — нет.
        val expected = context.getSystemService(LauncherApps::class.java)
            .getActivityList(null, Process.myUserHandle())
            .map { info -> info.componentName.packageName }
            .toSet()

        assertThat(found).isEqualTo(expected)
        // На целевом устройстве приложений много: пустой список означал бы сломанные <queries>.
        assertThat(found.size).isAtLeast(10)
    }

    @Test
    fun every_app_has_label_activity_and_icon() {
        val apps = runBlocking { catalog().apps() }

        assertThat(apps).isNotEmpty()
        apps.forEach { app ->
            assertThat(app.label.isNotBlank()).isTrue()
            assertThat(app.activityName.isNotBlank()).isTrue()
            assertThat(context.packageManager.getApplicationIcon(app.packageName)).isNotNull()
        }
    }

    @Test
    fun hidden_and_system_apps_are_filtered_by_rules_not_by_catalog() {
        val catalog = catalog()
        val apps = runBlocking { catalog.apps() }
        val visible = AppListRules.visible(
            apps = apps,
            hiddenPackages = emptySet(),
            showSystem = false,
            ownPackage = catalog.ownPackage,
        )

        assertThat(visible.map { app -> app.packageName }).doesNotContain(catalog.ownPackage)
        assertThat(visible.none { app -> app.isSystem }).isTrue()
    }
}
