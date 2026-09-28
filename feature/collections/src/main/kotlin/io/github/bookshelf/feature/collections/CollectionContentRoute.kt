package io.github.bookshelf.feature.collections

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import io.github.bookshelf.feature.collections.ui.CollectionContentScreen

/**
 * Маршрут содержимого полки.
 *
 * Идентификатор полки идёт аргументом маршрута, а не отдельным экраном-параметром: у полок
 * своя навигационная запись, и «Назад» из неё возвращает именно к списку полок.
 */
object CollectionContentRoute {

    /** Имя аргумента — обязано совпадать с [ARG_COLLECTION_ID]. */
    const val ARG_COLLECTION_ID: String = "collectionId"

    /** Шаблон маршрута: `collection/{collectionId}`. */
    const val route: String = "collection/{$ARG_COLLECTION_ID}"

    /** Готовый маршрут для конкретной полки. */
    fun createRoute(collectionId: String): String = "collection/$collectionId"
}

/**
 * Регистрирует экран содержимого полки.
 *
 * @param onBack возврат к списку полок.
 * @param onOpenBook открытие карточки книги из полки.
 */
fun NavGraphBuilder.collectionContentScreen(
    onBack: () -> Unit,
    onOpenBook: (String) -> Unit,
) {
    composable(
        route = CollectionContentRoute.route,
        arguments = listOf(
            navArgument(CollectionContentRoute.ARG_COLLECTION_ID) { type = NavType.StringType },
        ),
    ) { entry ->
        val collectionId = entry.arguments
            ?.getString(CollectionContentRoute.ARG_COLLECTION_ID)
            .orEmpty()
        CollectionContentScreen(
            collectionId = collectionId,
            onBack = onBack,
            onOpenBook = onOpenBook,
        )
    }
}
