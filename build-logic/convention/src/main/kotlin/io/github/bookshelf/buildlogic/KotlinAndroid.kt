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

    // Opt-in Compose-маркера подключается только модулям с Compose: в чистых модулях логики
    // (`core:data`, `core:opds`, `core:metadata` и т. п.) самой зависимости нет, и компилятор
    // с KSP пишут «unresolved opt-in marker androidx.compose.foundation.layout.ExperimentalLayoutApi».
    // Маркер нужен для FlowRow: без него каждый экран с блоком `actions` обязан ставить `@OptIn`
    // у себя — 13 аннотаций вместо одной настройки, и любая новая кнопка снова ломает сборку.
    COMPOSE_ENABLED_PLUGINS.forEach { pluginId ->
        pluginManager.withPlugin(pluginId) { addFlowRowOptIn() }
    }
}

private fun Project.addFlowRowOptIn() {
    extensions.configure<KotlinAndroidProjectExtension> {
        compilerOptions {
            optIn.add("androidx.compose.foundation.layout.ExperimentalLayoutApi")
        }
    }
}

/** Плагины, после которых в модуле точно есть `androidx.compose.foundation`. */
private val COMPOSE_ENABLED_PLUGINS = listOf(
    "bookshelf.android.library.compose",
    "bookshelf.android.application",
)

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

        // Общие фейки (часы, логгер, диспетчеры, флаги) живут в `:core:testing` и подключаются
        // обычной зависимостью, а не через Android-фичу `testFixtures`: на AGP 8.7.3 + KGP 2.0.21
        // для `testFixtures` не создаётся задача компиляции Kotlin, поэтому `.kt` из
        // `src/testFixtures` молча не собираются, а `testImplementation(testFixtures(...))`
        // подключает пустую банку. Исключения — сам `:core:common` и `:core:testing`, иначе цикл.
        if (path != ":core:common" && path != ":core:testing") {
            add("testImplementation", project(":core:testing"))
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
        // 1 ГБ на тестовую JVM: тесты мелкие, а на машине 7.8 ГБ — при 2 ГБ на JVM параллельные
        // модули вместе с демонами уходили в своп, и сборка растягивалась в разы.
        maxHeapSize = "1g"
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
