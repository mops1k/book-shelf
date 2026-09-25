package io.github.bookshelf.feature.settings

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.bookshelf.feature.settings.ui.SettingsScreen

/** Маршрут настроек (docs/eink-ux.md §6.8). */
object SettingsRoute {
    /** Путь маршрута в навигационном графе. */
    const val route: String = "settings"
}

/**
 * Регистрирует экран настроек в навигационном графе.
 *
 * @param onBack возврат на предыдущий экран.
 * @param onOpenSection открытие конкретного раздела настроек (Sprint 5).
 */
fun NavGraphBuilder.settingsScreen(
    onBack: () -> Unit,
    onOpenSection: (SettingsSectionKind) -> Unit,
) {
    composable(SettingsRoute.route) {
        SettingsScreen(onBack = onBack, onOpenSection = onOpenSection)
    }
}
