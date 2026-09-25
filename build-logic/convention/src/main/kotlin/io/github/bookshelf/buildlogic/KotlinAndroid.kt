package io.github.bookshelf.buildlogic

import com.android.build.api.dsl.CommonExtension
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/** Единственное место, где заданы уровни SDK. Меняем здесь — меняется во всех модулях. */
internal const val COMPILE_SDK = 35
internal const val MIN_SDK = 24
internal const val TARGET_SDK = 35
internal const val JVM_TARGET = "17"

/**
 * Общая настройка Android-модуля: SDK, Java/Kotlin 17, core library desugaring, тесты, lint.
 *
 * Desugaring включён везде осознанно: `java.time` и часть `java.nio` нужны на API 24,
 * а без него код пришлось бы писать на `Calendar`. См. `docs/architecture.md` §1.
 */
internal fun Project.configureKotlinAndroid(extension: CommonExtension<*, *, *, *, *, *>) {
    extension.apply {
        compileSdk = COMPILE_SDK

        defaultConfig {
            minSdk = MIN_SDK
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
            isCoreLibraryDesugaringEnabled = true
        }

        packaging {
            resources.excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*",
            )
        }

        testOptions {
            unitTests.isReturnDefaultValues = true
            // Robolectric-тестам нужны ресурсы и манифест модуля.
            unitTests.isIncludeAndroidResources = true
        }

        lint {
            abortOnError = true
            warningsAsErrors = false
            // Зависимости проверяются отдельной задачей, а не при каждой сборке модуля.
            checkDependencies = false
        }
    }

    configureKotlinAndroidOptions()

    dependencies {
        add("coreLibraryDesugaring", lib("android-desugar-jdk-libs"))
    }
}

private fun Project.configureKotlinAndroidOptions() {
    extensions.configure<KotlinAndroidProjectExtension> {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
}

/** Kotlin/JVM-модуль (без Android): нужен для чистых парсеров, которые не тянут платформу. */
internal fun Project.configureKotlinJvm() {
    extensions.configure<KotlinJvmProjectExtension> {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    dependencies {
        add("implementation", lib("kotlinx-coroutines-core"))
    }
}

/**
 * Тестовые зависимости, одинаковые для всех модулей.
 *
 * TDD — обязательный процесс проекта (docs/tdd.md), поэтому тестовый стек подключён
 * везде по умолчанию, а не «когда понадобится».
 */
internal fun Project.configureUnitTests() {
    dependencies {
        add("testImplementation", lib("junit"))
        add("testImplementation", lib("truth"))
        add("testImplementation", lib("turbine"))
        add("testImplementation", lib("mockk"))
        add("testImplementation", lib("kotlinx-coroutines-test"))
        add("testImplementation", lib("robolectric"))
        add("testImplementation", lib("androidx-test-core-ktx"))

        // Фейки ядра (часы, логгер, диспетчеры) доступны всем тестам, кроме тестов самого ядра:
        // ссылка на собственные testFixtures была бы циклической зависимостью.
        if (path != ":core:common") {
            add("testImplementation", testFixtures(project(":core:common")))
        }
    }

    configureTestTaskDefaults()
}

/** Тестовый стек чистого Kotlin/JVM-модуля: без Robolectric и Android-зависимостей. */
internal fun Project.configureJvmTests() {
    dependencies {
        add("testImplementation", lib("junit"))
        add("testImplementation", lib("truth"))
        add("testImplementation", lib("turbine"))
        add("testImplementation", lib("mockk"))
        add("testImplementation", lib("kotlinx-coroutines-test"))
    }

    configureTestTaskDefaults()
}

private fun Project.configureTestTaskDefaults() {
    tasks.withType<Test>().configureEach {
        // Стабильность: тесты не должны зависеть от локали и часов машины разработчика.
        systemProperty("user.language", "ru")
        systemProperty("user.country", "RU")
        systemProperty("user.timezone", "Europe/Moscow")
        maxHeapSize = "2g"
        testLogging {
            events("failed")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }
}

/** detekt — единый статический анализ (форматирование — его правилами, ktlint отдельно не тянем). */
internal fun Project.configureDetekt() {
    pluginManager.apply("io.gitlab.arturbosch.detekt")

    extensions.configure<DetektExtension> {
        buildUponDefaultConfig = true
        parallel = true
        config.setFrom(rootProject.layout.projectDirectory.file("config/detekt/detekt.yml"))
    }

    tasks.withType<Detekt>().configureEach {
        jvmTarget = JVM_TARGET
        reports {
            html.required.set(true)
            xml.required.set(true)
            sarif.required.set(false)
            md.required.set(false)
            txt.required.set(false)
        }
    }
}
