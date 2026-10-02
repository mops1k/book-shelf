package io.github.bookshelf.feature.onboarding

/**
 * Чистая логика шагов онбординга («E-ink UX» §6.1).
 *
 * Вынесена из ViewModel и Composable отдельным файлом: переходы по шагам — единственная
 * часть экрана, которую можно и нужно проверить тестом без устройства.
 */

/** Следующий шаг; на последнем шаге остаёмся на месте (завершение — отдельное действие). */
fun OnboardingStep.next(): OnboardingStep = OnboardingStep.entries.getOrElse(ordinal + 1) { this }

/** Предыдущий шаг; на первом шаге остаёмся на месте. */
fun OnboardingStep.previous(): OnboardingStep = OnboardingStep.entries.getOrElse(ordinal - 1) { this }

/**
 * Собрать библиотеку можно только когда выбрана хотя бы одна папка: без источника
 * сканировать нечего («Архитектура» §4.1).
 */
fun canFinishOnboarding(sources: List<SourceListItem>): Boolean = sources.isNotEmpty()
