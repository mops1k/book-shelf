plugins {
    id("bookshelf.android.library")
    id("bookshelf.android.room")
    // Hilt нужен: модуль предоставляет DAO через @Provides (di/DatabaseModule.kt).
    id("bookshelf.android.hilt")
}

android {
    namespace = "io.github.bookshelf.core.database"
}

// Схема, DAO и миграции. Модуль не знает ни про парсеры, ни про SAF:
// связывает их репозиторий в :core:data («Архитектура» §2).
dependencies {
    api(project(":core:common"))
    api(libs.androidx.room.runtime)
    // `withTransaction` (корректная по отношению к отмене корутины транзакция) живёт в `room-ktx`;
    // версия та же, что у `room-runtime`, отдельного поставщика не появляется.
    api(libs.androidx.room.ktx)
}
