package io.github.bookshelf.feature.search

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.bookshelf.feature.search.ui.SearchScreen

/** Маршрут поиска по библиотеке (docs/eink-ux.md §6.5). */
object SearchRoute {
    /** Путь маршрута в навигационном графе. */
    const val route: String = "search"
}

/**
 * Регистрирует экран поиска в навигационном графе.
 *
 * @param onBack возврат на полку.
 * @param onOpenBook открытие книги из результатов поиска.
 */
fun NavGraphBuilder.searchScreen(
    onBack: () -> Unit,
    onOpenBook: (String) -> Unit,
) {
    composable(SearchRoute.route) {
        SearchScreen(onBack = onBack, onOpenBook = onOpenBook)
    }
}
