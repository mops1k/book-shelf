plugins {
    id("bookshelf.android.library")
}

android {
    namespace = "io.github.bookshelf.core.testing"
}

// Тестовые утилиты и фейки. Модуль никогда не попадает в APK: на него смотрят только
// конфигурации testImplementation, поэтому в релизной сборке его нет.
//
// ВАЖНО: :core:testing зависит от :core:common, значит :core:common не может использовать
// :core:testing в своих тестах (циклическая зависимость) — там фейки объявляются локально.
dependencies {
    api(project(":core:storage"))
    api(project(":core:common"))
    // Фейки репозиториев и DAO живут здесь: собственные интерфейсы в тестах не мокаются
    // (ADR-012, правило 2), а `src/testFixtures` на AGP 8.7.3 не компилируют Kotlin.
    // Обратной зависимости нет: :core:data и :core:database смотрят на :core:testing только
    // в конфигурации testImplementation, поэтому цикла не возникает.
    api(project(":core:data"))
    api(project(":core:database"))
    api(libs.junit)
    api(libs.truth)
    api(libs.turbine)
    api(libs.mockk)
    api(libs.robolectric)
    api(libs.kotlinx.coroutines.test)
    api(libs.androidx.test.core.ktx)
}
