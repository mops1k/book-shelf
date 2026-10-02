package io.github.bookshelf.core.data.model

import io.github.bookshelf.core.data.paging.PageRequest

/**
 * Запрос страницы полки: фильтры + сортировка + группировка + пагинация («Архитектура» §3.5).
 *
 * @param page номер страницы с нуля (так удобнее считать `offset = page * pageSize`);
 * @param pageSize размер страницы; по умолчанию [PageRequest.DEFAULT_PAGE_SIZE].
 */
data class LibraryQuery(
    val bookQuery: BookQuery = BookQuery(),
    val sort: SortOrder = SortOrder.TITLE,
    val descending: Boolean = false,
    val grouping: Grouping = Grouping.NONE,
    val page: Int = 0,
    val pageSize: Int = PageRequest.DEFAULT_PAGE_SIZE,
)

/**
 * Набор активных фильтров («Архитектура» §3.5, ADR-008).
 *
 * Семантика множественного выбора: **ИЛИ внутри одного фасета, И между разными**. Пустые множества
 * означают «фильтр не задан», а не «ничего не найдено» — это важно для [isEmpty] и для того,
 * чтобы UI не показывал пустую полку на старте.
 *
 * @param sourceIds ограничение по источникам (папкам);
 * @param authorNames имена авторов. Отбор идёт по связям `book_authors`/`authors`: книга
 *   подходит, если автор есть в ней на любой позиции и с любыми соавторами. Это измерение
 *   «все книги автора» — по нему работает клик по автору в карточке книги и правило умной полки.
 *   Строка `books.authorsText` для отбора не годится: она хранит всех соавторов одной строкой,
 *   поэтому книга, где автор записан вторым, по нему не находилась (дефект Александра, 29.09.2026);
 * @param authorSets ТОЧНЫЕ наборы авторов — категории обзора «Авторы». Отбор идёт по связям и
 *   требует, чтобы число авторов книги совпало с числом имён набора: «A, B» не должна попадать
 *   в категорию «A, B, C» и наоборот. Порядок имён в наборе не важен;
 * @param series названия серий (в БД это денормализованное поле);
 * @param formats расширения без точки (`fb2`, `epub`, …);
 * @param statuses статусы чтения;
 * @param tags теги/жанры;
 * @param favoritesOnly только избранное;
 * @param searchTerm поисковый запрос; `null` или пустая строка — поиск не задан.
 */
data class BookQuery(
    val sourceIds: Set<String> = emptySet(),
    val authorNames: Set<String> = emptySet(),
    val authorSets: Set<List<String>> = emptySet(),
    val series: Set<String> = emptySet(),
    val formats: Set<String> = emptySet(),
    val statuses: Set<ReadingStatus> = emptySet(),
    val tags: Set<String> = emptySet(),
    val favoritesOnly: Boolean = false,
    /** Только книги без серии — категория «Вне серий» в обзоре по авторам. */
    val onlyWithoutSeries: Boolean = false,
    val searchTerm: String? = null,
)

/**
 * Не задан ли ни один фильтр.
 *
 * Используется для быстрого пути «вся библиотека» и для отрисовки состояния «фильтры не выбраны».
 * Поиск из пробелов считается незаданным: иначе полка мигала бы пустым результатом.
 *
 * Условие разбито на короткие части: длинная цепочка `&&` нечитаема и не проходит статический
 * анализ (detekt `ComplexCondition`).
 */
val BookQuery.isEmpty: Boolean
    get() = hasNoFilters() && !favoritesOnly && searchTerm.isNullOrBlank()

/** Фильтры по идентификаторам и строкам не заданы. */
private fun BookQuery.hasNoFilters(): Boolean = hasNoIdFilters() && hasNoValueFilters()

/** Не выбраны источники, авторы (и их наборы) и серии. */
private fun BookQuery.hasNoIdFilters(): Boolean =
    sourceIds.isEmpty() && authorNames.isEmpty() && authorSets.isEmpty() && series.isEmpty()

/** Не выбраны форматы, статусы и теги. */
private fun BookQuery.hasNoValueFilters(): Boolean =
    formats.isEmpty() && statuses.isEmpty() && tags.isEmpty() && !onlyWithoutSeries

/**
 * Добавить авторов к фильтру (копирование: [BookQuery] — неизменяемое значение).
 *
 * Множественный выбор — требование заказчика: «несколько авторов, несколько серий» («Архитектура» §3.5).
 */
fun BookQuery.withAuthor(vararg authorNames: String): BookQuery =
    copy(authorNames = this.authorNames + authorNames)
