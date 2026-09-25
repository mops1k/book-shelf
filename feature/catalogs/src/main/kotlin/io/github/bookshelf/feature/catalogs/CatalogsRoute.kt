package io.github.bookshelf.feature.catalogs

import androidx.navigation.NamedNavArgument
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import io.github.bookshelf.feature.catalogs.ui.CatalogsScreen

/** Маршрут OPDS-каталогов (docs/opds.md §3). */
object CatalogsRoute {
    /** Необязательный аргумент: каталог, который нужно открыть сразу. */
    const val ARG_CATALOG_ID: String = "catalogId"

    /** Шаблон маршрута: список каталогов или сразу браузер фида. */
    const val route: String = "catalogs?$ARG_CATALOG_ID={$ARG_CATALOG_ID}"

    /** Аргументы для `composable(...)`: `catalogId` необязателен. */
    val arguments: List<NamedNavArgument> = listOf(
        navArgument(ARG_CATALOG_ID) {
            type = NavType.StringType
            nullable = true
            defaultValue = null
        },
    )

    /** Собрать маршрут: без аргумента — список каталогов, с аргументом — браузер фида. */
    fun createRoute(catalogId: String? = null): String =
        if (catalogId == null) "catalogs" else "catalogs?$ARG_CATALOG_ID=$catalogId"
}

/**
 * Регистрирует экран OPDS-каталогов в навигационном графе.
 *
 * @param onBack возврат на предыдущий экран.
 * @param onOpenBook открытие карточки уже скачанной книги.
 */
fun NavGraphBuilder.catalogsScreen(
    onBack: () -> Unit,
    onOpenBook: (String) -> Unit,
) {
    composable(route = CatalogsRoute.route, arguments = CatalogsRoute.arguments) { entry ->
        val catalogId = entry.arguments?.getString(CatalogsRoute.ARG_CATALOG_ID)
        CatalogsScreen(
            catalogId = catalogId,
            onBack = onBack,
            onOpenBook = onOpenBook,
        )
    }
}
