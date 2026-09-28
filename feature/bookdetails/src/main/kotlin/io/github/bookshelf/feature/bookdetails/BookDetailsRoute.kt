package io.github.bookshelf.feature.bookdetails

import androidx.navigation.NamedNavArgument
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import io.github.bookshelf.feature.bookdetails.ui.BookDetailsScreen

/** Маршрут карточки книги: `bookdetails/{bookId}` (docs/eink-ux.md §6.4). */
object BookDetailsRoute {
    /** Имя аргумента маршрута — оно же ключ в `SavedStateHandle`. */
    const val ARG_BOOK_ID: String = "bookId"

    /** Шаблон маршрута с обязательным `bookId`. */
    const val route: String = "bookdetails/{$ARG_BOOK_ID}"

    /** Аргументы для `composable(...)`. */
    val arguments: List<NamedNavArgument> = listOf(
        navArgument(ARG_BOOK_ID) { type = NavType.StringType },
    )

    /** Собрать конкретный маршрут для перехода: `bookdetails/<id>`. */
    fun createRoute(bookId: String): String = "bookdetails/$bookId"
}

/**
 * Регистрирует карточку книги в навигационном графе.
 *
 * @param onBack возврат на полку.
 * @param onOpenCollections добавление книги в коллекцию (Sprint 7).
 */
fun NavGraphBuilder.bookDetailsScreen(
    onBack: () -> Unit,
    onOpenCollections: (String) -> Unit,
) {
    composable(route = BookDetailsRoute.route, arguments = BookDetailsRoute.arguments) { entry ->
        val bookId = entry.arguments?.getString(BookDetailsRoute.ARG_BOOK_ID).orEmpty()
        // Тот же экземпляр, что и у экрана: hiltViewModel() привязан к NavBackStackEntry.
        val viewModel: BookDetailsViewModel = hiltViewModel()
        BookDetailsScreen(
            viewModel = viewModel,
            bookId = bookId,
            onBack = onBack,
            // Переход-заглушка от навигации: открытие — команда ViewModel, а не переход.
            onRead = { viewModel.onRead() },
            onOpenCollections = onOpenCollections,
            // Как и открытие: это команда ViewModel, а не переход между экранами.
            onEditMetadata = { viewModel.onEditMetadata() },
        )
    }
}
