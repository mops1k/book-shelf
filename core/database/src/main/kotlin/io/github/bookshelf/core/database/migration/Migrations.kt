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

/**
 * 3 → 4: отметка «оптимизацию этой книги проверяли».
 *
 * Аддитивная: добавляется один необязательный столбец. Он нужен пакетной оптимизации FB2, чтобы
 * повторный запуск не перечитывал и не пересобирал книги, которые уже проверены: на 78 книгах это
 * около часа работы впустую (замечание Александра 30.09.2026). Старые строки получают `NULL` —
 * «ещё не проверяли», и первый прогон после обновления пройдёт по всем книгам один раз.
 */
val MIGRATION_3_4: Migration = object : Migration(3, 4) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `books` ADD COLUMN `optimizedAt` INTEGER")
    }
}

/**
 * Таблица связей «файл-копия → книга».
 *
 * Аддитивная: существующие таблицы не меняются, поэтому у пользователя с уже установленной версией
 * библиотека, прогресс и полки остаются как были. Нужна потому, что читалка кладёт байт-в-байт
 * копию книги внутрь нашей библиотеки, и без этой связи копия заводилась второй книгой в полке
 * (92 вместо 91, дефект 01.10.2026).
 */
val MIGRATION_4_5: Migration = object : Migration(4, 5) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `book_copies` (" +
                "`copyDocumentUri` TEXT NOT NULL, " +
                "`bookId` TEXT NOT NULL, " +
                "`sourceId` TEXT NOT NULL, " +
                "`sizeBytes` INTEGER NOT NULL, " +
                "`lastModified` INTEGER NOT NULL, " +
                "`contentHash` TEXT, " +
                "PRIMARY KEY(`copyDocumentUri`), " +
                "FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_book_copies_bookId` ON `book_copies` (`bookId`)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_book_copies_sourceId` ON `book_copies` (`sourceId`)",
        )
    }
}

/**
 * Схема 5 → 6: причина отказа разбора становится кодом.
 *
 * Пользователь видел русский текст парсера прямо в карточке. Теперь для человека есть код
 * (`parseFailure`), а текст остаётся диагностикой в `parseError` (в интерфейс не идёт).
 * Известные причины переносятся: «Формат не поддерживается» — FORMAT_UNSUPPORTED, всё остальное,
 * что было записано у неразобранных книг, — CORRUPT_FILE. Так ни одна книга не остаётся без
 * подписи, а данные пользователя не теряются.
 */
val MIGRATION_5_6: Migration = object : Migration(5, 6) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `books` ADD COLUMN `parseFailure` TEXT")
        db.execSQL(
            "UPDATE `books` SET `parseFailure` = 'FORMAT_UNSUPPORTED' " +
                "WHERE `parseError` = 'Формат не поддерживается'",
        )
        db.execSQL(
            "UPDATE `books` SET `parseFailure` = 'CORRUPT_FILE' " +
                "WHERE `parseFailure` IS NULL AND `parseError` IS NOT NULL",
        )
    }
}

/**
 * Схема 6 → 7: причина отказа загрузки становится кодом.
 *
 * Раньше отказ загрузки хранился готовым текстом (в том числе русской подсказкой про папку) и
 * показывался в очереди — в английском интерфейсе по-русски. Теперь для человека есть код
 * (`failure`), а текст остаётся диагностикой в `error`. Известные причины переносятся по тексту,
 * всё остальное становится общим DOWNLOAD_FAILED: строка без подписи хуже общей, но понятной.
 */
val MIGRATION_6_7: Migration = object : Migration(6, 7) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `opds_downloads` ADD COLUMN `failure` TEXT")
        db.execSQL(
            "UPDATE `opds_downloads` SET `failure` = 'NO_WRITE_PERMISSION' " +
                "WHERE `error` LIKE 'Приложению не разрешено писать в папку%'",
        )
        db.execSQL(
            "UPDATE `opds_downloads` SET `failure` = 'RECEIVER_FOLDER_GONE' " +
                "WHERE `error` = 'Папка-получатель больше не существует'",
        )
        db.execSQL(
            "UPDATE `opds_downloads` SET `failure` = 'CREDENTIALS_UNREADABLE' " +
                "WHERE `error` = 'Не удалось прочитать пароль каталога'",
        )
        db.execSQL(
            "UPDATE `opds_downloads` SET `failure` = 'BAD_DOCUMENT_URI' " +
                "WHERE `error` = 'Провайдер вернул адрес, из которого не собрать идентификатор'",
        )
        // Всё, что не опознано, получает общий код — но только у неудачных загрузок: у остальных
        // столбец `error` пуст, и кода быть не должно.
        db.execSQL(
            "UPDATE `opds_downloads` SET `failure` = 'DOWNLOAD_FAILED' " +
                "WHERE `failure` IS NULL AND `state` = 'FAILED'",
        )
    }
}

/**
 * Схема 8: имена служебных полок больше не хранятся в базе.
 *
 * Раньше имя писали мы сами и по-русски: «Новая полка» (с номером), «Подборка: жанр: …» и четыре
 * имени пресетов. В английском интерфейсе эти строки оставались русскими, а в данных лежал текст,
 * который нельзя перевести (docs/tdd.md §8). Теперь у служебной полки имя пустое, а слова собирает
 * экран: «Новая полка»/«Новая полка 2» по порядку безымянных полок и подпись правила из его частей.
 *
 * Список шаблонов намеренно закрытый и точный: «похоже на наше» затронуло бы полку, которую человек
 * назвал сам, — а это его данные. Имя не удаляется физически: обнуляется только совпадение, и если
 * человек когда-то переименовал служебную полку в «Избранное», он увидит подпись правила — меньший
 * вред, чем переименование чужой полки.
 */
val MIGRATION_7_8: Migration = object : Migration(7, 8) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "UPDATE `collections` SET `name` = '' WHERE `name` = 'Новая полка' " +
                "OR `name` LIKE 'Новая полка %'",
        )
        db.execSQL("UPDATE `collections` SET `name` = '' WHERE `name` LIKE 'Подборка: %'")
        db.execSQL(
            "UPDATE `collections` SET `name` = '' WHERE `name` IN " +
                "('Избранное', 'Непрочитанные', 'Читаю сейчас', 'Прочитанные')",
        )
    }
}

val ALL_MIGRATIONS: Array<Migration> =
    arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8,
    )
