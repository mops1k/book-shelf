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

/**
 * Сколько книг полка просит за один раз.
 *
 * Раньше запрос был один на 500 книг: на большой библиотеке это одна тяжёлая выдача, а книги
 * дальше пятисотой не показывались вовсе. Окно растёт по мере прокрутки, поэтому первая выдача
 * стоит столько же, сколько стоит открыть экран.
 */
const val SHELF_WINDOW_SIZE: Int = 50

/** Насколько заранее до конца окна просить следующую порцию: экран не должен упираться в пустоту. */
const val SHELF_PREFETCH: Int = 10

/** Пора ли просить следующую порцию: видимый конец подошёл к концу загруженного окна. */
fun shouldLoadMore(
    lastVisibleIndex: Int,
    loaded: Int,
    total: Int,
    prefetch: Int = SHELF_PREFETCH,
): Boolean {
    // Нечего догружать: окно пустое или в нём уже вся библиотека.
    if (loaded <= 0 || loaded >= total) return false
    // Список ещё не показал ни одной строки — по такому «концу» догрузку не начинают.
    if (lastVisibleIndex < 0) return false
    return lastVisibleIndex >= loaded - prefetch
}

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
