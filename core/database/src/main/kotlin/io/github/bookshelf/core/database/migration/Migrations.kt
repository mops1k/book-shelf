package io.github.bookshelf.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
 */
/**
 * 1 → 2: ручные правки метаданных и рабочий полнотекстовый индекс.
 *
 * Миграция делает две вещи, обе аддитивные — ни одна существующая таблица не меняется.
 *
 * **1. Таблица `metadata_overrides`.** Ручные правки хранятся отдельно от `books`, чтобы
 * пересканирование не затирало их (docs/architecture.md §3.1). `ON DELETE CASCADE` удаляет
 * переопределение вместе с книгой: иначе после удаления и восстановления того же `documentUri`
 * пользователь получил бы правки к прежнему содержимому.
 *
 * **2. Триггеры синхронизации FTS.** Это исправление, а не новая возможность. Таблица
 * `books_fts` объявлена внешним содержимым (`content=books`), и Room для таких таблиц
 * **не создаёт триггеры сам** — синхронизацию обязан обеспечить владелец схемы. Без триггеров
 * `SELECT count(*)` внешне выглядит рабочим (FTS4 читает `books`), но `MATCH` не находит
 * ничего: индекс пуст. Это проверено на схеме 1 — книга, вставленная до появления триггеров,
 * не находилась запросом. Отсюда и необходимость триггеров в миграции, а не только в коде.
 *
 * `books_fts_delete` объявлен `BEFORE DELETE`: у внешнего содержимого `DELETE` из FTS работает
 * только до удаления строки из `books`, иначе FTS4 не найдёт, что удалять.
 *
 * Пересборка индекса нужна для книг, добавленных в версии 1: они уже лежат в `books`, но в
 * индексе их нет. Команда `INSERT INTO books_fts(books_fts) VALUES('rebuild')` — штатный способ
 * перестроить внешний индекс FTS4 целиком.
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `metadata_overrides` (
                `bookId` TEXT NOT NULL,
                `title` TEXT,
                `subtitle` TEXT,
                `authorsText` TEXT,
                `seriesName` TEXT,
                `seriesIndex` REAL,
                `language` TEXT,
                `publishedYear` INTEGER,
                `publisher` TEXT,
                `description` TEXT,
                `isbn` TEXT,
                `pageCount` INTEGER,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`bookId`),
                FOREIGN KEY(`bookId`) REFERENCES `books`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_metadata_overrides_bookId` ON `metadata_overrides` (`bookId`)")

        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS `books_fts_insert` AFTER INSERT ON `books` BEGIN
                INSERT INTO `books_fts`(`rowid`, `title`, `authorsText`, `seriesName`, `fileName`, `description`)
                VALUES (new.`rowid`, new.`title`, new.`authorsText`, new.`seriesName`,
                    new.`fileName`, new.`description`);
            END
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS `books_fts_delete` BEFORE DELETE ON `books` BEGIN
                DELETE FROM `books_fts` WHERE `rowid` = old.`rowid`;
            END
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS `books_fts_update` AFTER UPDATE ON `books` BEGIN
                DELETE FROM `books_fts` WHERE `rowid` = old.`rowid`;
                INSERT INTO `books_fts`(`rowid`, `title`, `authorsText`, `seriesName`, `fileName`, `description`)
                VALUES (new.`rowid`, new.`title`, new.`authorsText`, new.`seriesName`,
                    new.`fileName`, new.`description`);
            END
            """.trimIndent(),
        )

        // Книги, накопленные в версии 1, в индексе отсутствуют — строим его заново.
        db.execSQL("INSERT INTO `books_fts`(`books_fts`) VALUES('rebuild')")
    }
}

/**
 * 2 → 3: серия в строке загрузки OPDS.
 *
 * Аддитивная: добавляется один необязательный столбец, существующие строки и таблицы не меняются.
 * Серия нужна шаблону пути загрузки (`opds.downloadFileNameTemplate`): вариант
 * «{author}/{series}/{title}.{ext}» раскладывает скачанное по папкам, а фид к моменту работы
 * воркера недоступен — серию приходится хранить вместе с загрузкой. Старые загрузки получают
 * `NULL`, и заполнитель просто отбрасывает пустую папку.
 */
val MIGRATION_2_3: Migration = object : Migration(2, 3) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `opds_downloads` ADD COLUMN `seriesName` TEXT")
    }
}

val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
