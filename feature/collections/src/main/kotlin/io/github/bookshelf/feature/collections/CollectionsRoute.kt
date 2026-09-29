package io.github.bookshelf.feature.collections

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.bookshelf.feature.collections.ui.CollectionsScreen

/** Маршрут коллекций и умных полок (docs/eink-ux.md §6.3). */
object CollectionsRoute {
    /** Путь маршрута в навигационном графе. */
    const val route: String = "collections"
}

/**
 * Регистрирует экран коллекций в навигационном графе.
 *
 * @param onBack возврат на полку.
 * @param onOpenCollection открытие содержимого выбранной полки.
 * @param onEditRules открытие редактора правил умной полки.
 */
fun NavGraphBuilder.collectionsScreen(
    onBack: () -> Unit,
    onOpenCollection: (String) -> Unit,
    onEditRules: (String) -> Unit,
) {
    composable(CollectionsRoute.route) {
        CollectionsScreen(
            onBack = onBack,
            onOpenCollection = onOpenCollection,
            onEditRules = onEditRules,
        )
    }
}
