package io.github.bookshelf.feature.library

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Тесты чистой арифметики страниц полки: без Compose и без ViewModel.
 */
class LibraryPagingTest {

    @Test
    fun `empty library always has a single page`() {
        assertThat(pageCount(total = 0, pageSize = 20)).isEqualTo(1)
    }

    @Test
    fun `page count rounds up to cover the tail page`() {
        assertThat(pageCount(total = 1, pageSize = 20)).isEqualTo(1)
        assertThat(pageCount(total = 140, pageSize = 20)).isEqualTo(7)
        assertThat(pageCount(total = 143, pageSize = 20)).isEqualTo(8)
    }

    @Test
    fun `nonsense page size does not produce zero pages`() {
        assertThat(pageCount(total = 10, pageSize = 0)).isEqualTo(1)
        assertThat(pageCount(total = 10, pageSize = -5)).isEqualTo(1)
    }

    @Test
    fun `clampPage keeps the page inside the range`() {
        assertThat(clampPage(page = -3, total = 143, pageSize = 20)).isEqualTo(0)
        assertThat(clampPage(page = 3, total = 143, pageSize = 20)).isEqualTo(3)
        assertThat(clampPage(page = 99, total = 143, pageSize = 20)).isEqualTo(7)
    }

    @Test
    fun `clampPage works for an empty library`() {
        assertThat(clampPage(page = 5, total = 0, pageSize = 20)).isEqualTo(0)
    }

    @Test
    fun `page neighbours are reported correctly`() {
        assertThat(hasPreviousPage(page = 0)).isFalse()
        assertThat(hasPreviousPage(page = 1)).isTrue()
        assertThat(hasNextPage(page = 6, total = 143, pageSize = 20)).isTrue()
        assertThat(hasNextPage(page = 7, total = 143, pageSize = 20)).isFalse()
    }
}
