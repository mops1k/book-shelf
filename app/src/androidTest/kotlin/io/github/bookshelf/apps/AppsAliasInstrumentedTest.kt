package io.github.bookshelf.apps

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.applist.rule.AppsAlias
import io.github.bookshelf.core.applist.rule.AppsAliasController
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Точка входа «Приложения» на устройстве (ADR-015, фаза 6).
 *
 * Проверяем то, что нельзя проверить на JVM: система разрешает наше действие, а alias
 * «Приложения BookShelf» появляется в списках запуска приложений (`PackageManager` и
 * `LauncherApps` — на втором построен выбор приложения для аппаратной кнопки) только когда он
 * включён настройкой.
 *
 * Состояние компонента — пользовательская настройка, поэтому тест запоминает его и возвращает
 * как было: иначе прогон тестов молча выключал бы пункт, уже включённый человеком.
 */
@RunWith(AndroidJUnit4::class)
class AppsAliasInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val alias = AppsAliasController(context)

    private var wasEnabled = false

    @Before
    fun rememberAliasState() {
        wasEnabled = alias.isEnabled()
    }

    @After
    fun restoreAliasState() {
        alias.setEnabled(wasEnabled)
    }

    @Test
    fun show_apps_action_resolves_to_our_activity() {
        val intent = Intent(AppsEntry.ACTION_SHOW_APPS).addCategory(Intent.CATEGORY_DEFAULT)

        val resolved = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)

        assertThat(resolved).isNotNull()
        assertThat(resolved!!.activityInfo.name).isEqualTo("io.github.bookshelf.apps.AppsActivity")
        assertThat(resolved.activityInfo.packageName).isEqualTo(context.packageName)
    }

    @Test
    fun alias_is_visible_in_package_manager_list_only_when_enabled() {
        alias.setEnabled(false)
        assertThat(launcherActivities()).doesNotContain(AppsAlias.fullName(context.packageName))

        alias.setEnabled(true)
        assertThat(launcherActivities()).contains(AppsAlias.fullName(context.packageName))
    }

    @Test
    fun alias_is_visible_to_launcher_apps_only_when_enabled() {
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        val user = Process.myUserHandle()

        alias.setEnabled(false)
        assertThat(launcherAppsActivityNames(launcherApps, user))
            .doesNotContain("io.github.bookshelf.apps.AppsAlias")

        alias.setEnabled(true)
        assertThat(launcherAppsActivityNames(launcherApps, user))
            .contains("io.github.bookshelf.apps.AppsAlias")
    }

    @Test
    fun enabled_alias_is_labelled_as_book_shelf_apps() {
        alias.setEnabled(true)

        val label = context.getSystemService(LauncherApps::class.java)
            .getActivityList(context.packageName, Process.myUserHandle())
            .firstOrNull { info -> info.name == "io.github.bookshelf.apps.AppsAlias" }
            ?.label

        assertThat(label).isEqualTo(context.getString(io.github.bookshelf.R.string.apps_alias_label))
    }

    private fun launcherActivities(): List<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return context.packageManager
            .queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .map { activity -> "${activity.activityInfo.packageName}/${activity.activityInfo.name}" }
    }

    private fun launcherAppsActivityNames(launcherApps: LauncherApps, user: android.os.UserHandle): List<String> =
        launcherApps.getActivityList(context.packageName, user).map { info -> info.name }
}
