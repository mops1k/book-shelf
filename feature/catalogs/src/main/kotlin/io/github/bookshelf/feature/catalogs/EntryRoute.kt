package io.github.bookshelf.feature.catalogs

import android.net.Uri
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import io.github.bookshelf.feature.catalogs.ui.EntryDetailsScreen

/**
 * Маршрут карточки записи каталога.
 *
 * Отдельный экран нужен, потому что сама запись OPDS — это не файл, а описание: нажатие на
 * книгу в листинге не должно сразу качать её. Раньше нажатие сразу ставило загрузку в
 * очередь, и описание, форматы и обложку посмотреть было негде.
 */
object EntryRoute {

    /** Аргумент с id каталога, к которому относится запись. */
    const val ARG_CATALOG_ID: String = "catalogId"

    /** Аргумент с id записи внутри загруженного фида. */
    const val ARG_ENTRY_ID: String = "entryId"

    /**
     * Адрес фида, из которого открыта запись.
     *
     * Без него карточка перечитывала корневой каталог, а запись лежала во вложенном фиде
     * раздела — и открывался пустой экран «Запись не найдена».
     */
    const val ARG_FEED_URL: String = "feedUrl"

    /** Шаблон маршрута: `opds/entry/{catalogId}/{entryId}/{feedUrl}`. */
    const val route: String = "opds/entry/{$ARG_CATALOG_ID}/{$ARG_ENTRY_ID}/{$ARG_FEED_URL}"

    /** Готовый маршрут для конкретной записи. */
    fun createRoute(catalogId: String, entryId: String, feedUrl: String?): String =
        "opds/entry/$catalogId/$entryId/${Uri.encode(feedUrl.orEmpty())}"
}

/**
 * Регистрирует карточку записи каталога.
 *
 * @param onBack возврат к фиду.
 * @param onOpenFeed возврат в браузер каталога, когда карточка попросила открыть фид автора
 *   или серии: навигация — дело вызывающего кода, а не экрана карточки.
 */
fun NavGraphBuilder.opdsEntryScreen(
    onBack: () -> Unit,
    onOpenFeed: () -> Unit,
) {
    composable(
        route = EntryRoute.route,
        arguments = listOf(
            navArgument(EntryRoute.ARG_CATALOG_ID) { type = NavType.StringType },
            navArgument(EntryRoute.ARG_ENTRY_ID) { type = NavType.StringType },
            navArgument(EntryRoute.ARG_FEED_URL) { type = NavType.StringType; defaultValue = "" },
        ),
    ) {
        // Аргументы не передаём в экран: ViewModel читает их из `SavedStateHandle` сам. Раньше
        // карточка перечитывала корневой фид, а запись лежала во вложенном — отсюда
        // «Запись не найдена» на каждой книге.
        EntryDetailsScreen(
            onBack = onBack,
            onOpenFeed = onOpenFeed,
        )
    }
}
