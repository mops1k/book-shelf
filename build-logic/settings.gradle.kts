// Included build с convention-плагинами: один раз описываем, как собирается модуль,
// и подключаем это в 22 модулях по id. Версии — из общего каталога основного билда.
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
include(":convention")
