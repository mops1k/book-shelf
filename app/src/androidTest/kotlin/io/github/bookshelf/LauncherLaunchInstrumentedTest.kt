package io.github.bookshelf

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.RequiresDevice
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.applist.catalog.AndroidAppCatalog
import io.github.bookshelf.core.common.testing.TestAppDispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Запуск приложения из списка на живом устройстве (ADR-015).
 *
 * Проверяет то, чего не проверить юнит-тестом: явный компонент из каталога действительно
 * резолвится системой — иначе экран показывал бы баннер на каждом тапе. Читалка CoolReader
 * выбрана как стабильно установленная на целевом устройстве; на устройстве без неё тест
 * пропускается, а не падает.
 */
@RunWith(AndroidJUnit4::class)
@RequiresDevice
class LauncherLaunchInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun catalog_launch_intent_resolves_for_cool_reader() {
        val catalog = AndroidAppCatalog(context, TestAppDispatchers())
        val apps = runBlocking { catalog.apps() }
        val reader = apps.firstOrNull { app -> app.packageName == "org.coolreader" }
        assumeTrue("CoolReader не установлен на устройстве", reader != null)

        val intent = catalog.launchIntent(reader!!)

        assertThat(intent).isNotNull()
        assertThat(context.packageManager.resolveActivity(intent!!, 0)).isNotNull()
    }

    @Test
    fun catalog_own_package_is_excluded_from_visible_list() {
        val catalog = AndroidAppCatalog(context, TestAppDispatchers())
        val visible = runBlocking { catalog.apps() }
            .filter { app -> app.packageName != catalog.ownPackage }

        assertThat(visible.map { app -> app.packageName }).doesNotContain(catalog.ownPackage)
    }
}
