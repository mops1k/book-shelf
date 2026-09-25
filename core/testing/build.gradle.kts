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
    api(project(":core:common"))
    api(libs.junit)
    api(libs.truth)
    api(libs.turbine)
    api(libs.mockk)
    api(libs.robolectric)
    api(libs.kotlinx.coroutines.test)
    api(libs.androidx.test.core.ktx)
}
