package io.github.bookshelf.feature.launcher

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.bookshelf.feature.launcher.ui.HiddenAppsScreen

/**
 * Экран «Скрытые приложения» (ADR-015, решение 7).
 *
 * Открывается строкой в настройках → «Лаунчер» и только когда скрытые есть. Без него «Скрыть
 * из списка» было бы односторонним действием: вернуть приложение было нечем (нашёл Александр
 * 02.11.2026).
 */
object HiddenAppsRoute {
    /** Путь маршрута в навигационном графе. */
    const val route: String = "apps-hidden"
}

/**
 * Регистрирует экран скрытых приложений.
 *
 * @param viewModel общий экземпляр лаунчера: тот же список, что в сетке, — не нужен второй каталог.
 * @param onBack возврат в настройки.
 */
fun NavGraphBuilder.hiddenAppsScreen(viewModel: LauncherViewModel, onBack: () -> Unit) {
    composable(HiddenAppsRoute.route) {
        HiddenAppsScreen(viewModel = viewModel, onBack = onBack)
    }
}
