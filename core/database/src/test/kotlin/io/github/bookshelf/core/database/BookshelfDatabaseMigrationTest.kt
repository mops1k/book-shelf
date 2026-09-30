package io.github.bookshelf.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.database.migration.MIGRATION_1_2
import io.github.bookshelf.core.database.migration.MIGRATION_2_3
import io.github.bookshelf.core.database.migration.MIGRATION_3_4
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
        const val TEST_DB = "migration-test.db"
        const val BOOK_ID = "book-1"
        const val DOWNLOAD_ID = "download-1"

        /**
         * Вставка только обязательных полей: схема требует `metadataSource`, `parseState`,
         * `readingStatus`, `openCount`, `favorite` и `isMissing` — у них нет DEFAULT в SQL.
         * Плейсхолдеры идут в порядке колонок: id, title, sortTitle, authorsText.
         */
        const val INSERT_BOOK_SQL =
            "INSERT INTO books (id, sourceId, documentUri, relativePath, fileName, extension, " +
                "mimeType, sizeBytes, lastModified, title, sortTitle, authorsText, metadataSource, " +
                "parseState, readingStatus, openCount, favorite, isMissing, addedAt, updatedAt, " +
                "lastSeenAt) VALUES (?, 's1', 'content://x', 'a/b.fb2', 'b.fb2', 'fb2', " +
                "'application/fb2', 10, 1, ?, ?, ?, 'NONE', 'PENDING', 'UNREAD', 0, 0, 0, 1, 1, 1)"
    }
}
