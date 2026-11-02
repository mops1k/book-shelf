package io.github.bookshelf.core.applist.rule

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Точка входа «Приложения» в списке приложений устройства (ADR-015, фаза 6).
 *
 * Отдельная активити нужна ради аппаратной кнопки, но в вендорском списке приложений её надо
 * включать осознанно: по умолчанию alias выключен и включает его настройка «Лаунчер».
 *
 * Пакет компонента — packageName сборки (`io.github.bookshelf.debug` у отладки), а имя класса —
 * от namespace (`io.github.bookshelf`): в манифесте стоит `.apps.AppsAlias`, и Android разворачивает
 * точку по namespace, а не по applicationId. Проверено на устройстве: компонент у debug-сборки
 * называется `io.github.bookshelf.debug/io.github.bookshelf.apps.AppsAlias`.
 */
object AppsAlias {

    /** Полное имя класса alias — от namespace, одинаковое во всех flavor. */
    const val CLASS_NAME: String = "io.github.bookshelf.apps.AppsAlias"

    /** Компонент для управления состоянием: пакет сборки + класс от namespace. */
    fun componentName(packageName: String): ComponentName = ComponentName(packageName, CLASS_NAME)

    /** Строковое имя компонента в виде, в котором его печатают `pm` и `dumpsys`. */
    fun fullName(packageName: String): String = "$packageName/$CLASS_NAME"
}

/**
 * Включение и выключение alias «Приложения».
 *
 * Состояние компонента — глобальное для пользователя, поэтому включение дублируется в хранилище
 * лаунчера: настройка должна быть видна и после перезапуска, и после переустановки приложения
 * (компонент при этом снова выключен — как в манифесте).
 */
class AppsAliasController(private val context: Context) {

    private val component: ComponentName
        get() = AppsAlias.componentName(context.packageName)

    /** Включён ли alias прямо сейчас. */
    fun isEnabled(): Boolean =
        context.packageManager.getComponentEnabledSetting(component) ==
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    /** Включить или выключить alias; активность не перезапускается. */
    fun setEnabled(enabled: Boolean) {
        val state = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        context.packageManager.setComponentEnabledSetting(
            component,
            state,
            PackageManager.DONT_KILL_APP,
        )
    }
}
