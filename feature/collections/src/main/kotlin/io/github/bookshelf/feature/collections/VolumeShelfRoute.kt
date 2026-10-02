package io.github.bookshelf.feature.collections

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import io.github.bookshelf.feature.collections.ui.VolumeShelfScreen

/**
 * Маршрут полки съёмного носителя.
 *
 * Отдельный экран, а не раздел списка полок: из него открывается карточка книги, и «Назад» должен
 * вернуть на перечень полок (правило Александра 02.10.2026 — любая полка открывается отдельным
 * экраном, фильтр основной библиотеки для этого не годится).
 */
object VolumeShelfRoute {

    /** Аргумент маршрута: `sources.id` тома. */
    const val ARG_SOURCE_ID: String = "sourceId"

    /** Путь маршрута для конкретного тома. */
    fun createRoute(sourceId: String): String = "volume/$sourceId"

    /** Шаблон маршрута в навигационном графе. */
    const val pattern: String = "volume/{$ARG_SOURCE_ID}"
}

/**
 * Регистрирует полку съёмного носителя в навигационном графе.
 *
 * @param onBack возврат на перечень полок.
 * @param onOpenBook открытие карточки книги.
 */
fun NavGraphBuilder.volumeShelfScreen(
    onBack: () -> Unit,
    onOpenBook: (String) -> Unit,
) {
    composable(
        route = VolumeShelfRoute.pattern,
        arguments = listOf(navArgument(VolumeShelfRoute.ARG_SOURCE_ID) { type = NavType.StringType }),
    ) {
        VolumeShelfScreen(onBack = onBack, onOpenBook = onOpenBook)
    }
}
