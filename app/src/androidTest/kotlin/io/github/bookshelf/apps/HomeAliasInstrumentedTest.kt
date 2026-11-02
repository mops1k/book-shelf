package io.github.bookshelf.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.applist.rule.HomeAlias
import io.github.bookshelf.core.applist.rule.HomeRoleController
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Роль домашнего экрана на устройстве (ADR-015, фаза 7).
 *
 * Проверяем то, что нельзя проверить на JVM: система видит наш домашний экран только когда alias
 * включён, и после включения MAIN/HOME разрешается в наш пакет. Состояние alias — пользовательская
 * настройка, поэтому тест возвращает его как было, а роль не запрашивает (системный диалог
 * в тестах недопустим).
 */
@RunWith(AndroidJUnit4::class)
class HomeAliasInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val controller = HomeRoleController(context)

    private var wasEnabled = false

    @Before
    fun rememberAliasState() {
        wasEnabled = controller.isAliasEnabled()
    }

    @After
    fun restoreAliasState() {
        controller.setAliasEnabled(wasEnabled)
    }

    @Test
    fun our_package_is_among_home_candidates_only_when_alias_is_enabled() {
        // resolveActivity здесь бесполезен: когда домашний экран не выбран, система возвращает
        // свой диалог (пакет «android»). Смотрим список кандидатов — в нём нас либо есть, либо нет.
        controller.setAliasEnabled(false)
        assertThat(homeCandidates()).doesNotContain(context.packageName)

        controller.setAliasEnabled(true)
        assertThat(homeCandidates()).contains(context.packageName)
    }

    @Test
    fun home_alias_component_belongs_to_the_namespace_but_not_to_the_application_id() {
        controller.setAliasEnabled(true)

        assertThat(homeComponents()).contains("${context.packageName}/${HomeAlias.CLASS_NAME}")
    }

    private fun homeCandidates(): List<String> = context.packageManager
        .queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_ALL,
        )
        .map { it.activityInfo.packageName }

    private fun homeComponents(): List<String> = context.packageManager
        .queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_ALL,
        )
        .map { "${it.activityInfo.packageName}/${it.activityInfo.name}" }
        .distinct()
}
