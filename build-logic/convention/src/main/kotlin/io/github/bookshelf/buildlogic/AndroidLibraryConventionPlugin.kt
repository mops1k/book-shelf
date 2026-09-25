package io.github.bookshelf.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType

/**
 * Базовый Android library-модуль: SDK, Java 17, desugaring, unit-тесты, detekt, testFixtures.
 *
 * `testFixtures` включены по умолчанию: TDD-процесс требует, чтобы фейки и билдеры жили рядом
 * с модулем, который они подменяют, и переиспользовались через
 * `testImplementation(testFixtures(project(":core:data")))`.
 */
class AndroidLibraryConventionPlugin : Plugin<Project> {

    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("com.android.library")
        pluginManager.apply("org.jetbrains.kotlin.android")

        val extension = extensions.getByType<LibraryExtension>()
        configureKotlinAndroid(extension)
        configureDetekt()
        configureUnitTests()

        extension.apply {
            testFixtures {
                enable = true
            }
            buildFeatures {
                buildConfig = false
            }
        }
    }
}
