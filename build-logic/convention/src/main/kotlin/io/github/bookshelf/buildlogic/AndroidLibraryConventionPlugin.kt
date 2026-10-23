package io.github.bookshelf.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType

/**
 * Базовый Android library-модуль: SDK, Java 17, desugaring, unit-тесты, detekt.
 *
 * Общие тестовые фейки переиспользуются через `:core:testing` обычной зависимостью
 * `testImplementation`, а не через Android-фичу `testFixtures`: на AGP 8.7.3 + KGP 2.0.21
 * для `testFixtures` не создаётся задача компиляции Kotlin, и `.kt` из `src/testFixtures`
 * молча не собираются. См. `configureUnitTests` в `KotlinAndroid.kt`.
 */
class AndroidLibraryConventionPlugin : Plugin<Project> {

    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("com.android.library")

        val extension = extensions.getByType<LibraryExtension>()
        configureKotlinAndroid(extension)
        configureDetekt()
        configureUnitTests()

        extension.apply {
            buildFeatures {
                buildConfig = false
            }
        }
    }
}
