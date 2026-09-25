package io.github.bookshelf.feature.library

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.bookshelf.feature.library.ui.LibraryScreen

/** Маршрут полки — главный экран приложения (docs/eink-ux.md §6.2). */
object LibraryRoute {
    /** Путь маршрута в навигационном графе. */
    const val route: String = "library"
}

/**
 * Регистрирует экран полки в навигационном графе.
 *
 * @param onOpenBook открытие карточки книги по `bookId`.
 * @param onOpenSearch переход к поиску по библиотеке.
 * @param onOpenFilters переход к экрану фасетных фильтров.
 * @param onOpenCollections переход к коллекциям и умным полкам.
 * @param onOpenScan переход к экрану сканирования (пустое состояние полки).
 * @param onExit выход или сворачивание приложения по аппаратному BACK из корня
 *   (docs/eink-ux.md §3.2: «из корня — выход (или „свернуть“, настройка)»).
 */
fun NavGraphBuilder.libraryScreen(
    onOpenBook: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenFilters: () -> Unit,
    onOpenCollections: () -> Unit,
    onOpenScan: () -> Unit,
    onExit: () -> Unit,
) {
    composable(LibraryRoute.route) {
        LibraryScreen(
            onOpenBook = onOpenBook,
            onOpenSearch = onOpenSearch,
            onOpenFilters = onOpenFilters,
            onOpenCollections = onOpenCollections,
            onOpenScan = onOpenScan,
            onExit = onExit,
        )
    }
}
