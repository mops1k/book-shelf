package io.github.bookshelf.core.covers.cache

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.getOrNull
import io.github.bookshelf.core.covers.CoverCache
import io.github.bookshelf.core.common.testing.TestAppDispatchers
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Дисковый кэш обложек («Архитектура» §5).
 *
 * Главное проверяемое свойство — **атомарность записи**. Обложка пишется на e-ink-устройстве во
 * время сканирования, и процесс может быть убит в любой момент. Если писать прямо в целевой
 * файл, после обрыва останется обрезанный WebP, который больше никогда не декодируется: файл
 * существует, `read` вернёт его, а на экране будет пустое место. Поэтому запись идёт через
 * `.tmp` с последующим `rename`.
 *
 * Тесты без Robolectric: контракт оперирует только `java.io.File`.
 */
class DiskCoverCacheTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var cacheDir: File
    private lateinit var cache: DiskCoverCache

    @Before
    fun setUp() {
        cacheDir = temporaryFolder.newFolder("covers")
        cache = DiskCoverCache(cacheDir, TestAppDispatchers())
    }

    @Test
    fun read_returns_null_when_there_is_no_cover_yet() = runTest {
        val result = cache.read("book-1", CoverCache.CoverKind.FULL)

        assertThat(result.getOrNull()).isNull()
    }

    @Test
    fun store_writes_the_file_and_read_returns_it() = runTest {
        val bytes = byteArrayOf(1, 2, 3, 4)

        val stored = cache.store("book-1", CoverCache.CoverKind.FULL, bytes)
        val read = cache.read("book-1", CoverCache.CoverKind.FULL)

        assertThat(stored.getOrNull()).isNotNull()
        assertThat(read.getOrNull()?.readBytes()).isEqualTo(bytes)
    }

    @Test
    fun store_leaves_no_temporary_file_behind() = runTest {
        cache.store("book-1", CoverCache.CoverKind.FULL, byteArrayOf(9))

        val leftovers = cacheDir.listFiles().orEmpty().filter { it.name.endsWith(CoverPaths.TEMP_SUFFIX) }

        assertThat(leftovers).isEmpty()
    }

    @Test
    fun store_replaces_an_existing_cover_atomically() = runTest {
        cache.store("book-1", CoverCache.CoverKind.FULL, byteArrayOf(1))

        cache.store("book-1", CoverCache.CoverKind.FULL, byteArrayOf(2, 2))

        val read = cache.read("book-1", CoverCache.CoverKind.FULL)
        assertThat(read.getOrNull()?.readBytes()).isEqualTo(byteArrayOf(2, 2))
    }

    @Test
    fun full_and_tile_cover_are_stored_separately() = runTest {
        cache.store("book-1", CoverCache.CoverKind.FULL, byteArrayOf(1))
        cache.store("book-1", CoverCache.CoverKind.TILE, byteArrayOf(2))

        val full = cache.read("book-1", CoverCache.CoverKind.FULL)
        val tile = cache.read("book-1", CoverCache.CoverKind.TILE)

        assertThat(full.getOrNull()?.readBytes()).isEqualTo(byteArrayOf(1))
        assertThat(tile.getOrNull()?.readBytes()).isEqualTo(byteArrayOf(2))
    }

    @Test
    fun invalidate_removes_both_variants() = runTest {
        cache.store("book-1", CoverCache.CoverKind.FULL, byteArrayOf(1))
        cache.store("book-1", CoverCache.CoverKind.TILE, byteArrayOf(2))

        cache.invalidate("book-1")

        assertThat(cache.read("book-1", CoverCache.CoverKind.FULL).getOrNull()).isNull()
        assertThat(cache.read("book-1", CoverCache.CoverKind.TILE).getOrNull()).isNull()
    }

    @Test
    fun invalidate_keeps_other_books() = runTest {
        cache.store("book-1", CoverCache.CoverKind.FULL, byteArrayOf(1))
        cache.store("book-2", CoverCache.CoverKind.FULL, byteArrayOf(2))

        cache.invalidate("book-1")

        assertThat(cache.read("book-2", CoverCache.CoverKind.FULL).getOrNull()).isNotNull()
    }

    @Test
    fun clear_removes_everything() = runTest {
        cache.store("book-1", CoverCache.CoverKind.FULL, byteArrayOf(1))
        cache.store("book-2", CoverCache.CoverKind.FULL, byteArrayOf(2))

        cache.clear()

        assertThat(cacheDir.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun size_bytes_sums_all_stored_files() = runTest {
        cache.store("book-1", CoverCache.CoverKind.FULL, ByteArray(10))
        cache.store("book-2", CoverCache.CoverKind.FULL, ByteArray(15))

        assertThat(cache.sizeBytes()).isEqualTo(25L)
    }

    @Test
    fun evict_to_limit_removes_least_recently_used_first() = runTest {
        val first = cache.store("old", CoverCache.CoverKind.FULL, ByteArray(100)).getOrNull()!!
        val second = cache.store("new", CoverCache.CoverKind.FULL, ByteArray(100)).getOrNull()!!
        // Разводим время модификации: LRU опирается на lastModified.
        first.setLastModified(1_000L)
        second.setLastModified(2_000L)

        val freed = cache.evictToLimit(limitBytes = 100L)

        assertThat(freed).isEqualTo(100L)
        assertThat(first.exists()).isFalse()
        assertThat(second.exists()).isTrue()
    }

    @Test
    fun evict_to_limit_keeps_everything_when_under_limit() = runTest {
        val file = cache.store("book-1", CoverCache.CoverKind.FULL, ByteArray(10)).getOrNull()!!

        val freed = cache.evictToLimit(limitBytes = 1_000L)

        assertThat(freed).isEqualTo(0L)
        assertThat(file.exists()).isTrue()
    }
}
