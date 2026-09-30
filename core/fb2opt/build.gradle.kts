plugins {
    id("bookshelf.android.library")
    id("bookshelf.android.hilt")
}

android {
    namespace = "io.github.bookshelf.core.fb2opt"
}

dependencies {
    implementation(project(":core:common"))

    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
}
