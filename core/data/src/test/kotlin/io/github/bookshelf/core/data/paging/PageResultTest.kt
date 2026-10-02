package io.github.bookshelf.core.data.paging

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Тест вычисляемых свойств [PageResult] («E-ink UX» §3.3).
 *
 * Здесь живёт вся арифметика «Стр. 3/12 · 47 книг», включая граничные случаи: пустой результат,
 * неполная последняя страница и защита от деления на ноль при `limit <= 0`.
 */
class PageResultTest {

    @Test
    fun page_count_rounds_up_for_partial_last_page() {
        val page = PageResult(items = listOf("a", "b", "c"), totalCount = 47, offset = 0, limit = 10)

        assertThat(page.pageCount).isEqualTo(5)
        assertThat(page.currentPage).isEqualTo(0)
        assertThat(page.hasNext).isTrue()
        assertThat(page.hasPrevious).isFalse()
        assertThat(page.isEmpty).isFalse()
    }

    @Test
    fun last_page_has_no_next() {
        val page = PageResult(items = listOf("x"), totalCount = 21, offset = 20, limit = 10)

        assertThat(page.currentPage).isEqualTo(2)
        assertThat(page.hasNext).isFalse()
        assertThat(page.hasPrevious).isTrue()
    }

    @Test
    fun partial_page_smaller_than_limit_is_still_the_last_one() {
        val page = PageResult(items = listOf("a", "b"), totalCount = 12, offset = 10, limit = 10)

        assertThat(page.hasNext).isFalse()
        assertThat(page.pageCount).isEqualTo(2)
    }

    @Test
    fun empty_result_has_zero_pages() {
        val page = PageResult.empty<String>(offset = 0, limit = 20)

        assertThat(page.items).isEmpty()
        assertThat(page.totalCount).isEqualTo(0)
        assertThat(page.pageCount).isEqualTo(0)
        assertThat(page.currentPage).isEqualTo(0)
        assertThat(page.hasNext).isFalse()
        assertThat(page.hasPrevious).isFalse()
        assertThat(page.isEmpty).isTrue()
    }

    @Test
    fun non_positive_limit_does_not_divide_by_zero() {
        val page = PageResult(items = emptyList<String>(), totalCount = 5, offset = 0, limit = 0)

        assertThat(page.pageCount).isEqualTo(0)
        assertThat(page.currentPage).isEqualTo(0)
    }

    @Test
    fun page_request_helpers_are_consistent() {
        val request = PageRequest.ofPage(page = 3, pageSize = 20)

        assertThat(request.offset).isEqualTo(60)
        assertThat(request.limit).isEqualTo(20)
        assertThat(request.page).isEqualTo(3)
        assertThat(PageRequest(offset = 60, limit = 20)).isEqualTo(request)
    }

    @Test
    fun page_request_rejects_invalid_bounds() {
        assertThat(runCatching { PageRequest(offset = -1, limit = 20) }.isFailure).isTrue()
        assertThat(runCatching { PageRequest(offset = 0, limit = 0) }.isFailure).isTrue()
        assertThat(runCatching { PageRequest.ofPage(page = -1, pageSize = 20) }.isFailure).isTrue()
    }
}
