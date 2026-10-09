// Корневой build-файл: только объявление плагинов, чтобы версии были зафиксированы в одном месте.
// Вся настройка модулей — в convention-плагинах (`build-logic/convention`).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.detekt) apply false
    // Покрытие: плагин применяется общей конвенцией к каждому модулю, здесь — только версия.
    alias(libs.plugins.kover) apply false
}
