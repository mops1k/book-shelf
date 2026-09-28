package io.github.bookshelf.core.common.testing

import io.github.bookshelf.core.common.flags.FeatureFlags

/**
 * Флаги возможностей для тестов — настраиваются под сборку, которую проверяет тест.
 *
 * Живут в `:core:testing`, а не в `src/testFixtures`: Android-фича `testFixtures` не создаёт
 * задачу компиляции Kotlin (проверено на AGP 8.7.3 + KGP 2.0.21), поэтому `.kt` из
 * `src/testFixtures` молча не собираются. `:core:testing` — обычный модуль, который компилируется
 * и подключается через `testImplementation`, поэтому виден тестам всех остальных модулей.
 *
 * Дефолт — сборка `full`: она разрешает всё, поэтому тест, который не выключает флаг явно,
 * проверяет полный набор парсеров. Для проверки «сборки без CBR» достаточно
 * `FakeFeatureFlags(cbrEnabled = false)` — ровно поэтому флаги внедряются зависимостью,
 * а не читаются из `BuildConfig` (ADR-007).
 */
data class FakeFeatureFlags(
    override val cbrEnabled: Boolean = true,
    override val allowAllFilesAccess: Boolean = true,
    override val opdsEnabled: Boolean = true,
) : FeatureFlags
