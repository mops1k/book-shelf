package io.github.bookshelf.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.database.migration.MIGRATION_1_2
import io.github.bookshelf.core.database.migration.MIGRATION_2_3
import io.github.bookshelf.core.database.migration.MIGRATION_3_4
import io.github.bookshelf.core.database.migration.MIGRATION_4_5
import io.github.bookshelf.core.database.migration.MIGRATION_5_6
import io.github.bookshelf.core.database.migration.MIGRATION_6_7
import io.github.bookshelf.core.database.migration.MIGRATION_7_8
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Миграции схемы БД (docs/architecture.md §3.4, docs/tdd.md §4).
 *
 * Проверяется ровно то, что обязано выполняться и чего не видно в обычных тестах: у пользователя с
 * уже установленной версией миграция обязана сохранить библиотеку. Поэтому тест создаёт базу
 * **версии 1**, кладёт в неё книгу, прогоняет миграцию и сверяет результат не только глазами, но и
 * через `runMigrationsAndValidate` — Room сама сравнит схему с экспортированным `schemas/2.json`
 * и провалит тест при любом расхождении, включая забытый индекс.
 *
 * Про внешние ключи: `MigrationTestHelper` отдаёт «сырую» `SupportSQLiteDatabase`, где
 * `PRAGMA foreign_keys` выключен, поэтому `ON DELETE CASCADE` здесь не срабатывает. Поведение
 * каскада в боевом пути проверяется отдельно в `MetadataOverrideCascadeTest` на настоящей базе Room.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class BookshelfDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        BookshelfDatabase::class.java,
    )

    @Test
    fun migrate_1_to_2_preserves_books_written_in_version_1() {
        givenVersion1WithBook()

        helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2).use { db ->
            assertThat(db.stringOf("SELECT title FROM books WHERE id = ?", BOOK_ID))
                .isEqualTo("Война и мир")
        }
    }

    @Test
    fun migrate_1_to_2_creates_usable_metadata_overrides_table() {
        givenVersion1WithBook()

        helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2).use { db ->
            db.execSQL(
                "INSERT OR REPLACE INTO metadata_overrides (bookId, title, updatedAt) VALUES (?, ?, ?)",
                arrayOf<Any>(BOOK_ID, "Моя редакция", 1000L),
            )

            assertThat(db.stringOf("SELECT title FROM metadata_overrides WHERE bookId = ?", BOOK_ID))
                .isEqualTo("Моя редакция")
        }
    }

    @Test
    fun migrate_1_to_2_books_added_before_migration_become_searchable() {
        givenVersion1WithBook()

        helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2).use { db ->
            // Книга лежит в books ещё с версии 1, а индекса FTS тогда не существовало: внешнее
            // содержимое читается из books, но сам индекс пуст. Без пересборки MATCH её не найдёт.
            assertThat(db.ftsHits("толстой")).isEqualTo(1)
        }
    }

    @Test
    fun migrate_1_to_2_books_added_after_migration_are_indexed_by_triggers() {
        givenVersion1WithoutBooks()

        helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2).use { db ->
            db.execSQL(INSERT_BOOK_SQL, bookArgs(BOOK_ID, "Анна Каренина"))

            assertThat(db.ftsHits("каренина")).isEqualTo(1)
        }
    }

    @Test
    fun migrate_1_to_2_editing_a_book_keeps_the_index_in_sync() {
        givenVersion1WithoutBooks()

        helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2).use { db ->
            db.execSQL(INSERT_BOOK_SQL, bookArgs(BOOK_ID, "Анна Каренина"))
            assertThat(db.ftsHits("каренина")).isEqualTo(1)

            db.execSQL(
                "UPDATE books SET title = ?, sortTitle = ? WHERE id = ?",
                arrayOf<Any>("Отцы и дети", "Отцы и дети", BOOK_ID),
            )

            assertThat(db.ftsHits("каренина")).isEqualTo(0)
            assertThat(db.ftsHits("отцы")).isEqualTo(1)
        }
    }

    @Test
    fun migrate_1_to_2_deleting_a_book_removes_it_from_the_index() {
        givenVersion1WithoutBooks()

        helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2).use { db ->
            db.execSQL(INSERT_BOOK_SQL, bookArgs(BOOK_ID, "Анна Каренина"))
            assertThat(db.ftsHits("каренина")).isEqualTo(1)

            db.execSQL("DELETE FROM books WHERE id = ?", arrayOf<Any>(BOOK_ID))

            assertThat(db.ftsHits("каренина")).isEqualTo(0)
        }
    }

    @Test
    fun migrate_2_to_3_adds_an_empty_series_to_existing_downloads() {
        givenVersion2WithDownload()

        helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3).use { db ->
            // Строка загрузки, созданная до миграции, обязана остаться: у пользователя в очереди
            // может лежать начатая загрузка.
            assertThat(db.stringOf("SELECT title FROM opds_downloads WHERE id = ?", DOWNLOAD_ID))
                .isEqualTo("Солярис")
            // Серии у старой загрузки нет — NULL, а не пустая строка: заполнитель `{series}`
            // отбрасывает пустую часть, а пустая строка создала бы папку без имени.
            assertThat(db.nullableStringOf("SELECT seriesName FROM opds_downloads WHERE id = ?", DOWNLOAD_ID))
                .isNull()

            // Столбец не только существует, но и записывается — иначе шаблон с серией молча
            // раскладывал бы всё в корень.
            db.execSQL(
                "UPDATE opds_downloads SET seriesName = ? WHERE id = ?",
                arrayOf<Any>("Пространство Откровения", DOWNLOAD_ID),
            )
            assertThat(db.stringOf("SELECT seriesName FROM opds_downloads WHERE id = ?", DOWNLOAD_ID))
                .isEqualTo("Пространство Откровения")
        }
    }

    @Test
    fun migrate_3_to_4_marks_existing_books_as_not_yet_optimized() {
        givenVersion3WithBook()

        helper.runMigrationsAndValidate(TEST_DB, 4, true, MIGRATION_3_4).use { db ->
            // Старые книги получают «ещё не проверяли»: первый прогон оптимизации после обновления
            // пройдёт по всем книгам один раз, и это правильное поведение.
            assertThat(db.nullableLongOf("SELECT optimizedAt FROM books WHERE id = ?", BOOK_ID)).isNull()
            assertThat(db.stringOf("SELECT title FROM books WHERE id = ?", BOOK_ID))
                .isEqualTo("Война и мир")
        }
    }

    @Test
    fun migrate_3_to_4_lets_the_mark_be_written_and_read() {
        givenVersion3WithBook()

        helper.runMigrationsAndValidate(TEST_DB, 4, true, MIGRATION_3_4).use { db ->
            // Столбец не только существует, но и записывается: иначе пакетная оптимизация
            // перечитывала бы уже проверенные книги на каждом запуске.
            db.execSQL("UPDATE books SET optimizedAt = ? WHERE id = ?", arrayOf<Any>(1234L, BOOK_ID))
            assertThat(db.nullableLongOf("SELECT optimizedAt FROM books WHERE id = ?", BOOK_ID))
                .isEqualTo(1234L)
        }
    }

    @Test
    fun migrate_6_to_7_moves_download_failures_into_codes() {
        givenVersion6WithFailedDownload()

        helper.runMigrationsAndValidate(TEST_DB, 7, true, MIGRATION_6_7).use { db ->
            // Отказ в записи переносится своим кодом: именно он даёт человеку подсказку про папку.
            assertThat(db.stringOf("SELECT failure FROM opds_downloads WHERE id = ?", DOWNLOAD_ID))
                .isEqualTo("NO_WRITE_PERMISSION")
            // Прочие тексты становятся общим кодом, а сам текст остаётся диагностикой.
            assertThat(db.stringOf("SELECT failure FROM opds_downloads WHERE id = ?", SECOND_DOWNLOAD_ID))
                .isEqualTo("DOWNLOAD_FAILED")
            assertThat(db.stringOf("SELECT error FROM opds_downloads WHERE id = ?", SECOND_DOWNLOAD_ID))
                .isEqualTo("Сервер ответил кодом 404")
            // Неудачная загрузка без записи причины тоже получает код, а не пустоту.
            assertThat(db.stringOf("SELECT failure FROM opds_downloads WHERE id = ?", THIRD_DOWNLOAD_ID))
                .isEqualTo("DOWNLOAD_FAILED")
        }
    }

    @Test
    fun migrate_7_to_8_clears_the_names_our_own_code_wrote() {
        givenVersion7WithServiceCollections()

        helper.runMigrationsAndValidate(TEST_DB, 8, true, MIGRATION_7_8).use { db ->
            // Имена служебных полок писали мы сами по-русски: раньше «Новая полка», «Подборка: …»,
            // «Избранное». Теперь в базе для них пусто, а слова собирает экран («Новая полка»,
            // «Новая полка 2», подпись правила) — docs/tdd.md §8.
            assertThat(db.stringOf("SELECT name FROM collections WHERE id = ?", NEW_SHELF_ID)).isEmpty()
            assertThat(db.stringOf("SELECT name FROM collections WHERE id = ?", SECOND_SHELF_ID)).isEmpty()
            assertThat(db.stringOf("SELECT name FROM collections WHERE id = ?", FILTER_SHELF_ID)).isEmpty()
            assertThat(db.stringOf("SELECT name FROM collections WHERE id = ?", FAVORITES_SHELF_ID)).isEmpty()
            assertThat(db.stringOf("SELECT name FROM collections WHERE id = ?", READING_SHELF_ID)).isEmpty()

            // Имя, которое написал человек, остаётся его данными: миграция его не трогает.
            assertThat(db.stringOf("SELECT name FROM collections WHERE id = ?", CUSTOM_SHELF_ID))
                .isEqualTo("Фантастика 2020")

            // Правила полки не потерялись: по ним экран и собирает подпись.
            assertThat(
                db.nullableStringOf("SELECT rulesJson FROM collections WHERE id = ?", FILTER_SHELF_ID),
            ).isNotNull()
        }
    }

    @Test
    fun migrate_5_to_6_moves_the_known_reason_into_a_code() {
        givenVersion5WithFailedBook()

        helper.runMigrationsAndValidate(TEST_DB, 6, true, MIGRATION_5_6).use { db ->
            // Пользователь видел русскую причину из парсера: она переносится в код, а сам текст
            // остаётся диагностикой (в интерфейс такие тексты больше не идут).
            assertThat(db.stringOf("SELECT parseFailure FROM books WHERE id = ?", BOOK_ID))
                .isEqualTo("FORMAT_UNSUPPORTED")
            assertThat(db.stringOf("SELECT parseError FROM books WHERE id = ?", BOOK_ID))
                .isEqualTo("Формат не поддерживается")

            // Прочие причины переносятся в «файл повреждён»: ни одна книга не остаётся без подписи.
            assertThat(db.stringOf("SELECT parseFailure FROM books WHERE id = ?", SECOND_BOOK_ID))
                .isEqualTo("CORRUPT_FILE")
            // Книга, разобравшаяся успешно, кода не получает.
            assertThat(
                db.nullableStringOf("SELECT parseFailure FROM books WHERE id = ?", OK_BOOK_ID),
            ).isNull()
        }
    }

    @Test
    fun migrate_4_to_5_creates_book_copies_table_that_can_be_written() {
        givenVersion4WithBook()

        helper.runMigrationsAndValidate(TEST_DB, 5, true, MIGRATION_4_5).use { db ->
            // Книга пользователя осталась на месте: миграция только добавляет таблицу.
            assertThat(db.stringOf("SELECT title FROM books WHERE id = ?", BOOK_ID))
                .isEqualTo("Война и мир")
            // Таблица копий существует и записывается: без неё копия, созданная читалкой, снова
            // стала бы второй книгой в полке (92 вместо 91, дефект 01.10.2026).
            db.execSQL(
                INSERT_COPY_SQL,
                arrayOf<Any>(COPY_URI, BOOK_ID, COPY_SOURCE_ID, COPY_SIZE, 1000L, "hash-1"),
            )
            assertThat(db.stringOf("SELECT bookId FROM book_copies WHERE copyDocumentUri = ?", COPY_URI))
                .isEqualTo(BOOK_ID)
        }
    }

    @Test
    fun migrate_4_to_5_removes_the_copy_mapping_with_its_book() {
        givenVersion4WithBook()

        helper.runMigrationsAndValidate(TEST_DB, 5, true, MIGRATION_4_5).use { db ->
            // Внешний ключ объявлен с ON DELETE CASCADE: связь с удалённой книгой смысла не имеет.
            // Сам каскад здесь не срабатывает (в `MigrationTestHelper` выключен `PRAGMA
            // foreign_keys`) — поведение каскада проверяется на настоящей базе Room в
            // `BookCopyCascadeTest`; здесь проверяется, что внешний ключ объявлен в схеме.
            val foreignKeys = db.query("PRAGMA foreign_key_list(book_copies)").use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        // Колонки прагмы: 2 — таблица, 3 — столбец ребёнка, 4 — столбец родителя,
                        // 6 — действие при удалении.
                        add(Triple(cursor.getString(2), cursor.getString(3), cursor.getString(6)))
                    }
                }
            }

            assertThat(foreignKeys).containsExactly(Triple("books", "bookId", "CASCADE"))
        }
    }

    /** Книга, записанная в схеме 4 — до появления таблицы копий. */
    /** Книга, у которой разбор не удался и причина записана текстом — так было до схемы 6. */
    /** Неудачные загрузки, записанные в схеме 6 — когда отказ хранился текстом. */
    private fun givenVersion6WithFailedDownload() {
        helper.createDatabase(TEST_DB, 6).apply {
            execSQL(DOWNLOAD_SQL, downloadArgs(DOWNLOAD_ID, "Приложению не разрешено писать в папку"))
            execSQL(DOWNLOAD_SQL, downloadArgs(SECOND_DOWNLOAD_ID, "Сервер ответил кодом 404"))
            execSQL(DOWNLOAD_SQL, downloadArgs(THIRD_DOWNLOAD_ID, null))
            close()
        }
    }

    /** Полки схемы 7: служебные (их имена писали мы) и одна с именем, которое дал человек. */
    private fun givenVersion7WithServiceCollections() {
        helper.createDatabase(TEST_DB, 7).apply {
            for ((id, name) in SERVICE_COLLECTION_NAMES) {
                execSQL(INSERT_COLLECTION_SQL, collectionArgs(id, name))
            }
            execSQL(INSERT_COLLECTION_SQL, collectionArgs(CUSTOM_SHELF_ID, "Фантастика 2020"))
            execSQL(
                "UPDATE collections SET rulesJson = ? WHERE id = ?",
                arrayOf<Any>("{}", FILTER_SHELF_ID),
            )
            close()
        }
    }

    /** Вставка полки: обязательные поля заполнены, имя — параметром. */
    private fun collectionArgs(id: String, name: String): Array<Any> =
        arrayOf<Any>(id, name, "SMART", 0, 1000L)

    /** Вставка строки очереди: обязательные поля есть, причина и состояние — параметрами. */
    private fun downloadArgs(id: String, error: String?): Array<Any?> =
        arrayOf<Any?>(id, "FAILED", error)

    private fun givenVersion5WithFailedBook() {
        helper.createDatabase(TEST_DB, 5).apply {
            execSQL(INSERT_BOOK_SQL, bookArgs(BOOK_ID, "Битый.fb2"))
            // Адрес документа у книг разный: на `documentUri` в схеме стоит UNIQUE.
            execSQL(
                INSERT_BOOK_WITH_URI_SQL,
                arrayOf<Any>(SECOND_BOOK_ID, "content://b2", "Тоже битый.fb2", "Тоже битый.fb2", ""),
            )
            execSQL(
                INSERT_BOOK_WITH_URI_SQL,
                arrayOf<Any>(OK_BOOK_ID, "content://b3", "Целая.fb2", "Целая.fb2", ""),
            )
            execSQL(
                "UPDATE books SET parseState = 'FAILED', parseError = ? WHERE id = ?",
                arrayOf<Any>("Формат не поддерживается", BOOK_ID),
            )
            execSQL(
                "UPDATE books SET parseState = 'FAILED', parseError = ? WHERE id = ?",
                arrayOf<Any>("FB2 внутри архива превышает допустимый размер XML", SECOND_BOOK_ID),
            )
            close()
        }
    }

    private fun givenVersion4WithBook() {
        helper.createDatabase(TEST_DB, 4).apply {
            execSQL(INSERT_BOOK_SQL, bookArgs(BOOK_ID, "Война и мир"))
            close()
        }
    }

    private fun givenVersion1WithBook() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(INSERT_BOOK_SQL, bookArgs(BOOK_ID, "Война и мир"))
            close()
        }
    }

    /** Книга, записанная в схеме 3 — до появления отметки оптимизации. */
    private fun givenVersion3WithBook() {
        helper.createDatabase(TEST_DB, 3).apply {
            execSQL(INSERT_BOOK_SQL, bookArgs(BOOK_ID, "Война и мир"))
            close()
        }
    }

    private fun givenVersion1WithoutBooks() {
        helper.createDatabase(TEST_DB, 1).close()
    }

    private fun SupportSQLiteDatabase.stringOf(sql: String, id: String): String =
        query(sql, arrayOf<Any>(id)).use { cursor ->
            check(cursor.moveToFirst()) { "запрос не вернул строк: $sql" }
            cursor.getString(0)
        }

    private fun SupportSQLiteDatabase.ftsHits(term: String): Int = query(
        "SELECT COUNT(*) FROM books INNER JOIN books_fts ON books.rowid = books_fts.rowid " +
            "WHERE books_fts MATCH ?",
        arrayOf<Any>(term),
    ).use { cursor ->
        cursor.moveToFirst()
        cursor.getInt(0)
    }

    private fun bookArgs(id: String, title: String): Array<Any> =
        arrayOf<Any>(id, title, title, "Лев Толстой")

    /**
     * Строка очереди загрузок версии 2.
     *
     * Вставляются только обязательные поля: у `seriesName` в схеме 2 столбца ещё нет, а у
     * `authorsText`, `mimeType`, `expectedSize`, `error` и `bookId` столбцы допускают NULL.
     */
    private fun givenVersion2WithDownload() {
        helper.createDatabase(TEST_DB, 2).apply {
            execSQL(
                "INSERT INTO opds_downloads (id, catalogId, entryId, title, downloadUrl, " +
                    "targetSourceId, targetRelativeDir, state, bytesDownloaded, createdAt) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any>(
                    DOWNLOAD_ID,
                    "catalog-1",
                    "entry-1",
                    "Солярис",
                    "https://host/1.fb2",
                    "source-1",
                    "",
                    "QUEUED",
                    0L,
                    1000L,
                ),
            )
            close()
        }
    }

    /** Числовое значение столбца, допускающего NULL: `getLong` на NULL вернул бы ноль. */
    private fun SupportSQLiteDatabase.nullableLongOf(sql: String, id: String): Long? =
        query(sql, arrayOf<Any>(id)).use { cursor ->
            check(cursor.moveToFirst()) { "запрос не вернул строк: $sql" }
            if (cursor.isNull(0)) null else cursor.getLong(0)
        }

    /** Значение столбца, допускающего NULL: [stringOf] на NULL бросил бы исключение. */
    private fun SupportSQLiteDatabase.nullableStringOf(sql: String, id: String): String? =
        query(sql, arrayOf<Any>(id)).use { cursor ->
            check(cursor.moveToFirst()) { "запрос не вернул строк: $sql" }
            cursor.getString(0)
        }

    private companion object {
        const val COPY_URI = "content://com.android.externalstorage.documents/document/copy"
        const val COPY_SIZE = 514_015L
        const val COPY_SOURCE_ID = "source-1"
        const val INSERT_COPY_SQL =
            "INSERT INTO book_copies (copyDocumentUri, bookId, sourceId, sizeBytes, lastModified, " +
                "contentHash) VALUES (?, ?, ?, ?, ?, ?)"

        /** Вставка полки: обязательные поля заполнены, имя — параметром. */
        const val INSERT_COLLECTION_SQL =
            "INSERT INTO collections (id, name, type, position, createdAt) VALUES (?, ?, ?, ?, ?)"

        const val NEW_SHELF_ID = "shelf-new"
        const val SECOND_SHELF_ID = "shelf-new-2"
        const val FILTER_SHELF_ID = "shelf-filter"
        const val FAVORITES_SHELF_ID = "shelf-favorites"
        const val READING_SHELF_ID = "shelf-reading"
        const val CUSTOM_SHELF_ID = "shelf-custom"

        /**
         * Имена, которые писали мы сами: ровно они обнуляются миграцией 7→8.
         *
         * Список закрытый и намеренно точный: «похоже на наше» затронуло бы полку, которую человек
         * назвал сам, а это его данные.
         */
        val SERVICE_COLLECTION_NAMES = listOf(
            NEW_SHELF_ID to "Новая полка",
            SECOND_SHELF_ID to "Новая полка 2",
            FILTER_SHELF_ID to "Подборка: жанр: Космическая фантастика",
            FAVORITES_SHELF_ID to "Избранное",
            READING_SHELF_ID to "Читаю сейчас",
        )

        const val TEST_DB = "migration-test.db"
        const val BOOK_ID = "book-1"
        const val SECOND_BOOK_ID = "book-2"
        const val OK_BOOK_ID = "book-3"
        const val DOWNLOAD_ID = "download-1"
        const val SECOND_DOWNLOAD_ID = "download-2"
        const val THIRD_DOWNLOAD_ID = "download-3"
        /** Строка очереди для проверки миграции причины отказа загрузки. */
        const val DOWNLOAD_SQL =
            "INSERT INTO opds_downloads (id, catalogId, entryId, title, downloadUrl, targetSourceId, " +
                "targetRelativeDir, state, bytesDownloaded, error, createdAt) VALUES (?, 'catalog-1', " +
                "'entry-1', 'Солярис', 'https://host/1.fb2', 'source-1', '', ?, 0, ?, 1)"

        /**
         * Вставка только обязательных полей: схема требует `metadataSource`, `parseState`,
         * `readingStatus`, `openCount`, `favorite` и `isMissing` — у них нет DEFAULT в SQL.
         * Плейсхолдеры идут в порядке колонок: id, title, sortTitle, authorsText.
         */
        /** Та же вставка, но адрес документа — параметром: у книг в тестах он разный. */
        const val INSERT_BOOK_WITH_URI_SQL =
            "INSERT INTO books (id, sourceId, documentUri, relativePath, fileName, extension, " +
                "mimeType, sizeBytes, lastModified, title, sortTitle, authorsText, metadataSource, " +
                "parseState, readingStatus, openCount, favorite, isMissing, addedAt, updatedAt, " +
                "lastSeenAt) VALUES (?, 's1', ?, 'a/b.fb2', 'b.fb2', 'fb2', 'application/fb2', " +
                "10, 1, ?, ?, ?, 'NONE', 'PENDING', 'UNREAD', 0, 0, 0, 1, 1, 1)"

        const val INSERT_BOOK_SQL =
            "INSERT INTO books (id, sourceId, documentUri, relativePath, fileName, extension, " +
                "mimeType, sizeBytes, lastModified, title, sortTitle, authorsText, metadataSource, " +
                "parseState, readingStatus, openCount, favorite, isMissing, addedAt, updatedAt, " +
                "lastSeenAt) VALUES (?, 's1', 'content://x', 'a/b.fb2', 'b.fb2', 'fb2', " +
                "'application/fb2', 10, 1, ?, ?, ?, 'NONE', 'PENDING', 'UNREAD', 0, 0, 0, 1, 1, 1)"
    }
}
