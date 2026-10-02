package io.github.bookshelf.feature.settings

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.bookshelf.feature.settings.ui.SettingsScreen

/** Маршрут настроек («E-ink UX» §6.8). */
object SettingsRoute {
    /** Путь маршрута в навигационном графе. */
    const val route: String = "settings"
}

/**
 * Регистрирует экран настроек в навигационном графе.
 *
 * @param onBack возврат на предыдущий экран.
 * @param onOpenCategory открытие экрана конкретной категории настроек.
 */
fun NavGraphBuilder.settingsScreen(
    onBack: () -> Unit,
    onOpenCategory: (SettingsSectionKind) -> Unit,
) {
    composable(SettingsRoute.route) {
        SettingsScreen(onBack = onBack, onOpenCategory = onOpenCategory)
    }
}
