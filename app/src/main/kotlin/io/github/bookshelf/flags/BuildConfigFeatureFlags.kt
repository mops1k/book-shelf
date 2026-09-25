package io.github.bookshelf.flags

import io.github.bookshelf.BuildConfig
import io.github.bookshelf.core.common.flags.FeatureFlags

/**
 * Единственное место, где flavor-сборка превращается в поведение.
 *
 * `BuildConfig` доступен только в `:app`, поэтому именно здесь флаги читаются, а `core:*`
 * и `feature:*` получают обычный интерфейс [FeatureFlags]. Благодаря этому:
 * * в коде нет ни одного `if (flavor == …)`;
 * * поведение «сборка без CBR» проверяется тестом с фейковыми флагами, а не сравнением
 *   двух APK вручную.
 */
class BuildConfigFeatureFlags : FeatureFlags {

    override val cbrEnabled: Boolean = BuildConfig.ENABLE_CBR

    override val allowAllFilesAccess: Boolean = BuildConfig.ALLOW_ALL_FILES_ACCESS

    override val opdsEnabled: Boolean = BuildConfig.ENABLE_OPDS
}
