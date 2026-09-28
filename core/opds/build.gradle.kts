plugins {
    id("bookshelf.android.library")
    id("bookshelf.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.bookshelf.core.opds"

    sourceSets {
        // Android-модуль по умолчанию не берёт src/test/resources: без этого настоящий фид
        // из resources не найдётся и тест будет проверять не то.
        getByName("test") { resources.srcDir("src/test/resources") }
    }
}

dependencies {
    // Таблицы opds_catalogs/opds_downloads/opds_feed_cache: кэш фидов лежит в БД.
    api(project(":core:database"))

    api(project(":core:common"))
    // Единственный модуль с сетью. В офлайн-сборке отсутствует (docs/opds.md)
    implementation(libs.okhttp)
    // Запись скачанного файла в дерево SAF — без него очередь загрузок ничего не делала.
    implementation(project(":core:storage"))
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
testImplementation(libs.robolectric)
testImplementation(libs.truth)
testImplementation(libs.kotlinx.coroutines.test)
}
