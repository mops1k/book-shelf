plugins {
    id("bookshelf.android.library.compose")
}

android {
    namespace = "io.github.bookshelf.core.designsystem"
}

dependencies {
    api(project(":core:common"))
    // Coil без сетевого модуля: обложки только локальные («Архитектура» §5)
    api(libs.coil.core)
    api(libs.coil.compose)
}
