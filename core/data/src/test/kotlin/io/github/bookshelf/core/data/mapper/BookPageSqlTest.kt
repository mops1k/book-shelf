package io.github.bookshelf.core.data.mapper

import androidx.sqlite.db.SupportSQLiteProgram
import androidx.sqlite.db.SupportSQLiteQuery
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.data.model.BookQuery
import io.github.bookshelf.core.data.model.LibraryQuery
import org.junit.Test

/**
 * Предикат отбора книг.
 *
 * Отдельная проверка появилась после дефекта, найденного на устройстве 28.09.2026: фильтр по
 * жанру не находил ни одной книги, хотя фасет показывал 43. Причина — `book_tags.tagId` хранит
 * `tags.id` (`tag:sf_space`), а в колонку подставлялся код жанра (`sf_space`).
 */
class BookPageSqlTest {

    @Test
    fun `tag filter matches by tag name, not by the id column`() {
        val query = BookPageSql.count(LibraryQuery(bookQuery = BookQuery(tags = setOf("sf_space"))))

        // Код жанра остаётся значением фильтра (и записью в правилах полки), а связь с книгой
        // идёт через tags.name: в `book_tags.tagId` лежит `tag:sf_space`.
        assertThat(query.sql).contains("INNER JOIN tags ON tags.id = book_tags.tagId")
        assertThat(query.sql).contains("tags.name IN")
        assertThat(query.sql).doesNotContain("WHERE tagId IN")
        assertThat(query.bindArgs()).containsExactly("sf_space")
    }

    @Test
    fun `several genres are passed as parameters`() {
        val query = BookPageSql.select(
            LibraryQuery(bookQuery = BookQuery(tags = setOf("sf_space", "detective"))),
        )

        // Набор неупорядочен, поэтому проверяется состав, а не порядок: важно, что коды дошли
        // до запроса, а не префикс из базы. Последние два аргумента — `pageSize` и `offset`
        // страницы, к фильтру они не относятся.
        assertThat(query.bindArgs().take(2)).containsExactly("sf_space", "detective")
    }

    @Test
    fun `filter without tags does not touch the tag tables`() {
        val query = BookPageSql.count(LibraryQuery(bookQuery = BookQuery(authorNames = setOf("Лев Толстой"))))

        assertThat(query.sql).doesNotContain("book_tags")
    }

    /** Аргументы запроса: `SimpleSQLiteQuery` их не отдаёт, зато отдаёт через [bindTo]. */
    private fun SupportSQLiteQuery.bindArgs(): List<Any?> {
        val program = RecordingProgram()
        bindTo(program)
        return program.args
    }

    private class RecordingProgram : SupportSQLiteProgram {
        val args = mutableListOf<Any?>()

        override fun bindNull(index: Int) {
            args += null
        }

        override fun bindLong(index: Int, value: Long) {
            args += value
        }

        override fun bindDouble(index: Int, value: Double) {
            args += value
        }

        override fun bindString(index: Int, value: String) {
            args += value
        }

        override fun bindBlob(index: Int, value: ByteArray) {
            args += value
        }

        override fun clearBindings() {
            args.clear()
        }

        override fun close() = Unit
    }
}
