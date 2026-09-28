package io.github.bookshelf.core.data.repository.impl

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.getOrNull
import io.github.bookshelf.core.common.error.isFailure
import io.github.bookshelf.core.data.model.OpdsCatalog
import io.github.bookshelf.core.data.sync.DownloadScheduler
import io.github.bookshelf.core.database.dao.OpdsCatalogDao
import io.github.bookshelf.core.database.dao.OpdsDownloadDao
import io.github.bookshelf.core.database.model.OpdsDownloadState
import io.github.bookshelf.core.database.entity.OpdsCatalogEntity
import io.github.bookshelf.core.database.entity.OpdsDownloadEntity
import io.github.bookshelf.core.opds.cache.CachedFeed
import io.github.bookshelf.core.opds.cache.OpdsFeedCache
import io.github.bookshelf.core.opds.client.ConditionalGet
import io.github.bookshelf.core.opds.client.HttpResponse
import io.github.bookshelf.core.opds.client.OpdsHttpClient
import io.github.bookshelf.core.opds.model.OpdsEntry as FeedEntry
import io.github.bookshelf.core.opds.model.OpdsFeed
import io.github.bookshelf.core.opds.model.OpdsLink
import io.github.bookshelf.core.opds.parser.OpdsFeedParser
import io.github.bookshelf.core.common.error.AppResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Репозиторий каталогов OPDS.
 *
 * Проверяется то, что пользователь увидит на экране: каталог добавляется только если адрес
 * действительно отдаёт фид, корневой заход идёт из кэша без сети, а скачивание выбирает
 * именно ссылку «скачать», а не «купить».
 */
class OpdsRepositoryImplTest {

    private val http = FakeHttp()
    private val catalogDao = FakeCatalogDao()
    private val downloadDao = FakeDownloadDao()
    private val cache = FakeCache()

    /** Флаг — поле экземпляра, а не companion: у каждого теста JUnit свой экземпляр. */
    private var parserShouldFail = false

    private fun repository() = OpdsRepositoryImpl(
        catalogDao = catalogDao,
        http = http,
        parser = FakeParser { parserShouldFail },
        cache = cache,
        downloadDao = downloadDao,
        clock = FixedClock,
        // Планировщик в тесте не проверяется: очередь проверяется по таблице `opds_downloads`.
        downloadScheduler = object : DownloadScheduler {
            override fun schedule(downloadId: String) = Unit
        },
    )

    @Test
    fun add_catalog_stores_it_after_successful_fetch() = runTest {
        val result = repository().addCatalog("", "https://host/opds", null, null)

        val catalog = result.getOrNull()
        assertThat(catalog).isNotNull()
        // Пустой заголовок заменяется названием фида: «Каталог» хуже, чем то, что назвал сервер.
        assertThat(catalog?.title).isEqualTo("Flibusta catalog")
        assertThat(catalogDao.rows).hasSize(1)
        // Фид кладётся в кэш сразу, чтобы первый заход не ждал второй запрос.
        assertThat(cache.saved).isNotNull()
    }

    @Test
    fun add_catalog_fails_when_server_errors() = runTest {
        http.failWith = io.github.bookshelf.core.common.error.AppError.Network(message = "нет сети")

        val result = repository().addCatalog("", "https://host/opds", null, null)

        // Каталог с недоступным адресом сохранять нельзя: пользователь увидел бы пустой список.
        assertThat(result.isFailure).isTrue()
        assertThat(catalogDao.rows).isEmpty()
    }

    @Test
    fun add_catalog_fails_on_unparsable_feed() = runTest {
        parserShouldFail = true

        assertThat(repository().addCatalog("", "https://host/opds", null, null).isFailure).isTrue()
        assertThat(catalogDao.rows).isEmpty()
    }

    @Test
    fun browse_root_is_served_from_cache_without_network() = runTest {
        catalogDao.rows["c1"] = entity("c1")
        cache.stored = CachedFeed(feed = feedFixture(), fetchedAt = FIXED_NOW, etag = "v1", lastModified = null)

        val entries = repository().browse("c1", url = null).getOrNull()

        assertThat(entries).hasSize(2)
        assertThat(http.calls).isEqualTo(0)
    }

