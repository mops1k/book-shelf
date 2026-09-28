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
}
