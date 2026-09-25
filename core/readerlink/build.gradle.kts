plugins {
    id("bookshelf.android.library")
    id("bookshelf.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.bookshelf.core.readerlink"
}

dependencies {
    api(project(":core:common"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.serialization.json)
    // TODO(Sprint 5): профили читалок и дефолты по формату живут в БД и настройках:
    //  implementation(project(":core:database")) — таблица reader_profiles;
    //  implementation(project(":core:datastore")) — format_defaults и «последняя читалка».
    // TODO(Sprint 7): включить aidl = true в android.buildFeatures, когда появятся
    // вендоренные AIDL-файлы FBReader (public domain) — см. src/main/aidl/README.md
}
