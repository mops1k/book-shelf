package io.github.bookshelf.core.data.mapper

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import io.github.bookshelf.core.data.author.AUTHOR_SET_SEPARATOR
import io.github.bookshelf.core.data.model.Grouping
import io.github.bookshelf.core.data.model.LibraryQuery
import io.github.bookshelf.core.data.model.SortOrder
import io.github.bookshelf.core.data.search.ftsQueryOf

/**
 * Сборка SQL для страницы полки («Архитектура» §3.5).
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
/**
 * Порядок чтения серии: сначала книги с номером (по возрастанию), затем без номера
 * (по году издания по возрастанию), внутри одного года — по названию.
 *
 * `IS NULL` в начале каждого ключа ставит книги без значения в конец: в SQLite `NULL`
 * по умолчанию сортируется первым, и серия начиналась бы с книги без номера.
 */
private const val SERIES_ORDER: String =
    "ORDER BY books.seriesIndex IS NULL, books.seriesIndex ASC, " +
        "books.publishedYear IS NULL, books.publishedYear ASC, " +
        "books.sortTitle COLLATE NOCASE ASC, books.id ASC"

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
    internal fun buildWhere(
        bookQuery: io.github.bookshelf.core.data.model.BookQuery,
        args: MutableList<Any?>,
    ): String {
        val conditions = mutableListOf<String>()

        bookQuery.sourceIds.takeIf { it.isNotEmpty() }?.let {
            conditions += "books.sourceId IN (${placeholders(it.size)})"
            args.addAll(it)
        }
        // Автор — связи book_authors/authors. Условие намеренно не смотрит на `position`:
        // «все книги автора» означают любую позицию и любых соавторов, иначе соавтор, записанный
        // вторым, не находился бы вовсе (дефект 29.09.2026). Строка `books.authorsText` для отбора
        // не годится: она хранит всех авторов одной строкой.
        bookQuery.authorNames.takeIf { it.isNotEmpty() }?.let {
            conditions += booksWithAuthorNames(it.size)
            args.addAll(it)
        }
        // Категория обзора авторов — ТОЧНЫЙ набор: мало «среди авторов есть A и B», нужно ещё,
        // чтобы лишних авторов у книги не было. Иначе книга «A, B, C» показалась бы в категории
        // «A, B», и число в категории разошлось бы с содержимым.
        bookQuery.authorSets.takeIf { it.isNotEmpty() }?.let { sets ->
            val setConditions = sets.map { names ->
                args.addAll(names)
                args.add(names.size)
                booksWithExactAuthorSet(names.size)
            }
            conditions += "(${setConditions.joinToString(" OR ")})"
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
        bookQuery.tags.takeIf { it.isNotEmpty() }?.let { tags ->
            // Значения фильтра — коды жанров из метаданных (`sf_space`), а `book_tags.tagId`
            // хранит `tags.id` (`tag:sf_space`). Сравнивать код с колонкой tagId напрямую нельзя:
            // не совпадала ни одна строка, и «полка по жанру» показывала 0 книг, хотя чип фасета
            // честно писал 43. Поэтому связь идёт через `tags.name`, а код остаётся значением
            // фильтра и записью в правилах умной полки.
            conditions += "books.id IN (SELECT book_tags.bookId FROM book_tags " +
                "INNER JOIN tags ON tags.id = book_tags.tagId " +
                "WHERE tags.name IN (${placeholders(tags.size)}))"
            args.addAll(tags)
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
        // Внутри одной серии книги читаются по порядку выпуска, а не по выбранной сортировке:
        // у большинства книг есть номер в серии (`books.seriesIndex`), у остальных — год издания.
        // Так серия «1, 2, 3» не превращается в алфавитный список, куда бы пользователь ни зашёл —
        // из обзора по авторам или из обзора по сериям.
        if (query.bookQuery.series.size == 1) return SERIES_ORDER

        val direction = if (query.descending) "DESC" else "ASC"
        val groupingColumn = when (query.grouping) {
            Grouping.NONE -> null
            Grouping.SERIES -> "books.seriesName"
            // Группировка по автору — часть авторского измерения: рядом встают книги одного
            // набора авторов, а не одной строки `books.authorsText`. Иначе «A, B» и «B, A» давали
            // бы два соседних, но разных заголовка группы (дефект 29.09.2026).
            Grouping.AUTHOR -> authorSetOrderExpression()
            Grouping.FOLDER -> "books.relativePath"
            Grouping.FORMAT -> "books.extension"
        }
        val parts = buildList {
            // NULL в начале строки сортировки выглядит как «книги без серии» в самом верху;
            // для группировки это раздражает, поэтому пустое значение уходит в конец.
            groupingColumn?.let { add("$it COLLATE NOCASE $direction") }
            // «Значения нет» — отдельный первый ключ, и он всегда возрастающий: иначе книги, которые
            // не открывали или не читали, занимали бы первые места при возрастании (в SQLite `NULL`
            // идёт первым). На устройстве 01.10.2026 так и было: из 92 книг дата открытия была у двух.
            missingLastKey(query.sort)?.let { add(it) }
            add(sortColumn(query.sort, direction))
            add("books.id ASC")
        }
        return "ORDER BY " + parts.joinToString(", ")
    }

    /**
     * Ключ «у книги нет значения»: `0` — значение есть, `1` — нет.
     *
     * Стоит первым и не меняет направление вместе с сортировкой: книги без даты открытия и без
     * прогресса обязаны быть в конце и при возрастании, и при убывании. Для прогресса «нет
     * значения» — это ноль (`COALESCE`), в том числе явный ноль в базе: книга, которую открыли,
     * но не прочитали, стоит рядом с непрочитанными, а не среди читаемых.
     */
    private fun missingLastKey(sort: SortOrder): String? = when (sort) {
        SortOrder.LAST_OPENED -> "books.lastOpenedAt IS NULL"
        SortOrder.PROGRESS -> "COALESCE(progress.percent, 0) = 0"
        else -> null
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

    /**
     * Книги, у которых есть хотя бы один из перечисленных авторов — на любой позиции.
     *
     * Связь берётся из `book_authors`/`authors`: это авторское измерение. Строка `books.authorsText`
     * сравнивалась целиком, поэтому книга, где автор записан вторым, не находилась.
     */
    private fun booksWithAuthorNames(count: Int): String =
        "books.id IN (" +
            "SELECT book_authors.bookId FROM book_authors " +
            "INNER JOIN authors ON authors.id = book_authors.authorId " +
            "WHERE authors.name IN (${placeholders(count)}))"

    /**
     * Книги, у которых состав авторов РОВНО равен переданным именам.
     *
     * `HAVING COUNT(*) = ?` — это и есть «нет лишних авторов»: без него книга «A, B, C» попала бы
     * в категорию «A, B». Порядок имён не важен: сравниваются множества, а не строки. Условие
     * общее с фасетом серий набора (`FacetSql.seriesOfAuthorSet`), поэтому вынесено наружу:
     * разойдись оно, число в категории перестало бы совпадать с её содержимым.
     */
    internal fun booksWithExactAuthorSet(count: Int): String =
        "books.id IN (" +
            "SELECT book_authors.bookId FROM book_authors " +
            "INNER JOIN authors ON authors.id = book_authors.authorId " +
            "WHERE authors.name IN (${placeholders(count)}) " +
            "GROUP BY book_authors.bookId HAVING COUNT(*) = ?)"

    /**
     * Выражение ключа набора авторов книги — для группировки по автору.
     *
     * Имена собираются по алфавиту (`GROUP_CONCAT` по отсортированному подзапросу), поэтому
     * «A, B» и «B, A» дают одну и ту же строку и книги одного состава идут подряд.
     */
    private fun authorSetOrderExpression(): String =
        "(SELECT GROUP_CONCAT(author_name, '${AUTHOR_SET_SEPARATOR}') FROM (" +
            "SELECT authors.name AS author_name FROM book_authors " +
            "INNER JOIN authors ON authors.id = book_authors.authorId " +
            "WHERE book_authors.bookId = books.id " +
            "ORDER BY authors.name COLLATE NOCASE ASC))"

    private const val PROGRESS_JOIN = "LEFT JOIN reading_progress AS progress ON progress.bookId = books.id"
}
