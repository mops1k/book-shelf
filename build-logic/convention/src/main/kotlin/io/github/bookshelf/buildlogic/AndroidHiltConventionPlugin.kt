package io.github.bookshelf.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Hilt (KSP) для модуля, который объявляет `@Inject`/`@Module`, но не обязательно UI. */
class AndroidHiltConventionPlugin : Plugin<Project> {

    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("com.google.devtools.ksp")
        pluginManager.apply("com.google.dagger.hilt.android")

        dependencies {
            add("implementation", lib("hilt-android"))
            add("ksp", lib("hilt-compiler"))
        }
    }
}
