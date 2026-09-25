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
}
