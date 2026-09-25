plugins {
    id("bookshelf.android.library")
    id("bookshelf.android.hilt")
}

android {
    namespace = "io.github.bookshelf.core.metadata"
}

dependencies {
    api(project(":core:common"))
    // определение кодировок FB2/TXT; ICU4J сознательно не подключаем (docs/formats.md)
    implementation(libs.juniversalchardet)
    // TODO(Sprint 3): epub4j — только если свой парсер EPUB окажется дороже (ADR-003)
    // implementation(libs.epub4j.core)
}
