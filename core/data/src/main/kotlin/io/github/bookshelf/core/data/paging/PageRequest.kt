package io.github.bookshelf.core.data.paging

/**
 * Запрос страницы данных.
 *
 * Преобразуется из [io.github.bookshelf.core.data.model.LibraryQuery] функцией
 * `LibraryQuery.toPageRequest()` (см. `mapper/BookMappers.kt`) и передаётся в DAO. Отдельный тип,
 * а не пара чисел, — чтобы `offset`/`limit` нельзя было перепутать местами.
 *
 * @param offset смещение от начала выборки, ≥ 0;
 * @param limit размер страницы, > 0.
 */
data class PageRequest(
    val offset: Int,
    val limit: Int,
) {
    init {
        require(offset >= 0) { "offset не может быть отрицательным: $offset" }
        require(limit > 0) { "limit должен быть положительным: $limit" }
    }

    /** Номер страницы с нуля (обратная операция к [ofPage]). */
    val page: Int get() = offset / limit

    companion object {

        /** Размер страницы по умолчанию: 50 книг — компромисс между запросом и числом кадров. */
        const val DEFAULT_PAGE_SIZE = 50

        /** Запрос по номеру страницы (нумерация с нуля). */
        fun ofPage(page: Int, pageSize: Int = DEFAULT_PAGE_SIZE): PageRequest {
            require(page >= 0) { "page не может быть отрицательным: $page" }
            return PageRequest(offset = page * pageSize, limit = pageSize)
        }
    }
}
