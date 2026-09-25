package io.github.bookshelf.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType

/** Доступ к `gradle/libs.versions.toml` из обычных (не precompiled) convention-плагинов. */
internal val Project.versionCatalog: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

/**
 * Координата зависимости по алиасу каталога.
 *
 * Сознательно не используем `libs.xxx` аксессоры: в `.kt`-плагине их нет, а имена строкой
 * всё равно проверяются при сборке — опечатка падает сразу, с понятным сообщением.
 */
internal fun Project.lib(alias: String): Provider<MinimalExternalModuleDependency> =
    versionCatalog.findLibrary(alias).orElseThrow {
        IllegalStateException(
            "Алиас '$alias' не найден в gradle/libs.versions.toml. " +
                "Проверьте имя в секции [libraries]."
        )
    }

/** Версия строкой из секции `[versions]` каталога (используется для versionName/versionCode). */
internal fun Project.libVersion(alias: String): String =
    versionCatalog.findVersion(alias).orElseThrow {
        IllegalStateException("Версия '$alias' не найдена в секции [versions] gradle/libs.versions.toml")
    }.requiredVersion
