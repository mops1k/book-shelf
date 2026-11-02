package io.github.bookshelf.ui

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.datastore.model.HomeScreen
import io.github.bookshelf.feature.launcher.AppsRoute
import io.github.bookshelf.feature.library.LibraryRoute
import org.junit.Test

/**
 * Решения роли HOME (ADR-015, фаза 7).
 *
 * Кто домашний экран, что открывать по Home, куда девается BACK и когда роль надо освобождать —
 * чистые функции: `RoleManager` и `NavController` в тестах не подменить, а решения проверить
 * нужно. Android-обвязка (alias, запрос роли) живёт в контроллере.
 */
class HomeRolePolicyTest {

    private val own = "io.github.bookshelf.debug"

    @Test
    fun our_package_as_home_is_our_role() {
        assertThat(HomeRolePolicy.isOurHome(packageName = own, ownPackage = own)).isTrue()
    }

    @Test
    fun another_launcher_as_home_is_not_our_role() {
        assertThat(HomeRolePolicy.isOurHome(packageName = "com.android.launcher3", ownPackage = own)).isFalse()
    }

    @Test
    fun no_home_activity_is_not_our_role() {
        assertThat(HomeRolePolicy.isOurHome(packageName = null, ownPackage = own)).isFalse()
    }

    @Test
    fun home_without_memory_opens_the_apps_list() {
        // Решение Александра 02.11.2026: в роли лаунчера первым ожидают список приложений.
        assertThat(HomeRolePolicy.startScreen(remembered = null)).isEqualTo(AppsRoute.route)
    }

    @Test
    fun home_opens_the_last_screen_remembered_in_the_process() {
        assertThat(HomeRolePolicy.startScreen(remembered = "library")).isEqualTo("library")
    }

    @Test
    fun home_opens_the_shelf_when_the_user_prefers_it() {
        assertThat(HomeRolePolicy.startScreen(remembered = null, preferred = HomeScreen.SHELF))
            .isEqualTo(LibraryRoute.route)
    }

    @Test
    fun back_at_root_collapses_by_default_in_home_role() {
        assertThat(HomeRolePolicy.shouldCollapse(homeRoleHeld = true, collapseInsteadOfExit = true)).isTrue()
    }

    @Test
    fun back_at_root_exits_when_user_asked_to_exit() {
        assertThat(HomeRolePolicy.shouldCollapse(homeRoleHeld = true, collapseInsteadOfExit = false)).isFalse()
    }

    @Test
    fun back_at_root_exits_when_we_are_not_home() {
        assertThat(HomeRolePolicy.shouldCollapse(homeRoleHeld = false, collapseInsteadOfExit = true)).isFalse()
    }

    @Test
    fun disabling_launcher_mode_releases_the_home_role_alias() {
        // Выключили режим, а мы домашний экран — alias гасим, система вернётся к выбору лаунчера.
        assertThat(HomeRolePolicy.homeAliasEnabledAfterModeChange(launcherEnabled = false)).isFalse()
    }

    @Test
    fun enabling_launcher_mode_keeps_the_alias_as_is() {
        assertThat(HomeRolePolicy.homeAliasEnabledAfterModeChange(launcherEnabled = true)).isTrue()
    }
}
