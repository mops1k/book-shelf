package io.github.bookshelf.core.data.mapper

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import io.github.bookshelf.core.data.model.BookQuery

/**
 * Запросы фасетов по тому же предикату, что и список книг (docs/architecture.md §3.5, ADR-008).
 *
 * Зачем: фасеты должны зависеть от текущего выбора — выбран автор, и в «Серии» видны только его
 * серии, а в «Жанрах» только его жанры. Раньше фасеты считались по всей библиотеке
 * (`STUB(Sprint 4)`), поэтому фильтры не сужались и экран разрастался.
 *
 * Фасет считается БЕЗ собственного фильтра (авторы — без фильтра по авторам, серии — без фильтра
 * по сериям): иначе, выбрав одного автора, нельзя было бы выбрать второго. Это стандартное
 * поведение фасетного поиска.
 */
internal object FacetSql {

    fun authors(query: BookQuery): SupportSQLiteQuery = facet(
        query = query.copy(authorNames = emptySet()),
        select = "SELECT books.authorsText AS name, COUNT(*) AS bookCount FROM books",
        extra = "books.authorsText <> ''",
        groupBy = "GROUP BY books.authorsText ORDER BY books.authorsText COLLATE NOCASE ASC",
    )

    fun series(query: BookQuery): SupportSQLiteQuery = facet(
        query = query.copy(series = emptySet()),
        select = "SELECT books.seriesName AS seriesName, COUNT(*) AS bookCount FROM books",
        extra = "books.seriesName IS NOT NULL AND books.seriesName <> ''",
        groupBy = "GROUP BY books.seriesName ORDER BY books.seriesName COLLATE NOCASE ASC",
    )

    fun tags(query: BookQuery): SupportSQLiteQuery = facet(
        query = query.copy(tags = emptySet()),
        select = "SELECT tags.name AS name, COUNT(*) AS bookCount FROM books " +
            "INNER JOIN book_tags ON book_tags.bookId = books.id " +
            "INNER JOIN tags ON tags.id = book_tags.tagId",
        extra = null,
        groupBy = "GROUP BY tags.id, tags.name ORDER BY bookCount DESC, tags.name COLLATE NOCASE ASC",
    )

    fun formats(query: BookQuery): SupportSQLiteQuery = facet(
        query = query.copy(formats = emptySet()),
        select = "SELECT books.extension AS extension, COUNT(*) AS bookCount FROM books",
        extra = null,
        groupBy = "GROUP BY books.extension ORDER BY books.extension COLLATE NOCASE ASC",
    )

    fun statuses(query: BookQuery): SupportSQLiteQuery = facet(
        query = query.copy(statuses = emptySet()),
        select = "SELECT books.readingStatus AS readingStatus, COUNT(*) AS bookCount FROM books",
        extra = null,
        groupBy = "GROUP BY books.readingStatus ORDER BY books.readingStatus ASC",
    )

    private fun facet(
        query: BookQuery,
        select: String,
        extra: String?,
        groupBy: String,
    ): SupportSQLiteQuery {
        val args = mutableListOf<Any?>()
        val where = BookPageSql.buildWhere(query, args).removePrefix("WHERE ").trim()
        val conditions = listOfNotNull(where.takeIf { it.isNotEmpty() }, extra)
        val whereClause = if (conditions.isEmpty()) "" else "WHERE " + conditions.joinToString(" AND ")
        return SimpleSQLiteQuery("$select $whereClause $groupBy", args.toTypedArray())
    }
}
