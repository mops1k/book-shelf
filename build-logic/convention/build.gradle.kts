plugins {
    `kotlin-dsl`
}

group = "io.github.bookshelf.buildlogic"

dependencies {
    // `implementation`, а не `compileOnly`: плагины должны быть на classpath в момент применения.
    // Версии совпадают с версиями основного билда (общий version catalog),
    // поэтому «Kotlin Gradle plugin loaded multiple times» не возникает.
    implementation(libs.gradlePlugin.android)
    implementation(libs.gradlePlugin.kotlin)
    implementation(libs.gradlePlugin.compose)
    implementation(libs.gradlePlugin.ksp)
    implementation(libs.gradlePlugin.hilt)
    implementation(libs.gradlePlugin.detekt)
    // Kover применяется общей конвенцией (`configureKover`), поэтому плагин нужен здесь, а не
    // в build-файлах модулей: Kover 0.9.x не собирает покрытие чужих модулей из корня.
    implementation(libs.gradlePlugin.kover)
}

// Id плагинов, которые подключают модули: id("bookshelf.android.library") и т.д.
gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "bookshelf.android.application"
            implementationClass = "io.github.bookshelf.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "bookshelf.android.library"
            implementationClass = "io.github.bookshelf.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("androidComposeLibrary") {
            id = "bookshelf.android.library.compose"
            implementationClass = "io.github.bookshelf.buildlogic.AndroidComposeLibraryConventionPlugin"
        }
        register("androidFeature") {
            id = "bookshelf.android.feature"
            implementationClass = "io.github.bookshelf.buildlogic.AndroidFeatureConventionPlugin"
        }
        register("androidHilt") {
            id = "bookshelf.android.hilt"
            implementationClass = "io.github.bookshelf.buildlogic.AndroidHiltConventionPlugin"
        }
        register("androidRoom") {
            id = "bookshelf.android.room"
            implementationClass = "io.github.bookshelf.buildlogic.AndroidRoomConventionPlugin"
        }
        register("jvmLibrary") {
            id = "bookshelf.jvm.library"
            implementationClass = "io.github.bookshelf.buildlogic.JvmLibraryConventionPlugin"
        }
    }
}
