package io.github.bookshelf.buildlogic

import com.android.build.api.dsl.LibraryExtension
import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

/**
 * Room (KSP) с экспортом схемы.
 *
 * Экспорт схемы обязателен: миграции проверяются тестом `MigrationTestHelper`, а не «на глаз»
 * («TDD» §4). Каталог `schemas/` лежит в репозитории вместе с кодом.
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

        // MigrationTestHelper читает экспортированные схемы из assets теста, а не с диска:
        // без этого он падает с FileNotFoundException на `…/BookshelfDatabase/1.json`.
        // Подключаем весь каталог schemas/ — он один на модуль и коммитится в репозиторий.
        pluginManager.withPlugin("com.android.library") {
            val android = extensions.getByType<LibraryExtension>()
            android.sourceSets.getByName("test").assets.directories.add("$projectDir/schemas")
        }
    }
}
