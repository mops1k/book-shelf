package io.github.bookshelf.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

/**
 * Android library с Compose (дизайн-система и feature-модули).
 *
 * Отдельный id, а не «флаг в библиотечном плагине»: явно видно, какие модули рисуют UI,
 * а какие остаются чистой логикой. Для e-ink это важно — Compose допустим только в UI-слое.
 */
class AndroidComposeLibraryConventionPlugin : Plugin<Project> {

    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("bookshelf.android.library")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        extensions.getByType<LibraryExtension>().apply {
            buildFeatures {
                compose = true
            }
        }

        dependencies {
            add("implementation", platform(lib("androidx-compose-bom")))
            add("implementation", lib("androidx-compose-ui"))
            add("implementation", lib("androidx-compose-ui-graphics"))
            add("implementation", lib("androidx-compose-ui-tooling-preview"))
            add("implementation", lib("androidx-compose-foundation"))
            add("implementation", lib("androidx-compose-material3"))
            add("implementation", lib("androidx-compose-material-icons-core"))
            add("debugImplementation", lib("androidx-compose-ui-tooling"))
            // Compose UI-тесты (Robolectric и/или device) — часть TDD-процесса, не «на потом».
            add("testImplementation", platform(lib("androidx-compose-bom")))
            add("testImplementation", lib("androidx-compose-ui-test-junit4"))
            add("debugImplementation", lib("androidx-compose-ui-test-manifest"))
        }
    }
}
