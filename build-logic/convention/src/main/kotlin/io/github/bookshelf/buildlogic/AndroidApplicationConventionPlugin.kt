package io.github.bookshelf.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

/**
 * `:app` — единственный application-модуль: Application, MainActivity, навигационный граф,
 * корневой DI-граф, WorkManager-конфигурация, flavors.
 *
 * Всё содержательное живёт в `:core:*` и `:feature:*`; `:app` только связывает.
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {

    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("com.android.application")
        pluginManager.apply("org.jetbrains.kotlin.android")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")
        pluginManager.apply("com.google.devtools.ksp")
        pluginManager.apply("com.google.dagger.hilt.android")

        configureKotlinAndroid(extensions.getByType<ApplicationExtension>())
        configureDetekt()
        configureUnitTests()

        extensions.configure<ApplicationExtension> {
            defaultConfig {
                applicationId = APPLICATION_ID
                targetSdk = TARGET_SDK
                versionCode = appVersionCode()
                versionName = appVersionName()
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                vectorDrawables.useSupportLibrary = true
            }

            buildFeatures {
                buildConfig = true
                compose = true
            }

            // Одно flavor-измерение `store`: от него зависят разрешения, наличие CBR
            // и способ доступа к файлам. Ветвлений `if (flavor == ...)` в коде быть не должно —
            // только флаги BuildConfig. См. docs/architecture.md §9 и ADR-007.
            flavorDimensions += STORE_FLAVOR_DIMENSION
            productFlavors {
                create("full") {
                    dimension = STORE_FLAVOR_DIMENSION
                    buildConfigField("boolean", "ENABLE_CBR", "true")
                    buildConfigField("boolean", "ALLOW_ALL_FILES_ACCESS", "true")
                    buildConfigField("boolean", "ENABLE_OPDS", "true")
                }
                create("fdroid") {
                    dimension = STORE_FLAVOR_DIMENSION
                    // junrar под несвободной UnRar License — F-Droid её не принимает.
                    buildConfigField("boolean", "ENABLE_CBR", "false")
                    buildConfigField("boolean", "ALLOW_ALL_FILES_ACCESS", "true")
                    buildConfigField("boolean", "ENABLE_OPDS", "true")
                }
                create("play") {
                    dimension = STORE_FLAVOR_DIMENSION
                    buildConfigField("boolean", "ENABLE_CBR", "false")
                    // MANAGE_EXTERNAL_STORAGE требует Declaration Form — в play-сборке недоступно.
                    buildConfigField("boolean", "ALLOW_ALL_FILES_ACCESS", "false")
                    buildConfigField("boolean", "ENABLE_OPDS", "true")
                }
                // 4-й flavor `play-files` (Play + доступ ко всем файлам) сознательно не создан:
                // решение зависит от площадки публикации (открытый вопрос № 5 в docs/decisions.md).
            }

            // Подпись релиза: локально ключ из `keystore.properties`, в CI — из переменных
            // окружения. Ключа нет — конфигурация остаётся пустой и не подключается (см.
            // ReleaseSigning.kt): молча выпустить неподписанный релиз нельзя, это ловит workflow.
            signingConfigs {
                create(RELEASE_SIGNING_CONFIG) {
                    releaseSigningKey()?.let { key ->
                        storeFile = key.storeFile
                        storePassword = key.storePassword
                        keyAlias = key.keyAlias
                        keyPassword = key.keyPassword
                    }
                }
            }

            buildTypes {
                getByName("debug") {
                    applicationIdSuffix = ".debug"
                    versionNameSuffix = "-debug"
                    isMinifyEnabled = false
                }
                getByName("release") {
                    isMinifyEnabled = true
                    isShrinkResources = true
                    proguardFiles(
                        getDefaultProguardFile("proguard-android-optimize.txt"),
                        "proguard-rules.pro",
                    )
                    // Подпись подключается только когда ключ есть (ReleaseSigning.kt): иначе
                    // release собирается неподписанным и годится лишь как артефакт проверки.
                    if (releaseSigningKey() != null) {
                        signingConfig = signingConfigs.getByName(RELEASE_SIGNING_CONFIG)
                    }
                }
            }
        }

        dependencies {
            add("implementation", platform(lib("androidx-compose-bom")))
            add("implementation", lib("androidx-compose-ui"))
            add("implementation", lib("androidx-compose-ui-graphics"))
            add("implementation", lib("androidx-compose-ui-tooling-preview"))
            add("implementation", lib("androidx-compose-material3"))
            add("implementation", lib("androidx-activity-compose"))
            add("implementation", lib("androidx-core-ktx"))
            add("implementation", lib("androidx-lifecycle-runtime-ktx"))
            add("implementation", lib("androidx-lifecycle-runtime-compose"))
            add("implementation", lib("androidx-navigation-compose"))
            add("implementation", lib("androidx-splashscreen"))
            add("implementation", lib("androidx-profileinstaller"))
            add("implementation", lib("androidx-work-runtime-ktx"))
            add("implementation", lib("hilt-android"))
            add("ksp", lib("hilt-compiler"))
            add("implementation", lib("androidx-hilt-work"))
            add("ksp", lib("androidx-hilt-compiler"))
            add("debugImplementation", lib("androidx-compose-ui-tooling"))
            add("debugImplementation", lib("androidx-compose-ui-test-manifest"))
        }
    }

    private companion object {
        const val APPLICATION_ID = "io.github.bookshelf"
        const val STORE_FLAVOR_DIMENSION = "store"

        /** Имя конфигурации подписи релиза: `signingConfigs.release`. */
        const val RELEASE_SIGNING_CONFIG = "release"
    }
}
