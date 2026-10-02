plugins {
    id("bookshelf.android.application")
}

// :app — только связывание. Никакой бизнес-логики: всё живёт в core:*/feature:*.
// Именно поэтому app зависит от всех модулей, а не наоборот.
android {
    namespace = "io.github.bookshelf"
}

dependencies {
    // core
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:storage"))
    implementation(project(":core:metadata"))
    implementation(project(":core:covers"))
    implementation(project(":core:readerlink"))
    implementation(project(":core:opds"))
    implementation(project(":core:eink"))

    // Обложки записей OPDS приходят ссылкой, и Coil нужен сетевой загрузчик: без него
    // `AsyncImage(coverUrl)` молча не рисует ничего. OkHttp тот же, что у каталога, — чтобы
    // обложки грузились там, где DNS каталога заблокирован (DoH-резолвер из :core:opds).
    implementation(libs.coil.network.okhttp)
    implementation(libs.okhttp)

    // CBR (RAR с комиксом) — только во flavor `full`: junrar распространяется под несвободной
    // UnRar License, которую не принимает F-Droid (ADR-007). Библиотека нужна ровно одному
    // классу — `app/src/full/.../rar/JunrarRarArchive.kt`; в остальных сборках этого источника
    // нет, набор реализаций чтения RAR пуст, и формат честно не предлагается.
    "fullImplementation"(libs.junrar)

    // Инструментальные тесты: провайдер SAF, FileProvider, запуск приложения и пробуждение
    // чужих activity обычными юнит-тестами не проверить — нужен живой Android и настоящие
    // системные службы («TDD» §4). Раннер задан в convention-плагине
    // (`androidx.test.runner.AndroidJUnitRunner`).
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core.ktx)
    // Truth подключается к androidTest явно: проверки в инструментальных тестах пишутся тем же
    // стилем, что и везде в проекте, и не должны зависеть от состава фейков в `:core:testing`.
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.espresso.core)
    // Фейки репозиториев и портов: экраны в инструментальных тестах собираются с настоящим
    // ViewModel и подставными данными (ADR-012: свои типы не мокаются).
    // Robolectric из этой зависимости исключается: он нужен JVM-тестам, но в инструментальном APK
    // его `Shadow` инициализируется на живом устройстве и роняет прогон целиком
    // (`No field IMPL_LOOKUP in class Ljava/lang/invoke/MethodHandles$Lookup` — проверено на
    // Bigme B6 Color 02.10.2026).
    androidTestImplementation(project(":core:testing")) {
        exclude(group = "org.robolectric")
    }
    // Compose-артефакты версий не несут: их задаёт BOM, а он в convention-плагине подключён
    // только к `implementation` и `testImplementation`, поэтому для androidTest — здесь.
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    // `createComposeRule` без своей activity берёт её из этого артефакта (только debug).
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // features
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:library"))
    implementation(project(":feature:bookdetails"))
    implementation(project(":feature:collections"))
    implementation(project(":feature:search"))
    implementation(project(":feature:filters"))
    implementation(project(":feature:catalogs"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:scan"))

    // Инструментальные тесты (androidTest) и Robolectric-инфраструктура.
    // Только testImplementation: :core:testing тянет через api() junit, truth, turbine, mockk
    // и robolectric — в production APK им места нет.
    testImplementation(project(":core:testing"))
    // Тестовый WorkManager: проверка, что расписание действительно поставлено.
    testImplementation(libs.androidx.work.testing)
}
