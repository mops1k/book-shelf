package io.github.bookshelf.feature.onboarding

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.bookshelf.feature.onboarding.ui.OnboardingScreen

/** Маршрут онбординга — первый запуск приложения (docs/eink-ux.md §6.1). */
object OnboardingRoute {
    /** Путь маршрута в навигационном графе. */
    const val route: String = "onboarding"
}

/**
 * Регистрирует экран онбординга в навигационном графе.
 *
 * @param onFinished вызывается по кнопке «Собрать библиотеку»: дальше открывается
 *   экран сканирования (Sprint 2).
 */
fun NavGraphBuilder.onboardingScreen(
    onFinished: () -> Unit,
) {
    composable(OnboardingRoute.route) {
        OnboardingScreen(onFinished = onFinished)
    }
}
