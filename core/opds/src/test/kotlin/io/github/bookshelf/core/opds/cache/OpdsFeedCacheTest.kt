package io.github.bookshelf.core.opds.cache

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.getOrNull
import io.github.bookshelf.core.database.entity.OpdsFeedCacheEntity
import io.github.bookshelf.core.opds.model.OpdsEntry
import io.github.bookshelf.core.opds.model.OpdsFeed
import io.github.bookshelf.core.opds.model.OpdsLink
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Кэш фидов: тело пишется файлом, метаданные — в БД.
 *
 * DAO подменяется заглушкой: проверяется поведение кэша, а не Room.
 */
class OpdsFeedCacheTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val dao = FakeCacheDao()
    private val now = 1_000_000L

    private fun cache(nowMillis: Long) = DefaultOpdsFeedCache(
        dao = dao,
        cacheDir = folder.root,
        clock = FixedClock(nowMillis),
    )

    @Test
    fun stores_and_reads_feed_back() = runTest {
        val feed = sampleFeed()
        cache(now).put(feed, etag = "v1", lastModified = "Mon, 01 Jan 2026 00:00:00 GMT")

        val cached = cache(now).get(hashOf(feed), maxAgeMillis = MAX_AGE).getOrNull()

        assertThat(cached).isNotNull()
        assertThat(cached?.feed?.title).isEqualTo("Каталог")
        assertThat(cached?.feed?.entries?.first()?.title).isEqualTo("Кольцо")
        assertThat(cached?.etag).isEqualTo("v1")
    }

    @Test
    fun entry_links_and_cover_survive_round_trip() = runTest {
        val feed = sampleFeed()
        cache(now).put(feed, null, null)

        val entries = cache(now).get(hashOf(feed), MAX_AGE).getOrNull()?.feed?.entries.orEmpty()

        assertThat(entries).hasSize(1)
        assertThat(entries.first().coverUrl).isEqualTo("https://host/covers/1.jpg")
        assertThat(entries.first().links.first().rel).isEqualTo("http://opds-spec.org/acquisition/open-access")
    }

    @Test
    fun stale_entry_is_treated_as_missing() = runTest {
        val feed = sampleFeed()
        cache(now).put(feed, null, null)

        // Прошло больше maxAge: кэш не годен, но лежит — вызывающий обязан спросить сеть.
        val cached = cache(now + MAX_AGE + 1).get(hashOf(feed), maxAgeMillis = MAX_AGE).getOrNull()

        assertThat(cached).isNull()
    }

    @Test
    fun missing_url_returns_null_not_error() = runTest {
        assertThat(cache(now).get("нет такого", MAX_AGE).getOrNull()).isNull()
    }

    @Test
    fun deleted_body_file_yields_null_not_crash() = runTest {
        val feed = sampleFeed()
        cache(now).put(feed, null, null)
        File(dao.rows.values.first().bodyPath).delete()

        // Тело пропало, а запись в БД осталась: это не ошибка, а отсутствие кэша.
        assertThat(cache(now).get(hashOf(feed), MAX_AGE).getOrNull()).isNull()
    }

    @Test
    fun key_is_stable_across_instances() = runTest {
        val feed = sampleFeed()
        cache(now).put(feed, null, null)
        cache(now + 5).put(feed, null, null)

        // Второй экземпляр обязан перезаписать запись, а не завести вторую.
        assertThat(dao.rows).hasSize(1)
    }

    private fun hashOf(feed: OpdsFeed): String = feed.id.hashCode().toUInt().toString(16)

    private fun sampleFeed() = OpdsFeed(
        title = "Каталог",
        id = "https://host/opds/root.xml",
        updated = "2026-01-01T00:00:00Z",
        links = listOf(OpdsLink(rel = "self", href = "https://host/opds/root.xml")),
        entries = listOf(
            OpdsEntry(
                id = "tag:book:1",
                title = "Кольцо",
                links = listOf(
                    OpdsLink(
                        rel = "http://opds-spec.org/acquisition/open-access",
                        href = "https://host/dl/1",
                        type = "application/fb2+zip",
                    ),
                    OpdsLink(rel = "http://opds-spec.org/image", href = "https://host/covers/1.jpg"),
                ),
                coverUrl = "https://host/covers/1.jpg",
            ),
        ),
    )

    private class FakeCacheDao : io.github.bookshelf.core.database.dao.OpdsFeedCacheDao {
        val rows = mutableMapOf<String, OpdsFeedCacheEntity>()

        override suspend fun upsert(entry: OpdsFeedCacheEntity) {
            rows[entry.urlHash] = entry
        }

        override suspend fun getByUrlHash(urlHash: String) = rows[urlHash]

        override suspend fun deleteByUrlHash(urlHash: String) {
            rows.remove(urlHash)
        }

        override suspend fun deleteFetchedBefore(threshold: Long) {
            rows.entries.removeAll { it.value.fetchedAt < threshold }
        }

        override suspend fun countAll(): Int = rows.size
    }

    private class FixedClock(private val value: Long) : io.github.bookshelf.core.common.time.AppClock {
        override fun nowMillis(): Long = value
    }

    private companion object {
        const val MAX_AGE = 60_000L
    }
}
