package io.github.bookshelf.core.database.migration

import androidx.room.migration.Migration

/**
 * Все миграции схемы БД, применяемые при открытии базы.
 *
 * Правила (docs/architecture.md §3.4):
 *
 * * `exportSchema = true`, JSON-схемы лежат в `core/database/schemas/` и коммитятся в репозиторий;
 * * миграции **только аддитивные**; удаление или переименование столбца — отдельная миграция
 *   с явным `ALTER TABLE`, а не «забыли и пересоздали базу»;
 * * `fallbackToDestructiveMigration()` запрещён в release: потеря пользовательской библиотеки,
 *   истории чтения и коллекций недопустима;
 * * каждая миграция покрывается автотестом `MigrationTestHelper` — это обязательное требование CI;
 * * перед миграцией делается резервная копия БД в `noBackupFilesDir`.
 *
 * На версии 1 массив пуст: это стартовая схема.
 */
// STUB(Sprint 2): добавить MIGRATION_1_2 при первой эволюции схемы (метаданные/обложки).
val ALL_MIGRATIONS: Array<Migration> = emptyArray()
