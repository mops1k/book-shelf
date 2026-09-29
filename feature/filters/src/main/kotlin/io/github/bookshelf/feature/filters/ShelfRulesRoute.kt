package io.github.bookshelf.feature.filters

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import io.github.bookshelf.feature.filters.ui.ShelfRulesScreen

/**
 * Маршрут редактора правил умной полки.
 *
 * Отдельный маршрут, а не панель внутри экрана полок: у редактора свой предмет (фасеты полки),
 * своя навигационная запись и возврат к списку полок по «Назад».
 */
object ShelfRulesRoute {

    /** Имя аргумента — обязано совпадать с [ARG_COLLECTION_ID]. */
    const val ARG_COLLECTION_ID: String = "collectionId"

    /** Шаблон маршрута: `shelf-rules/{collectionId}`. */
    const val route: String = "shelf-rules/{$ARG_COLLECTION_ID}"

    /** Готовый маршрут для конкретной полки. */
    fun createRoute(collectionId: String): String = "shelf-rules/$collectionId"
}

/**
 * Регистрирует редактор правил умной полки.
 *
 * @param onBack возврат без сохранения.
 * @param onSaved возврат после сохранённых правил.
 */
fun NavGraphBuilder.shelfRulesScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    composable(
        route = ShelfRulesRoute.route,
        arguments = listOf(
            navArgument(ShelfRulesRoute.ARG_COLLECTION_ID) { type = NavType.StringType },
        ),
    ) {
        ShelfRulesScreen(onBack = onBack, onSaved = onSaved)
    }
}
