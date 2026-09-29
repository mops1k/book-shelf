plugins {
    id("bookshelf.android.library")
    id("bookshelf.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.bookshelf.core.data"
}

dependencies {
    // Единственный модуль, который видит БД, SAF, парсеры и кэш обложек одновременно.
    // Feature-модули зависят только от :core:data и не знают про Room.
    api(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:storage"))
    // `api`, а не `implementation`: тип `MetadataEdit` входит в публичный контракт порта
    // `BookMetadataEditor`, и модули, которые им пользуются, должны его видеть.
    api(project(":core:metadata"))
    implementation(project(":core:covers"))
    implementation(project(":core:readerlink"))
    implementation(project(":core:opds"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.mockk)
    // Общие фейки (свои интерфейсы в тестах не мокаются — ADR-012, правило 2).
    testImplementation(project(":core:testing"))
}
