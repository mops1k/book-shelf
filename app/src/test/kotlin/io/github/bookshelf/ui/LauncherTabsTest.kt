package io.github.bookshelf.ui

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.feature.launcher.AppsRoute
import io.github.bookshelf.feature.library.LibraryRoute
import org.junit.Test

/**
 * Правило возврата и вкладки нижней панели (ADR-015).
 *
 * Чистые функции без Compose и без `NavController`: решение «куда ведёт BACK» принимается по
 * маршруту, роли HOME и настройке сворачивания, и именно оно ошибается чаще всего — на устройстве
 * это выглядит как «лаунчер закрывается сам» или «BACK не выходит из списка приложений».
 */
class LauncherTabsTest {

    @Test
    fun tab_bar_is_visible_while_launcher_mode_is_on() {
        assertThat(LauncherTabs.tabBarVisible(launcherEnabled = true, hideTabBar = false)).isTrue()
    }

    @Test
    fun tab_bar_can_be_hidden_by_setting() {
        // Устройства с вендорскими кнопками: полка и приложения открываются кнопками.
        assertThat(LauncherTabs.tabBarVisible(launcherEnabled = true, hideTabBar = true)).isFalse()
    }

    @Test
    fun tab_bar_disappears_when_launcher_mode_is_off() {
        // Без режима лаунчера экрана «Приложения» нет — панель вела бы в отключённый экран.
        assertThat(LauncherTabs.tabBarVisible(launcherEnabled = false, hideTabBar = false)).isFalse()
    }

    @Test
    fun apps_route_is_the_second_tab() {
        assertThat(LauncherTabs.tabOf(AppsRoute.route)).isEqualTo(LauncherTabs.Tab.APPS)
    }

    @Test
    fun library_route_is_the_first_tab() {
        assertThat(LauncherTabs.tabOf(LibraryRoute.route)).isEqualTo(LauncherTabs.Tab.SHELF)
    }

    @Test
    fun other_routes_have_no_tab() {
        assertThat(LauncherTabs.tabOf("settings")).isNull()
    }

    @Test
    fun back_from_apps_returns_to_the_shelf() {
        val action = LauncherTabs.backAction(
            route = AppsRoute.route,
            homeRole = false,
            collapseInsteadOfExit = true,
        )

        assertThat(action).isEqualTo(LauncherTabs.BackAction.SHOW_SHELF)
    }

    @Test
    fun back_from_shelf_in_home_role_collapses_when_setting_says_so() {
        val action = LauncherTabs.backAction(
            route = LibraryRoute.route,
            homeRole = true,
            collapseInsteadOfExit = true,
        )

        assertThat(action).isEqualTo(LauncherTabs.BackAction.COLLAPSE)
    }

    @Test
    fun back_from_shelf_in_home_role_exits_when_user_chose_exit() {
        val action = LauncherTabs.backAction(
            route = LibraryRoute.route,
            homeRole = true,
            collapseInsteadOfExit = false,
        )

        assertThat(action).isEqualTo(LauncherTabs.BackAction.EXIT)
    }

    @Test
    fun back_from_shelf_without_home_role_exits() {
        val action = LauncherTabs.backAction(
            route = LibraryRoute.route,
            homeRole = false,
            collapseInsteadOfExit = true,
        )

        assertThat(action).isEqualTo(LauncherTabs.BackAction.EXIT)
    }

    @Test
    fun back_on_other_routes_pops_normally() {
        val action = LauncherTabs.backAction(
            route = "settings",
            homeRole = true,
            collapseInsteadOfExit = true,
        )

        assertThat(action).isEqualTo(LauncherTabs.BackAction.POP)
    }
}
