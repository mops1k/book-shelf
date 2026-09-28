package io.github.bookshelf.core.data.model

import io.github.bookshelf.core.data.paging.PageRequest

/**
 * Запрос страницы полки: фильтры + сортировка + группировка + пагинация (docs/architecture.md §3.5).
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
 * Набор активных фильтров (docs/architecture.md §3.5, ADR-008).
 *
 * Семантика множественного выбора: **ИЛИ внутри одного фасета, И между разными**. Пустые множества
 * означают «фильтр не задан», а не «ничего не найдено» — это важно для [isEmpty] и для того,
 * чтобы UI не показывал пустую полку на старте.
 *
 * @param sourceIds ограничение по источникам (папкам);
 * @param authorNames ключи авторов — это значение `books.authorsText` («Лев Толстой»), а не id:
 *   нормализованные таблицы `authors`/`book_authors` в приложении пока не заполняются (0 строк при
 *   72 книгах на устройстве), поэтому и фасеты, и фильтр, и обзор идут по денормализованному полю.
 *   У книги с несколькими авторами строка хранится целиком — известное упрощение до отдельной
 *   задачи нормализации;
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

/** Не выбраны источники, авторы и серии. */
private fun BookQuery.hasNoIdFilters(): Boolean =
    sourceIds.isEmpty() && authorNames.isEmpty() && series.isEmpty()

/** Не выбраны форматы, статусы и теги. */
private fun BookQuery.hasNoValueFilters(): Boolean =
    formats.isEmpty() && statuses.isEmpty() && tags.isEmpty() && !onlyWithoutSeries

/**
 * Добавить авторов к фильтру (копирование: [BookQuery] — неизменяемое значение).
 *
 * Множественный выбор — требование заказчика: «несколько авторов, несколько серий» (docs/architecture.md §3.5).
 */
fun BookQuery.withAuthor(vararg authorNames: String): BookQuery =
    copy(authorNames = this.authorNames + authorNames)
