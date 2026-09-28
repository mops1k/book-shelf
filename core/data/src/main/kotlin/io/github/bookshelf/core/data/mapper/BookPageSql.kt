package io.github.bookshelf.core.data.mapper

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import io.github.bookshelf.core.data.model.Grouping
import io.github.bookshelf.core.data.model.LibraryQuery
import io.github.bookshelf.core.data.model.SortOrder
import io.github.bookshelf.core.data.search.ftsQueryOf

/**
 * Сборка SQL для страницы полки (docs/architecture.md §3.5).
 *
 * Живёт в `:core:data`, а не в `:core:database`, потому что знает доменные модели — а правило
 * проекта запрещает `:core:data` отдавать наружу DAO и сущности, но не запрещает `:core:database`
 * ими пользоваться. В другую сторону зависимости быть не может: `:core:database` не знает
 * про `LibraryQuery`.
 *
 * Три правила, которые здесь важнее красоты кода:
 *
 * 1. **Все значения — только параметры.** Текст запроса собирается из констант и значений enum'ов,
 *    сопоставленных с колонками. Ни одно значение не подставляется в строку: иначе строка поиска
 *    вида `"1; DROP TABLE books—"` испортила бы базу.
 * 2. **Пустое множество фильтра — это «фильтр не задан», а не «ничего не найдено».** Поэтому
 *    условие добавляется только для непустых множеств: пустой `IN ()` ещё и синтаксически
 *    недопустим в SQLite.
 * 3. **Сортировка всегда заканчивается `books.id`.** Без этого пагинация через `LIMIT/OFFSET`
 *    нестабильна: строки с одинаковым заголовком или годом могут возвращаться то на одной
 *    странице, то на другой, и пользователь увидит дубли и пропуски.
 */
internal object BookPageSql {

    /** Выборка страницы. */
    fun select(query: LibraryQuery): SupportSQLiteQuery {
        val args = mutableListOf<Any?>()
        val where = buildWhere(query.bookQuery, args)
        val join = if (query.sort == SortOrder.PROGRESS) PROGRESS_JOIN else ""
        val offset = query.page * query.pageSize
        val bound = args + listOf(query.pageSize, offset)
        return SimpleSQLiteQuery(
            "SELECT books.* FROM books $join $where ${orderBy(query)} LIMIT ? OFFSET ?",
            bound.toTypedArray(),
        )
    }

    /** Счётчик по тем же условиям — без `ORDER BY`, `LIMIT` и без лишнего JOIN. */
    fun count(query: LibraryQuery): SupportSQLiteQuery {
        val args = mutableListOf<Any?>()
        val where = buildWhere(query.bookQuery, args)
        return SimpleSQLiteQuery("SELECT COUNT(*) FROM books $where", args.toTypedArray())
    }

    /**
     * Условие `WHERE` и список параметров.
     *
     * Порядок условий фиксирован и совпадает с порядком добавления параметров: иначе placeholders
     * разъедутся со значениями. Параметры добавляются строка за строкой, в момент присоединения
     * каждого условия.
     */
    private fun buildWhere(
        bookQuery: io.github.bookshelf.core.data.model.BookQuery,
        args: MutableList<Any?>,
    ): String {
        val conditions = mutableListOf<String>()

        bookQuery.sourceIds.takeIf { it.isNotEmpty() }?.let {
            conditions += "books.sourceId IN (${placeholders(it.size)})"
            args.addAll(it)
        }
        // Автор — денормализованное поле books.authorsText: нормализованные authors/book_authors
        // приложение не заполняет, и отбор подзапросом по ним не находил ни одной книги.
        bookQuery.authorNames.takeIf { it.isNotEmpty() }?.let {
            conditions += "books.authorsText IN (${placeholders(it.size)})"
            args.addAll(it)
        }
        bookQuery.series.takeIf { it.isNotEmpty() }?.let {
            conditions += "books.seriesName IN (${placeholders(it.size)})"
            args.addAll(it)
        }
        // Категория «Вне серий» в обзоре по авторам: пустая строка серии равна её отсутствию —
        // парсеры кладут в seriesName либо имя, либо NULL, но данные приходят из разных источников.
        if (bookQuery.onlyWithoutSeries) {
            conditions += "(books.seriesName IS NULL OR books.seriesName = '')"
        }
        bookQuery.formats.takeIf { it.isNotEmpty() }?.let {
            conditions += "books.extension IN (${placeholders(it.size)})"
            args.addAll(it)
        }
        bookQuery.statuses.takeIf { it.isNotEmpty() }?.let { statuses ->
            conditions += "books.readingStatus IN (${placeholders(statuses.size)})"
            args.addAll(statuses.map { it.name })
        }
        bookQuery.tags.takeIf { it.isNotEmpty() }?.let {
            conditions += "books.id IN (SELECT bookId FROM book_tags WHERE tagId IN (${placeholders(it.size)}))"
            args.addAll(it)
        }
        if (bookQuery.favoritesOnly) {
            conditions += "books.favorite = 1"
        }
        ftsQueryOf(bookQuery.searchTerm)?.let { term ->
            // books_fts — внешнее содержимое books, связь по rowid.
            conditions += "books.rowid IN (SELECT rowid FROM books_fts WHERE books_fts MATCH ?)"
            args.add(term)
        }

        return if (conditions.isEmpty()) "" else "WHERE " + conditions.joinToString(" AND ")
    }

    /**
     * `ORDER BY`: сначала ключ группировки, потом выбранная сортировка, всегда с добивкой по `id`.
     */
    private fun orderBy(query: LibraryQuery): String {
        val direction = if (query.descending) "DESC" else "ASC"
        val groupingColumn = when (query.grouping) {
            Grouping.NONE -> null
            Grouping.SERIES -> "books.seriesName"
            Grouping.AUTHOR -> "books.authorsText"
            Grouping.FOLDER -> "books.relativePath"
            Grouping.FORMAT -> "books.extension"
        }
        val parts = buildList {
            // NULL в начале строки сортировки выглядит как «книги без серии» в самом верху;
            // для группировки это раздражает, поэтому пустое значение уходит в конец.
            groupingColumn?.let { add("$it COLLATE NOCASE $direction") }
            add(sortColumn(query.sort, direction))
            add("books.id ASC")
        }
        return "ORDER BY " + parts.joinToString(", ")
    }

    private fun sortColumn(sort: SortOrder, direction: String): String = when (sort) {
        SortOrder.TITLE -> "books.sortTitle COLLATE NOCASE $direction"
        SortOrder.AUTHOR -> "books.authorsText COLLATE NOCASE $direction"
        SortOrder.SERIES -> "books.seriesName COLLATE NOCASE $direction"
        SortOrder.ADDED_AT -> "books.addedAt $direction"
        SortOrder.LAST_OPENED -> "books.lastOpenedAt $direction"
        SortOrder.SIZE -> "books.sizeBytes $direction"
        // Прогресс лежит в отдельной таблице, поэтому нужен JOIN; COALESCOLE даёт 0 книгам без прогресса.
        SortOrder.PROGRESS -> "COALESCE(progress.percent, 0) $direction"
        SortOrder.YEAR -> "books.publishedYear $direction"
    }

    private fun placeholders(count: Int): String = List(count) { "?" }.joinToString(", ")

    private const val PROGRESS_JOIN = "LEFT JOIN reading_progress AS progress ON progress.bookId = books.id"
}