    @Test
    fun browse_passes_conditional_headers_from_cache() = runTest {
        catalogDao.rows["c1"] = entity("c1")
        cache.stored = CachedFeed(feed = feedFixture(), fetchedAt = 0L, etag = "v1", lastModified = null)
        http.respondNotModified = true

        // Кэш старше maxAge, поэтому идём в сеть — и обязаны спросить условно.
        val entries = repository().browse("c1", url = "https://host/opds/new").getOrNull()

        assertThat(entries).hasSize(2)
        assertThat(http.conditional).isEqualTo(ConditionalGet("v1", null))
    }

    @Test
    fun browse_unknown_catalog_fails() = runTest {
        assertThat(repository().browse("нет такого", null).isFailure).isTrue()
    }

    @Test
    fun entry_prefers_open_access_link_over_purchase() = runTest {
        catalogDao.rows["c1"] = entity("c1")
        http.respondNotModified = true
        cache.stored = CachedFeed(feed = feedFixture(), fetchedAt = FIXED_NOW, etag = null, lastModified = null)

        val entries = repository().browse("c1", url = "https://host/opds/x").getOrNull().orEmpty()

        // У первой книги первая acquisition-ссылка — «купить»; взята должна быть open-access.
        assertThat(entries.first().acquisitionUrl).isEqualTo("https://host/dl/1-open.fb2.zip")
    }

    @Test
    fun enqueue_download_without_acquisition_link_fails() = runTest {
        val entry = io.github.bookshelf.core.data.model.OpdsEntry(id = "1", title = "Без ссылки")

        val result = repository().enqueueDownload(entry, "c1", "src", null)

        assertThat(result.isFailure).isTrue()
        assertThat(downloadDao.rows).isEmpty()
    }

    @Test
    fun enqueue_download_records_queue_entry() = runTest {
        val entry = io.github.bookshelf.core.data.model.OpdsEntry(
            id = "1",
            title = "Кольцо",
            authorsText = "Бакстер",
            acquisitionUrl = "https://host/dl/2-open.fb2.zip",
            mimeType = "application/fb2+zip",
        )

        repository().enqueueDownload(entry, "c1", "src", "Books")

        val saved = downloadDao.rows.values.single()
        assertThat(saved.title).isEqualTo("Кольцо")
        assertThat(saved.targetSourceId).isEqualTo("src")
        assertThat(saved.targetRelativeDir).isEqualTo("Books")
    }

    @Test
    fun enqueue_download_requires_target_source() = runTest {
        val entry = io.github.bookshelf.core.data.model.OpdsEntry(
            id = "1",
            title = "Кольцо",
            acquisitionUrl = "https://host/dl/1",
        )

        // Без папки книга исчезла бы: место назначения спрашиваем сразу, а не падаем в воркере.
        assertThat(repository().enqueueDownload(entry, "c1", null, null).isFailure).isTrue()
    }

    private fun entity(id: String) = OpdsCatalogEntity(
        id = id,
        title = "Flibusta catalog",
        url = "https://host/opds",
    )

    private fun feedFixture() = OpdsFeed(
        title = "Flibusta catalog",
        id = "https://host/opds",
        updated = "2026-01-01T00:00:00Z",
        entries = listOf(
            FeedEntry(
                id = "tag:book:1",
                title = "Кольцо",
                authors = listOf("Бакстер"),
                links = listOf(
                    OpdsLink(rel = "http://opds-spec.org/acquisition/buy", href = "https://host/buy/1"),
                    OpdsLink(
                        rel = "http://opds-spec.org/acquisition/open-access",
                        href = "https://host/dl/1-open.fb2.zip",
                        type = "application/fb2+zip",
                    ),
                ),
            ),
            FeedEntry(
                id = "tag:book:2",
                title = "Кольцо 2",
                links = listOf(
                    OpdsLink(
                        rel = "http://opds-spec.org/acquisition/buy",
                        href = "https://host/dl/2.fb2.zip",
                        type = "application/fb2+zip",
                    ),
                ),
            ),
        ),
    )

