package io.github.bookshelf.feature.settings

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import io.github.bookshelf.feature.settings.ui.SettingsCategoryScreen

/**
 * Маршрут экрана одной категории настроек («E-ink UX» §6.8).
 *
 * Категория передаётся именем [SettingsSectionKind], а не индексом: список разделов можно
 * переставить или дополнить, и сохранённый в бэкстеке маршрут после этого не откроет чужой экран.
 */
object SettingsCategoryRoute {
    /** Имя аргумента с названием категории (`SettingsSectionKind.name`). */
    const val ARG_CATEGORY: String = "category"

    /** Шаблон маршрута: одна категория настроек. */
    const val route: String = "settings/{$ARG_CATEGORY}"

    /** Собрать путь к экрану конкретной категории. */
    fun createRoute(kind: SettingsSectionKind): String = "settings/${kind.name}"
}

/**
 * Регистрирует экран категории настроек в навигационном графе.
 *
 * Неизвестное имя категории (например, маршрут остался в бэкстеке после переименования) — это
 * не повод показать пустой экран: возвращаемся к списку настроек.
 *
 * @param onBack возврат к списку настроек.
 * @param onOpenCatalogs переход в браузер каталогов OPDS.
 * @param onOpenScan переход к экрану сканирования: его отчёт с проблемными файлами иначе
 *   доступен только с пустой полки, то есть при живой библиотеке — никак.
 */
fun NavGraphBuilder.settingsCategoryScreen(
    onBack: () -> Unit,
    onOpenCatalogs: () -> Unit,
    onOpenScan: () -> Unit,
) {
    composable(
        route = SettingsCategoryRoute.route,
        arguments = listOf(
            navArgument(SettingsCategoryRoute.ARG_CATEGORY) { type = NavType.StringType },
        ),
    ) { entry ->
        val raw = entry.arguments?.getString(SettingsCategoryRoute.ARG_CATEGORY).orEmpty()
        val kind = runCatching { SettingsSectionKind.valueOf(raw) }.getOrNull()
        if (kind == null) {
            onBack()
        } else {
            SettingsCategoryScreen(
                kind = kind,
                onBack = onBack,
                onOpenCatalogs = onOpenCatalogs,
                onOpenScan = onOpenScan,
            )
        }
    }
}
