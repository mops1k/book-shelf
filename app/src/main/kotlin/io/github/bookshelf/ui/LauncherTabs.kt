package io.github.bookshelf.ui

import io.github.bookshelf.feature.library.LibraryRoute
import io.github.bookshelf.feature.launcher.AppsRoute

/**
 * Вкладки нижней панели и правило BACK (ADR-015).
 *
 * Решение вынесено из экранов чистыми функциями: «куда ведёт BACK» зависит от маршрута, роли HOME
 * и настройки «сворачивать вместо выхода», и проверяется тестами без Compose и `NavController`.
 */
object LauncherTabs {

    /** Две вкладки нижней панели: полка и список приложений. */
    enum class Tab { SHELF, APPS }

    /** Что делать по BACK: показать полку, свернуть приложение, выйти или обычный возврат. */
    enum class BackAction {
        /** Вернуться на полку (BACK с экрана приложений). */
        SHOW_SHELF,

        /** Свернуть приложение: роль HOME не должна выкидывать на пустой экран. */
        COLLAPSE,

        /** Выйти из приложения (не-лаунчерский режим или так выбрал пользователь). */
        EXIT,

        /** Обычный `popBackStack`: глубина приложения. */
        POP,
    }

    /** Какая вкладка соответствует маршруту; `null` — маршрут вне панели. */
    fun tabOf(route: String?): Tab? = when (route) {
        LibraryRoute.route -> Tab.SHELF
        AppsRoute.route -> Tab.APPS
        else -> null
    }

    /**
     * Решение по BACK для текущего маршрута.
     *
     * С экрана приложений — всегда на полку: список приложений не корень. В корне в роли HOME
     * решает настройка: по умолчанию приложение сворачивается (решение 02.11.2026), «выходить»
     * возвращает прежнее поведение. На остальных маршрутах — обычный возврат.
     */
    fun backAction(route: String?, homeRole: Boolean, collapseInsteadOfExit: Boolean): BackAction = when {
        route == AppsRoute.route -> BackAction.SHOW_SHELF
        route == LibraryRoute.route && homeRole && collapseInsteadOfExit -> BackAction.COLLAPSE
        route == LibraryRoute.route -> BackAction.EXIT
        else -> BackAction.POP
    }
}
