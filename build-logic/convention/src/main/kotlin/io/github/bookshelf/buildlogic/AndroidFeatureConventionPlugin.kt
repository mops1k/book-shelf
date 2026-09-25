package io.github.bookshelf.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Feature-модуль: Compose + Hilt + навигация + lifecycle.
 *
 * Сознательно не добавляет зависимости на `core:*` — конкретные слои каждый feature
 * объявляет сам, чтобы граф зависимостей читался из его build-файла, а не из плагина.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {

    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("bookshelf.android.library.compose")
        pluginManager.apply("bookshelf.android.hilt")

        dependencies {
            add("implementation", lib("androidx-core-ktx"))
            add("implementation", lib("androidx-navigation-compose"))
            add("implementation", lib("androidx-lifecycle-viewmodel-compose"))
            add("implementation", lib("androidx-lifecycle-runtime-compose"))
            add("implementation", lib("androidx-hilt-navigation-compose"))
        }
    }
}
