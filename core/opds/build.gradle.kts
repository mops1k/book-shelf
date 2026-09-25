plugins {
    id("bookshelf.android.library")
    id("bookshelf.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.bookshelf.core.opds"
}

dependencies {
    api(project(":core:common"))
    // Единственный модуль с сетью. В офлайн-сборке отсутствует (docs/opds.md)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.okhttp.mockwebserver)

    // TODO(Sprint 8): загрузка книг из каталога требует
    //  implementation(libs.androidx.work.runtime.ktx) — DownloadWorker;
    //  project(":core:database") — таблицы opds_catalogs/opds_downloads/opds_feed_cache;
    //  project(":core:storage") — запись .part-файла в выбранную папку источника.
    // TODO(Sprint 8): CredentialStore — шифрованное хранение паролей каталогов.
    //  Кандидат — androidx.security:security-crypto; перед добавлением проверить
    //  актуальный статус библиотеки (ветка 1.1.x долго в alpha) и рассмотреть
    //  EncryptedFile/Keystore напрямую. Решение — отдельная запись ADR.
}
