package io.github.bookshelf.feature.scan

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.bookshelf.feature.scan.ui.ScanScreen

/** Маршрут экрана сканирования: прогресс, отчёт, дубликаты (docs/eink-ux.md §6.6–6.7). */
object ScanRoute {
    /** Путь маршрута в навигационном графе. */
    const val route: String = "scan"
}

/**
 * Регистрирует экран сканирования в навигационном графе.
 *
 * @param onBack возврат на предыдущий экран.
 * @param onOpenBook открытие книги из отчёта или группы дубликатов.
 */
fun NavGraphBuilder.scanScreen(
    onBack: () -> Unit,
    onOpenBook: (String) -> Unit,
) {
    composable(ScanRoute.route) {
        ScanScreen(onBack = onBack, onOpenBook = onOpenBook)
    }
}
