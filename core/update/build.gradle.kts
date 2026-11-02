plugins {
    id("bookshelf.android.library")
    id("bookshelf.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.bookshelf.core.update"
}

dependencies {
    api(project(":core:common"))
    api(project(":core:datastore"))
    // Единственный сетевой вызов, кроме OPDS: последний релиз на GitHub (ADR-018).
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(project(":core:testing"))
    testImplementation(libs.okhttp.mockwebserver)
}
