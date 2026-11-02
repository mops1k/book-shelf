package io.github.bookshelf.ui

import io.github.bookshelf.core.datastore.model.HomeScreen
import io.github.bookshelf.feature.launcher.AppsRoute
import io.github.bookshelf.feature.library.LibraryRoute

/**
 * Решения роли HOME (ADR-015, фаза 7).
 *
 * Что открывать по Home, куда девается BACK в корне и когда освобождать роль — чистые функции:
 * `RoleManager` и `NavController` в тестах не подменить, а решения проверить нужно. Системная
 * обвязка (включение alias, запрос роли) живёт в контроллере.
 */
object HomeRolePolicy {

    /**
     * Экран по умолчанию в роли HOME.
     *
     * Решение Александра 02.11.2026: в роли лаунчера первым ожидают список приложений, а полка
     * остаётся вкладкой. Вне роли HOME приложение стартует на полке, как раньше.
     */
    val DEFAULT_SCREEN: String = routeOf(HomeScreen.APPS)

    /** Мы ли домашний экран: система разрешает MAIN/HOME в наш пакет. */
    fun isOurHome(packageName: String?, ownPackage: String): Boolean =
        packageName != null && packageName == ownPackage

    /**
     * Сворачивать ли приложение по BACK в корне.
     *
     * Только когда мы домашний экран и пользователь не выбрал «выходить»: домашний экран,
     * который закрывается по BACK, оставляет человека на пустом рабочем столе.
     */
    fun shouldCollapse(homeRoleHeld: Boolean, collapseInsteadOfExit: Boolean): Boolean =
        homeRoleHeld && collapseInsteadOfExit

    /**
     * Экран, который открыть по Home (решение Александра 02.11.2026, уточнено в той же сессии).
     *
     * Всегда настройка «Открывать по Home»: по умолчанию список приложений, по выбору — полка.
     * «Последний экран из памяти процесса» отменён: человек ожидает стабильный экран, а не тот,
     * на котором его застали в прошлый раз.
     */
    fun startScreen(preferred: HomeScreen = HomeScreen.APPS): String = routeOf(preferred)

    /** Маршрут экрана по настройке «Открывать по Home». */
    fun routeOf(screen: HomeScreen): String = when (screen) {
        HomeScreen.APPS -> AppsRoute.route
        HomeScreen.SHELF -> LibraryRoute.route
    }

    /**
     * Экран для входящего запуска.
     *
     * Home — экран из настройки «Открывать по Home»; запуск приложения (иконка на рабочем столе,
     * аппаратная кнопка, назначенная на BookShelf) — полка; остальное навигацию не задаёт.
     */
    fun launchScreen(
        isHomeLaunch: Boolean,
        isLauncherLaunch: Boolean,
        preferred: HomeScreen = HomeScreen.APPS,
    ): String? = when {
        isHomeLaunch -> startScreen(preferred)
        isLauncherLaunch -> LibraryRoute.route
        else -> null
    }

    /** Состояние alias HOME после смены мастер-режима: без режима лаунчера роль освобождаем. */
    fun homeAliasEnabledAfterModeChange(launcherEnabled: Boolean): Boolean = launcherEnabled
}
