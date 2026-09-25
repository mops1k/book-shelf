// Единая точка сборки. Плагины конвенций лежат в included build `build-logic`,
// поэтому модули подключают их по id (`id("bookshelf.android.library")`), а не настраивают AGP руками.
pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()

        // Публичный Maven-репозиторий Onyx (Boox). Лицензия SDK — BSD-3-Clause.
        // Только http: https-эндпоинт отдаёт DH-ключ, который JVM отвергает (SSL_DH_KEY_TOO_SMALL).
        // Фильтр по группе обязателен, иначе Gradle будет опрашивать этот репозиторий для всех зависимостей.
        maven {
            url = uri("http://repo.boox.com/repository/maven-public/")
            isAllowInsecureProtocol = true
            content { includeGroup("com.onyx.android.sdk") }
        }
    }
}

rootProject.name = "BookShelf"

// :app — точка входа; :core:* — переиспользуемые слои; :feature:* — экраны.
// Правило зависимостей: feature:* -> core:*, никогда feature:* -> feature:*.
include(":app")

include(":core:common")
include(":core:designsystem")
include(":core:database")
include(":core:datastore")
include(":core:data")
include(":core:storage")
include(":core:metadata")
include(":core:covers")
include(":core:readerlink")
include(":core:opds")
include(":core:eink")
include(":core:testing")

include(":feature:onboarding")
include(":feature:library")
include(":feature:bookdetails")
include(":feature:collections")
include(":feature:search")
include(":feature:filters")
include(":feature:catalogs")
include(":feature:settings")
include(":feature:scan")
