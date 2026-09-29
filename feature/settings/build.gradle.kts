plugins {
    id("bookshelf.android.feature")
}

android {
    namespace = "io.github.bookshelf.feature.settings"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    // Кэш обложек: раздел «Хранилище и кэш» показывает занятое место и очищает его
    // (`CoverCache.sizeBytes()` / `clear()`).
    implementation(project(":core:covers"))
    implementation(project(":core:data"))
    implementation(project(":core:datastore"))
    implementation(project(":core:storage"))
    implementation(project(":core:readerlink"))
    implementation(project(":core:eink"))
    implementation(project(":core:opds"))
    // Системный выборщик папки и запрос разрешений — оба через activity-compose.
    implementation("androidx.activity:activity-compose")
    testImplementation(project(":core:testing"))
}
