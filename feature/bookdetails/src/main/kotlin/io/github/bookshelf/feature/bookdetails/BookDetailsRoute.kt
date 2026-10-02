package io.github.bookshelf.feature.bookdetails

import androidx.navigation.NamedNavArgument
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import io.github.bookshelf.feature.bookdetails.ui.BookDetailsScreen

/** Маршрут карточки книги: `bookdetails/{bookId}` («E-ink UX» §6.4). */
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
 * @param onOpenLibrary возврат на полку — им заканчиваются переходы по автору и серии: карточка
 *   кладёт просьбу в [io.github.bookshelf.core.data.repository.LibraryBrowseRequest], полка её
 *   забирает и показывает нужные книги.
 */
fun NavGraphBuilder.bookDetailsScreen(
    onBack: () -> Unit,
    onOpenCollections: (String) -> Unit,
    onOpenLibrary: () -> Unit,
    onEditMetadata: (String) -> Unit,
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
            onOpenLibrary = onOpenLibrary,
            // Правка метаданных — переход на отдельный экран: полей пять, в карточке им тесно.
            onEditMetadata = { onEditMetadata(bookId) },
        )
    }
}
