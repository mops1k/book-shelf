package io.github.bookshelf.core.data.paging

/**
 * Страница результата вместе с общим числом записей.
 *
 * Нужна, чтобы UI показывал «Стр. 3/12 · 47 книг» («E-ink UX» §3.3) без второго запроса:
 * `totalCount` считается тем же обращением (или `COUNT(*)` по тем же фильтрам).
 *
 * Все вычисляемые свойства — чистая арифметика, не требующая БД, и покрыты `PageResultTest`.
 * Граничные случаи (`limit <= 0`, пустой результат) описаны явно, чтобы не было деления на ноль.
 */
data class PageResult<T>(
    val items: List<T>,
    val totalCount: Int,
    val offset: Int,
    val limit: Int,
) {
    /** Номер текущей страницы с нуля. */
    val currentPage: Int get() = if (limit <= 0) 0 else offset / limit

    /** Всего страниц; 0, если записей нет. */
    val pageCount: Int get() = if (limit <= 0) 0 else (totalCount + limit - 1) / limit

    /** Есть ли записи после текущей страницы. */
    val hasNext: Boolean get() = offset + items.size < totalCount

    /** Есть ли записи до текущей страницы. */
    val hasPrevious: Boolean get() = offset > 0

    /** Пустая ли страница. */
    val isEmpty: Boolean get() = items.isEmpty()

    companion object {

        /** Пустая страница для заглушек репозиториев и пустых состояний. */
        fun <T> empty(
            offset: Int = 0,
            limit: Int = PageRequest.DEFAULT_PAGE_SIZE,
        ): PageResult<T> = PageResult(
            items = emptyList(),
            totalCount = 0,
            offset = offset,
            limit = limit,
        )
    }
}
