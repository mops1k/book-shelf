package io.github.bookshelf.core.data.mapper

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import io.github.bookshelf.core.data.author.AUTHOR_SET_SEPARATOR
import io.github.bookshelf.core.data.model.BookQuery

/**
 * Запросы фасетов по тому же предикату, что и список книг («Архитектура» §3.5, ADR-008).
 *
 * Зачем: фасеты должны зависеть от текущего выбора — выбран автор, и в «Серии» видны только его
 * серии, а в «Жанрах» только его жанры. Раньше фасеты считались по всей библиотеке
 * (`STUB(Sprint 4)`), поэтому фильтры не сужались и экран разрастался.
 *
 * Фасет считается БЕЗ собственного фильтра (авторы — без фильтра по авторам, серии — без фильтра
 * по сериям): иначе, выбрав одного автора, нельзя было бы выбрать второго. Это стандартное
 * поведение фасетного поиска.
 *
 * Авторские фасеты идут по связям `book_authors`/`authors`, а не по строке `books.authorsText`:
 * строка хранит всех соавторов одной строкой, поэтому соавтор, записанный вторым, не получал
 * своей строки ни в фильтре, ни в обзоре (дефект Александра, 29.09.2026).
 */
internal object FacetSql {

    /**
     * Авторы с числом книг — для фильтра: строка на каждого человека.
     *
     * Это измерение «все книги автора»: книга с соавторами попадает в строки всех своих авторов.
     * Категории обзора (наборы) — отдельный фасет [authorSets].
     */
    fun authors(query: BookQuery): SupportSQLiteQuery = facet(
        query = query.copy(authorNames = emptySet(), authorSets = emptySet()),
        select = "SELECT authors.name AS name, COUNT(*) AS bookCount FROM books " +
            "INNER JOIN book_authors ON book_authors.bookId = books.id " +
            "INNER JOIN authors ON authors.id = book_authors.authorId",
        extra = null,
        groupBy = "GROUP BY authors.id, authors.name ORDER BY authors.name COLLATE NOCASE ASC",
    )

    /**
     * Категории обзора авторов — точные наборы авторов книги с числом книг.
     *
     * Ключ набора собирается в SQL: имена авторов книги сортируются по алфавиту и соединяются
     * разделителем, поэтому «A, B» и «B, A» дают одну строку, а «A, B, C» — другую. Тот же
     * разделитель использует [io.github.bookshelf.core.data.author.authorSetKey], так что ключ
     * категории один и тот же в запросе и в UI. Сортировка подзапроса обязательна: без неё
     * `GROUP_CONCAT` склеивал бы имена в порядке строк, и одна и та же пара распадалась бы на две
     * категории.
     */
    fun authorSets(query: BookQuery): SupportSQLiteQuery = facet(
        query = query.copy(authorNames = emptySet(), authorSets = emptySet()),
        select = "SELECT keyed.setKey AS setKey, COUNT(*) AS bookCount FROM books " +
            "INNER JOIN ($AUTHOR_SET_KEYS) keyed ON keyed.bookId = books.id",
        extra = null,
        groupBy = "GROUP BY keyed.setKey ORDER BY keyed.setKey COLLATE NOCASE ASC",
    )

    fun series(query: BookQuery): SupportSQLiteQuery = facet(
        query = query.copy(series = emptySet()),
        select = "SELECT books.seriesName AS seriesName, COUNT(*) AS bookCount FROM books",
        extra = "books.seriesName IS NOT NULL AND books.seriesName <> ''",
        groupBy = "GROUP BY books.seriesName ORDER BY books.seriesName COLLATE NOCASE ASC",
    )

