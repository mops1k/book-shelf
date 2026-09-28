plugins {
    id("bookshelf.android.library")
    id("bookshelf.android.hilt")
}

android {
    namespace = "io.github.bookshelf.core.covers"
}

dependencies {
    api(project(":core:common"))
    api(libs.coil.core)
    implementation(libs.androidx.core.ktx)
    // Общие фейки лежат в обычном модуле :core:testing, а не в src/testFixtures: Android-фича
    // testFixtures не создаёт задачу компиляции Kotlin.
    testImplementation(project(":core:testing"))
}
