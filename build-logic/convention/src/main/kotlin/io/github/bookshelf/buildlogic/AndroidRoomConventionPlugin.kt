package io.github.bookshelf.buildlogic

import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * Room (KSP) с экспортом схемы.
 *
 * Экспорт схемы обязателен: миграции проверяются тестом `MigrationTestHelper`, а не «на глаз»
 * (docs/tdd.md §4). Каталог `schemas/` лежит в репозитории вместе с кодом.
 */
class AndroidRoomConventionPlugin : Plugin<Project> {

    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("com.google.devtools.ksp")

        dependencies {
            add("implementation", lib("androidx-room-runtime"))
            add("implementation", lib("androidx-room-ktx"))
            add("ksp", lib("androidx-room-compiler"))
            add("testImplementation", lib("androidx-room-testing"))
        }

        extensions.configure<KspExtension> {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }
}
