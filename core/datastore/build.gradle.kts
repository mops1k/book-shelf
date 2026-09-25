plugins {
    id("bookshelf.android.library")
    id("bookshelf.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.bookshelf.core.datastore"
}

dependencies {
    api(project(":core:common"))
    implementation(libs.androidx.datastore.preferences)
    // Профили читалок и правила умных полок хранятся как JSON
    implementation(libs.kotlinx.serialization.json)
}
