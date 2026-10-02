package io.github.bookshelf.feature.filters

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.bookshelf.feature.filters.ui.FiltersScreen

/** Маршрут фасетных фильтров («Архитектура» §3.5, «E-ink UX» §6.2). */
object FiltersRoute {
    /** Путь маршрута в навигационном графе. */
    const val route: String = "filters"
}

/**
 * Регистрирует экран фильтров в навигационном графе.
 *
 * @param onBack возврат на полку без применения.
 * @param onApply вернуться на полку с выбранными фильтрами.
 */
fun NavGraphBuilder.filtersScreen(
    onBack: () -> Unit,
    onApply: () -> Unit,
) {
    composable(FiltersRoute.route) {
        FiltersScreen(onBack = onBack, onApply = onApply)
    }
}
