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
    const val DEFAULT_SCREEN: String = AppsRoute.route

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
     * Экран, который открыть по Home: последний из памяти процесса, иначе выбранный в настройках.
     *
     * По умолчанию — список приложений ([DEFAULT_SCREEN]); настройка позволяет открывать полку.
     */
    fun startScreen(remembered: String?, preferred: HomeScreen = HomeScreen.APPS): String =
        remembered ?: routeOf(preferred)

    /** Маршрут экрана по настройке «Открывать по Home». */
    fun routeOf(screen: HomeScreen): String = when (screen) {
        HomeScreen.APPS -> AppsRoute.route
        HomeScreen.SHELF -> LibraryRoute.route
    }

    /** Состояние alias HOME после смены мастер-режима: без режима лаунчера роль освобождаем. */
    fun homeAliasEnabledAfterModeChange(launcherEnabled: Boolean): Boolean = launcherEnabled
}