    /**
     * Серии одного набора авторов — серии книг категории обзора.
     *
     * Условие «ровно этот состав авторов» берётся из [BookPageSql.booksWithExactAuthorSet], то же
     * самое, что у фильтра списка книг: иначе число в категории и её содержимое разъезжались бы.
     * Строка с `NULL` в имени — книги набора вне серий.
     *
     * @param authorNames имена набора по алфавиту;
     * @param genreCode код жанра: `null` — все серии набора, иначе только серии с книгами этого
     *   жанра (обзор по жанрам: на уровне набора видны только его книги выбранного жанра).
     */
    fun seriesOfAuthorSet(authorNames: List<String>, genreCode: String?): SupportSQLiteQuery {
        // Порядок параметров совпадает с порядком placeholder'ов: сначала имена набора и их число
        // (условие «ровно этот состав»), затем код жанра.
        val args = mutableListOf<Any?>()
        args.addAll(authorNames)
        args.add(authorNames.size)
        // Жанр добавляется отдельным условием, а не через «(? IS NULL OR EXISTS(… = ?))»: у такой
        // записи ДВА плейсхолдера, а параметр передавался один — второй оставался NULL, и
        // `tags.name = NULL` не находил ничего. Дефект с устройства (29.09.2026): из жанра у набора
        // авторов не показывалось ни одной серии, хотя серии есть.
        val genreCondition = genreCode?.let {
            args.add(it)
            "AND $BOOKS_WITH_GENRE "
        }.orEmpty()
        val sql = "SELECT books.seriesName AS seriesName, COUNT(*) AS bookCount FROM books " +
            "WHERE ${BookPageSql.booksWithExactAuthorSet(authorNames.size)} " +
            genreCondition +
            "GROUP BY books.seriesName ORDER BY books.seriesName COLLATE NOCASE ASC"
        return SimpleSQLiteQuery(sql, args.toTypedArray())
    }

    fun tags(query: BookQuery): SupportSQLiteQuery = facet(
        query = query.copy(tags = emptySet()),
        select = "SELECT tags.name AS name, COUNT(*) AS bookCount FROM books " +
            "INNER JOIN book_tags ON book_tags.bookId = books.id " +
            "INNER JOIN tags ON tags.id = book_tags.tagId",
        extra = null,
        groupBy = "GROUP BY tags.id, tags.name ORDER BY bookCount DESC, tags.name COLLATE NOCASE ASC",
    )

    /**
     * Источники (папки) с числом книг.
     *
     * Свой фильтр снимается: выбранная папка не должна скрывать остальные, иначе второй источник
     * уже не выбрать. Числа — «сколько книг придёт из этой папки при текущих фильтрах».
     */
    fun sources(query: BookQuery): SupportSQLiteQuery = facet(
        query = query.copy(sourceIds = emptySet()),
        // Имя источника лежит в колонке `displayName`: поля `name` в таблице `sources` нет.
        // Такая ошибка не ловится проверкой строки SQL — только исполнением запроса (см.
        // `SourceFacetTest`: на устройстве фильтры показывали «Не удалось загрузить фильтры»).
        select = "SELECT sources.id AS sourceId, sources.displayName AS sourceName, " +
            "COUNT(*) AS bookCount " +
            "FROM books INNER JOIN sources ON sources.id = books.sourceId",
        extra = null,
        groupBy = "GROUP BY sources.id, sources.displayName " +
            "ORDER BY sources.displayName COLLATE NOCASE ASC",
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

    /**
     * Подзапрос «книга → ключ набора её авторов».
     *
     * Вложенный `ORDER BY` обязателен: `GROUP_CONCAT` собирает имена в том порядке, в котором их
     * отдаёт подзапрос, и без сортировки «A, B» и «B, A» дали бы разные ключи.
     */
    private const val AUTHOR_SET_KEYS: String =
        "SELECT ba.bookId AS bookId, (" +
            "SELECT GROUP_CONCAT(ordered.name, '$AUTHOR_SET_SEPARATOR') FROM (" +
            "SELECT a.name AS name FROM book_authors ba2 " +
            "INNER JOIN authors a ON a.id = ba2.authorId " +
            "WHERE ba2.bookId = ba.bookId ORDER BY a.name COLLATE NOCASE ASC" +
            ") ordered) AS setKey " +
            "FROM book_authors ba GROUP BY ba.bookId"

    private const val BOOKS_WITH_GENRE: String =
        "EXISTS (SELECT 1 FROM book_tags " +
            "INNER JOIN tags ON tags.id = book_tags.tagId " +
            "WHERE book_tags.bookId = books.id AND tags.name = ?)"
}
