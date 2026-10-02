package io.github.bookshelf.feature.library

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.bookshelf.feature.library.ui.LibraryScreen

/** Маршрут полки — главный экран приложения («E-ink UX» §6.2). */
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
 * @param onOpenCatalogs переход к каталогам OPDS.
 * @param onOpenScan переход к экрану сканирования (пустое состояние полки).
 * @param onOpenSettings переход к настройкам: читалка, папки, разрешения.
 * @param onExit выход или сворачивание приложения по аппаратному BACK из корня
 *   («E-ink UX» §3.2: «из корня — выход (или „свернуть“, настройка)»).
 */
fun NavGraphBuilder.libraryScreen(
    onOpenBook: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenFilters: () -> Unit,
    onOpenCollections: () -> Unit,
    onOpenCatalogs: () -> Unit,
    onOpenScan: () -> Unit,
    onOpenSettings: () -> Unit,
    onExit: () -> Unit,
) {
    composable(LibraryRoute.route) {
        LibraryScreen(
            onOpenBook = onOpenBook,
            onOpenSearch = onOpenSearch,
            onOpenFilters = onOpenFilters,
            onOpenCollections = onOpenCollections,
            onOpenCatalogs = onOpenCatalogs,
            onOpenScan = onOpenScan,
            onOpenSettings = onOpenSettings,
            onExit = onExit,
        )
    }
}
