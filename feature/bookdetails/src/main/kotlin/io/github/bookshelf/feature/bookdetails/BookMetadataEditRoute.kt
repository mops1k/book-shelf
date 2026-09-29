package io.github.bookshelf.feature.bookdetails

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import io.github.bookshelf.feature.bookdetails.ui.BookMetadataEditScreen

/** Маршрут правки метаданных: `bookmetadata/{bookId}`. */
object BookMetadataEditRoute {

    /** Имя аргумента маршрута — оно же ключ в `SavedStateHandle`. */
    const val ARG_BOOK_ID: String = "bookId"

    /** Шаблон маршрута с обязательным `bookId`. */
    const val route: String = "bookmetadata/{$ARG_BOOK_ID}"

    /** Аргументы для `composable(...)`. */
    val arguments: List<NamedNavArgument> = listOf(
        navArgument(ARG_BOOK_ID) { type = NavType.StringType },
    )

    /** Собрать конкретный маршрут для перехода: `bookmetadata/<id>`. */
    fun createRoute(bookId: String): String = "bookmetadata/$bookId"
}

/**
 * Регистрирует экран правки метаданных в навигационном графе.
 *
 * @param onBack возврат в карточку книги. Сохранение не закрывает экран само: человек должен
 *   увидеть, что именно записано и что не удалось, — поэтому итог показывается здесь же.
 */
fun NavGraphBuilder.bookMetadataEditScreen(onBack: () -> Unit) {
    composable(route = BookMetadataEditRoute.route, arguments = BookMetadataEditRoute.arguments) {
        val viewModel: BookMetadataEditViewModel = hiltViewModel()
        BookMetadataEditScreen(viewModel = viewModel, onBack = onBack)
    }
}
