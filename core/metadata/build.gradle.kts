plugins {
    id("bookshelf.android.library")
    id("bookshelf.android.hilt")
}

android {
    namespace = "io.github.bookshelf.core.metadata"

    sourceSets {
        // Настоящая книга из Flibusta как фикстура: синтетический fb2 написан по моему же
        // пониманию формата и всегда совпадает с парсером — ошибок в нём не видно.
        getByName("test") { resources.srcDir("src/test/resources") }
    }

    testOptions {
        // unit-тесты Android-модуля не видят src/test/resources без этого флага.
        unitTests.isIncludeAndroidResources = true
    }

}


dependencies {
    api(project(":core:common"))
    // определение кодировок FB2/TXT; ICU4J сознательно не подключаем (docs/formats.md)
    implementation(libs.juniversalchardet)
    // TODO(Sprint 3): epub4j — только если свой парсер EPUB окажется дороже (ADR-003)
    // implementation(libs.epub4j.core)
}
