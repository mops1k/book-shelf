package io.github.bookshelf.feature.library

/**
 * Чистая арифметика постраничного листания полки («E-ink UX» §3.3).
 *
 * Вынесена из ViewModel отдельным файлом: страницы считаются на стороне ViewModel
 * (у неё нет доступа к Compose-состоянию `EinkPagerState`), а подпись «Стр. N/M · K книг»
 * на экране формирует подпись подвала.
 */

/** Размер страницы по умолчанию: 2 колонки × 10 строк в режиме «Крупный». */
const val DEFAULT_PAGE_SIZE: Int = 20

/** Сколько всего страниц при [total] книгах и размере страницы [pageSize]; минимум одна. */
fun pageCount(total: Int, pageSize: Int): Int =
    if (total <= 0 || pageSize <= 0) 1 else (total + pageSize - 1) / pageSize

/** Приводит номер страницы [page] (нумерация с нуля) внутрь допустимого диапазона. */
fun clampPage(page: Int, total: Int, pageSize: Int): Int =
    page.coerceIn(0, pageCount(total, pageSize) - 1)

/** Есть ли страница перед текущей. */
fun hasPreviousPage(page: Int): Boolean = page > 0

/** Есть ли страница после текущей. */
fun hasNextPage(page: Int, total: Int, pageSize: Int): Boolean = page < pageCount(total, pageSize) - 1
