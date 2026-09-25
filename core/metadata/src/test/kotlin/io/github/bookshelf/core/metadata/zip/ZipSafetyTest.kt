package io.github.bookshelf.core.metadata.zip

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Лимиты распаковки — чистая арифметика zip-bomb-защиты (docs/formats.md §9).
 */
class ZipSafetyTest {

    private val limits = ZipLimits.DEFAULT

    @Test
    fun normal_archive_is_not_suspicious() {
        val suspicious = limits.isSuspicious(
            entries = NORMAL_ENTRIES,
            uncompressed = NORMAL_UNCOMPRESSED,
            compressed = NORMAL_COMPRESSED,
        )

        assertThat(suspicious).isFalse()
    }

    @Test
    fun too_many_entries_is_suspicious() {
        val suspicious = limits.isSuspicious(
            entries = limits.maxEntries + 1,
            uncompressed = 0L,
            compressed = 0L,
        )

        assertThat(suspicious).isTrue()
    }

    @Test
    fun too_large_uncompressed_size_is_suspicious() {
        val oversized = limits.maxUncompressedBytes + 1

        val suspicious = limits.isSuspicious(
            entries = NORMAL_ENTRIES,
            uncompressed = oversized,
            compressed = oversized,
        )

        assertThat(suspicious).isTrue()
    }

    @Test
    fun high_compression_ratio_is_suspicious() {
        val suspicious = limits.isSuspicious(
            entries = NORMAL_ENTRIES,
            uncompressed = TEN_MEGABYTES,
            compressed = ONE_KILOBYTE,
        )

        assertThat(suspicious).isTrue()
    }

    @Test
    fun zero_compressed_with_content_is_suspicious() {
        val suspicious = limits.isSuspicious(
            entries = NORMAL_ENTRIES,
            uncompressed = ONE_KILOBYTE,
            compressed = 0L,
        )

        assertThat(suspicious).isTrue()
    }

    @Test
    fun empty_archive_is_not_suspicious() {
        assertThat(limits.isSuspicious(entries = 0, uncompressed = 0L, compressed = 0L)).isFalse()
    }

    @Test
    fun custom_limits_are_respected() {
        val strict = ZipLimits(maxEntries = STRICT_ENTRIES)

        assertThat(strict.isSuspicious(entries = STRICT_ENTRIES, uncompressed = 0L, compressed = 0L)).isFalse()
        assertThat(strict.isSuspicious(entries = STRICT_ENTRIES + 1, uncompressed = 0L, compressed = 0L)).isTrue()
    }

    private companion object {
        const val NORMAL_ENTRIES = 100
        const val NORMAL_UNCOMPRESSED = 4_000L
        const val NORMAL_COMPRESSED = 1_000L
        const val TEN_MEGABYTES = 10L * 1_024 * 1_024
        const val ONE_KILOBYTE = 1_024L
        const val STRICT_ENTRIES = 5
    }
}
