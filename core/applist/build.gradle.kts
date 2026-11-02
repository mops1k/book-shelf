plugins {
    id("bookshelf.android.library")
    id("bookshelf.android.hilt")
}

android {
    namespace = "io.github.bookshelf.core.applist"
}

dependencies {
    api(project(":core:common"))
    // Иконки чужих приложений: свой Fetcher поверх Drawable из PackageManager.
    // Сетевого загрузчика здесь нет и не будет — список приложений офлайновый.
    api(libs.coil.core)
    implementation(libs.androidx.core.ktx)
    // Общие фейки и правила JUnit лежат в :core:testing, а не в src/testFixtures:
    // Android-библиотека testFixtures не создаёт задачу компиляции Kotlin.
    testImplementation(project(":core:testing"))
}
