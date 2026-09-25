plugins {
    id("bookshelf.android.library")
    id("bookshelf.android.hilt")
}

android {
    namespace = "io.github.bookshelf.core.eink"
}

dependencies {
    api(project(":core:common"))
    // Onyx SDK подключается как compileOnly и только при реализации OnyxEinkController
    // (Phase 3). Пока классы не используются напрямую — только рефлексия, поэтому
    // зависимость не объявлена: сборка не должна требовать repo.boox.com.
    // TODO(Sprint 10): compileOnly(libs.onyx.device); compileOnly(libs.onyx.base)
}
