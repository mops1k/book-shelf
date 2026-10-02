plugins {
    id("bookshelf.android.library")
    id("bookshelf.android.hilt")
    // ScanCursor сохраняется в scan_runs.cursorDocumentId как JSON — курсор возобновляемого скана
    // («Архитектура» §4.5).
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.bookshelf.core.storage"
}

dependencies {
    api(project(":core:common"))
    implementation(libs.androidx.core.ktx)
    // DocumentFile нужен только как вспомогательный API; обход дерева идёт через
    // DocumentsContract напрямую («Архитектура» §4.2)
    implementation(libs.androidx.documentfile)
    implementation(libs.kotlinx.serialization.json)
    // runTest нужен тестам обхода; проверки — на Truth, как во всём проекте.
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    // mockk нужен BookContentOpenerTest: ContentResolver.openInputStream final,
    // и подменить его можно только мок-библиотекой.
    testImplementation(libs.mockk)
    testImplementation(project(":core:testing"))
}
