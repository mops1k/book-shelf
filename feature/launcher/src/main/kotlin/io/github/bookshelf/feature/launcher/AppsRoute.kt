package io.github.bookshelf.feature.launcher

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.bookshelf.feature.launcher.ui.LauncherScreen

/**
 * Маршрут экрана «Приложения» (ADR-015).
 *
 * Экран — вторая вкладка нижней панели, поэтому приходит с полки, а не открывается отдельным
 * пунктом меню. В системном списке приложений его представляет `activity-alias` (фаза 6), и это
 * отдельная точка входа, а не этот маршрут.
 */
object AppsRoute {
    /** Путь маршрута в навигационном графе. */
    const val route: String = "apps"
}

/**
 * Регистрирует экран «Приложения» в навигационном графе.
 *
 * @param viewModel общий на всё приложение экземпляр: при переключении вкладок запись навигации
 *   списка приложений уничтожается, и без общего ViewModel поиск и список пересоздавались бы при
 *   каждом возврате — то есть «состояние вкладок сохраняется» (решение 02.11.2026) не выполнялось бы.
 * @param onOpenShelf возврат на полку касанием активной панели вкладок.
 * @param onOpenSettings переход в настройки по шестерёнке рядом с поиском.
 */
fun NavGraphBuilder.launcherScreen(
    viewModel: LauncherViewModel,
    onOpenShelf: () -> Unit,
    onOpenSettings: () -> Unit,
    showTabBar: Boolean = true,
) {
    composable(AppsRoute.route) {
        LauncherScreen(
            viewModel = viewModel,
            onOpenShelf = onOpenShelf,
            onOpenSettings = onOpenSettings,
            showTabBar = showTabBar,
        )
    }
}
