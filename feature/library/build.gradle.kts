plugins {
    id("bookshelf.android.feature")
}

android {
    namespace = "io.github.bookshelf.feature.library"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    // Настройки полки (плотность, число колонок, сортировка по умолчанию) — публичный API
    // :core:datastore; feature-модуль обращается к нему напрямую, а не через :core:data.
    implementation(project(":core:datastore"))
    implementation(project(":core:covers"))
    implementation(project(":core:readerlink"))
    testImplementation(project(":core:testing"))
}
