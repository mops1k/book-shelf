package io.github.bookshelf.core.applist.rule

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

/**
 * Домашний экран BookShelf (ADR-015, фаза 7).
 *
 * Роль HOME включается `activity-alias` с категорией HOME, выключенным в манифесте: пока он
 * выключен, приложение не участвует в выборе домашнего экрана. Имя класса — от namespace
 * (`io.github.bookshelf.apps.HomeAlias`), пакет — от сборки, как и у alias «Приложения».
 */
object HomeAlias {

    /** Полное имя класса alias HOME — от namespace, одинаковое во всех flavor. */
    const val CLASS_NAME: String = "io.github.bookshelf.apps.HomeAlias"

    /** Компонент для управления состоянием. */
    fun componentName(packageName: String): ComponentName = ComponentName(packageName, CLASS_NAME)

    /** Строковое имя компонента в виде, в котором его печатают `pm` и `dumpsys`. */
    fun fullName(packageName: String): String = "$packageName/$CLASS_NAME"
}

/**
 * Управление ролью домашнего экрана.
 *
 * На Android 10+ роль запрашивается через `RoleManager` — это единственный путь, который сам
 * предлагает человеку выбор домашнего приложения; ниже десятой версии открываются системные
 * настройки домашнего экрана. Список лаунчеров в системных настройках спрятан глубоко (жалоба
 * Александра 02.11.2026), поэтому кнопка в наших настройках решает эту задачу.
 */
class HomeRoleController(private val context: Context) {

    private val component: ComponentName
        get() = HomeAlias.componentName(context.packageName)

    /** Включён ли alias HOME: пока выключен, нас нет в списке домашних экранов. */
    fun isAliasEnabled(): Boolean =
        context.packageManager.getComponentEnabledSetting(component) ==
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    /** Включить или выключить alias HOME; активность не перезапускается. */
    fun setAliasEnabled(enabled: Boolean) {
        val state = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        context.packageManager.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
    }

    /** Мы ли домашний экран прямо сейчас. */
    fun isHomeRoleHeld(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.packageName == context.packageName
    }

    /**
     * Интент, открывающий выбор домашнего экрана.
     *
     * На Android 10+ это системный диалог роли (он и предлагает заменить текущий лаунчер);
     * ниже — настройки домашнего экрана.
     */
    fun roleRequestIntent(): Intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = context.getSystemService(RoleManager::class.java)
        roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
    } else {
        Intent(Settings.ACTION_HOME_SETTINGS)
    }
}
