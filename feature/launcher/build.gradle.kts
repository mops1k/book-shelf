plugins {
    id("bookshelf.android.feature")
}

android {
    namespace = "io.github.bookshelf.feature.launcher"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    // Каталог приложений и правила списка — публичный API :core:applist (ADR-015).
    implementation(project(":core:applist"))
    // Состояние лаунчера (избранное, скрытые, сортировка, счётчики) — публичный API :core:datastore.
    implementation(project(":core:datastore"))
    testImplementation(project(":core:testing"))
}
