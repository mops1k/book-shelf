package io.github.bookshelf.core.update.model

/**
 * Установленная сборка: версия и flavor (ADR-018).
 *
 * `BuildConfig` живёт только в `:app`, а проверка обновлений — в `:core:update` и `:feature:*`,
 * поэтому сборка описывается обычным значением, которое передаёт DI в `:app`. Тесты подставляют
 * своё, без Android-сборки.
 */
data class AppBuildInfo(
    /** `BuildConfig.VERSION_NAME`, например «2.0.1». */
    val versionName: String,
    val flavor: AppFlavor,
) {

    /** Разобранная версия; `null`, если имя версии не похоже на `X.Y.Z`. */
    val version: AppVersion? get() = AppVersion.parse(versionName)
}