    private class FakeHttp : OpdsHttpClient {
        var failWith: io.github.bookshelf.core.common.error.AppError? = null
        var respondNotModified = false
        var calls = 0
        var conditional: ConditionalGet? = null

        override suspend fun get(
            url: String,
            conditional: ConditionalGet?,
            credentials: io.github.bookshelf.core.opds.auth.OpdsCredentials?,
        ): AppResult<HttpResponse> {
            calls++
            this.conditional = conditional
            failWith?.let { return AppResult.failure(it) }
            if (respondNotModified) {
                return AppResult.success(HttpResponse(304, "", "v1", null, url))
            }
            val body = """<?xml version="1.0" encoding="utf-8"?>
                <feed xmlns="http://www.w3.org/2005/Atom">
                  <id>$url</id><title>Flibusta catalog</title>
                  <link href="$url" rel="self"/>
                  <entry><id>tag:book:1</id><title>Кольцо</title></entry>
                </feed>""".trimIndent()
            return AppResult.success(HttpResponse(200, body, "v1", null, url))
        }
    }

    private class FakeParser(private val shouldFail: () -> Boolean) : OpdsFeedParser {
        override suspend fun parse(body: String, baseUrl: String): AppResult<OpdsFeed> {
            if (shouldFail()) {
                return AppResult.failure(
                    io.github.bookshelf.core.common.error.AppError.Parse(format = "opds", message = "мусор"),
                )
            }
            return AppResult.success(
                OpdsFeed(
                    title = "Flibusta catalog",
                    id = baseUrl,
                    updated = "",
                    links = listOf(OpdsLink(rel = "self", href = baseUrl)),
                    entries = listOf(FeedEntry(id = "tag:book:1", title = "Кольцо")),
                ),
            )
        }
    }

    private class FakeCache : OpdsFeedCache {
        var stored: CachedFeed? = null
        var saved: CachedFeed? = null

        override suspend fun get(urlHash: String, maxAgeMillis: Long) = AppResult.success(stored)

        override suspend fun put(feed: OpdsFeed, etag: String?, lastModified: String?) {
            saved = CachedFeed(feed, FIXED_NOW, etag, lastModified)
        }
    }

    private class FakeCatalogDao : OpdsCatalogDao {
        val rows = linkedMapOf<String, OpdsCatalogEntity>()

        override suspend fun upsertAll(catalogs: List<OpdsCatalogEntity>) {
            catalogs.forEach { rows[it.id] = it }
        }

        override fun observeAll(): Flow<List<OpdsCatalogEntity>> = flowOf(rows.values.toList())

        override suspend fun getById(id: String) = rows[id]

        override suspend fun deleteById(id: String) {
            rows.remove(id)
        }
    }

    /** Часы фиксированы: иначе проверки кэша зависели бы от реального времени. */
    private object FixedClock : io.github.bookshelf.core.common.time.AppClock {
        override fun nowMillis(): Long = FIXED_NOW
    }

    private class FakeDownloadDao : OpdsDownloadDao {
        override fun observeActive(): Flow<List<OpdsDownloadEntity>> = flowOf(emptyList())

        val rows = linkedMapOf<String, OpdsDownloadEntity>()

        override suspend fun upsert(download: OpdsDownloadEntity) {
            rows[download.id] = download
        }

        override fun observeAll(): Flow<List<OpdsDownloadEntity>> = flowOf(rows.values.toList())

        /** Активная загрузка той же книги: повторное «Скачать» её переиспользует. */
        override suspend fun findActive(catalogId: String, entryId: String) = rows.values.firstOrNull {
            it.catalogId == catalogId && it.entryId == entryId &&
                it.state != OpdsDownloadState.DONE && it.state != OpdsDownloadState.FAILED
        }

        override suspend fun getById(id: String) = rows[id]

        override suspend fun deleteById(id: String) {
            rows.remove(id)
        }
    }

    private companion object {
        const val FIXED_NOW = 1_000_000L
    }
}
