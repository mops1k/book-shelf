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

// Тесты модуля идут с тем же потолком кучи, что и приложение на устройстве (256 МБ): оптимизация
// книги в 13 МБ обязана укладываться в него, иначе на e-ink-читалке система убивает процесс.
tasks.withType<Test>().configureEach {
    maxHeapSize = "256m"
    testLogging { showStandardStreams = true }
}
