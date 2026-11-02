package io.github.bookshelf.feature.launcher

import io.github.bookshelf.core.applist.model.InstalledApp
import io.github.bookshelf.core.applist.rule.AppListRules
import io.github.bookshelf.core.datastore.model.AppListView
import io.github.bookshelf.core.datastore.model.AppSortMode

/**
 * Состояние экрана «Приложения» (ADR-015).
 *
 * Сырые данные (список от каталога, предпочтения, счётчики) хранятся как есть, а видимый список
 * считается свойством [visible]: так поиск, скрытие и сортировка не дублируют друг друга в
 * нескольких полях состояния, а правило остаётся одним — из `:core:applist`.
 */
data class LauncherUiState(
    val apps: List<InstalledApp> = emptyList(),
    val ownPackage: String = "",
    val query: String = "",
    val sortMode: AppSortMode = AppSortMode.NAME,
    val viewMode: AppListView = AppListView.GRID,
    val showSystemApps: Boolean = false,
    val favorites: Set<String> = emptySet(),
    val hidden: Set<String> = emptySet(),
    val launchCounts: Map<String, Int> = emptyMap(),
    val loading: Boolean = true,
    val error: LauncherError? = null,
) {

    /** Что видит пользователь: отбор → поиск → порядок. */
    val visible: List<InstalledApp>
        get() {
            val base = AppListRules.visible(
                apps = apps,
                hiddenPackages = hidden,
                showSystem = showSystemApps,
                ownPackage = ownPackage,
            )
            val found = AppListRules.search(base, query)
            return when (sortMode) {
                AppSortMode.NAME -> AppListRules.byName(found, favorites)
                AppSortMode.LAUNCH_FREQUENCY -> AppListRules.byLaunchFrequency(found, launchCounts, favorites)
            }
        }
}

/** Ошибки экрана: тексты берутся из ресурсов, сюда попадает только код. */
enum class LauncherError {
    /** Каталог не удалось прочитать (система не отдала список). */
    LOAD_FAILED,

    /** Приложение больше нельзя запустить: его удалили или отключили активность. */
    LAUNCH_FAILED,
}
