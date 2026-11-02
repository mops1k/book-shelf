package io.github.bookshelf.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.applist.rule.AppsAlias
import io.github.bookshelf.core.applist.rule.AppsAliasController
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Точка входа «Приложения» на устройстве (ADR-015, фаза 6).
 *
 * Проверяем то, что нельзя проверить на JVM: что система действительно разрешает наше действие
 * и что alias «Приложения» появляется в списке приложений только после включения настройкой.
 */
@RunWith(AndroidJUnit4::class)
class AppsAliasInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun show_apps_action_resolves_to_our_activity() {
        val intent = Intent(AppsEntry.ACTION_SHOW_APPS).addCategory(Intent.CATEGORY_DEFAULT)

        val resolved = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)

        assertThat(resolved).isNotNull()
        assertThat(resolved!!.activityInfo.name).isEqualTo("io.github.bookshelf.apps.AppsActivity")
        assertThat(resolved.activityInfo.packageName).isEqualTo(context.packageName)
    }

    @Test
    fun alias_is_visible_in_launcher_list_only_when_enabled() {
        val alias = AppsAliasController(context)
        val component = AppsAlias.componentName(context.packageName)
        try {
            alias.setEnabled(false)
            assertThat(launcherActivities()).doesNotContain(component.flattenToString())

            alias.setEnabled(true)
            assertThat(alias.isEnabled()).isTrue()
            assertThat(launcherActivities()).contains(component.flattenToString())
        } finally {
            alias.setEnabled(false)
        }
    }

    private fun launcherActivities(): List<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return context.packageManager
            .queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .map { activity -> "${activity.activityInfo.packageName}/${activity.activityInfo.name}" }
    }
}
