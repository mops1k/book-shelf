package io.github.bookshelf.core.data.repository.impl

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.AppError
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
import io.github.bookshelf.core.datastore.SettingsRepository
import io.github.bookshelf.core.datastore.model.AppSettings
import io.github.bookshelf.core.datastore.model.OpdsSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Репозиторий каталогов OPDS.
 *
 * Проверяется то, что пользователь увидит на экране: каталог добавляется только если адрес
 * действительно отдаёт фид, корневой заход идёт из кэша без сети, поиск подставляет запрос в
 * шаблон OpenSearch (и достаёт его из дескриптора, если каталог объявил только дескриптор),
 * а скачивание выбирает именно ссылку «скачать», а не «купить».
 *
 * Robolectric нужен из-за разбора дескриптора OpenSearch: он идёт через `android.util.Xml`, и без
 * Robolectric тест падал бы с `NoClassDefFoundError`, что выглядит как «поиск сломан».
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class OpdsRepositoryImplTest {

    private val http = FakeHttp()
    private val catalogDao = FakeCatalogDao()
    private val downloadDao = FakeDownloadDao()
    private val cache = FakeCache()

    /** Флаг — поле экземпляра, а не companion: у каждого теста JUnit свой экземпляр. */
    private var parserShouldFail = false

    /** Парсер-фейк держим полем: тесты поиска задают ему шаблон OpenSearch. */
    private val parser = FakeParser { parserShouldFail }

    /** Настройки — поле: возраст кэша фидов берётся из них. */
    private val settingsRepository = FakeSettingsRepository()

    /** Пароли каталогов: подставное хранилище — настоящий Keystore живёт только на устройстве. */
    private val credentials = FakeCredentialStore()

    private fun repository() = OpdsRepositoryImpl(
        catalogDao = catalogDao,
        http = http,
        parser = parser,
        cache = cache,
        downloadDao = downloadDao,
        clock = FixedClock,
        // Планировщик в тесте не проверяется: очередь проверяется по таблице `opds_downloads`.
        downloadScheduler = object : DownloadScheduler {
            override fun schedule(downloadId: String) = Unit
        },
        settingsRepository = settingsRepository,
        credentialStore = credentials,
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
    fun clear_download_history_reports_how_many_records_were_removed() = runTest {
        downloadDao.rows["done"] = downloadRow("done", OpdsDownloadState.DONE)
        downloadDao.rows["running"] = downloadRow("running", OpdsDownloadState.RUNNING)

        val removed = repository().clearDownloadHistory().getOrNull()

        assertThat(removed).isEqualTo(1)
        // Активная загрузка остаётся: иначе работа писала бы прогресс в никуда.
        assertThat(downloadDao.rows.keys).containsExactly("running")
    }

    private fun downloadRow(id: String, state: OpdsDownloadState) = OpdsDownloadEntity(
        id = id,
        catalogId = "catalog-1",
        entryId = "entry-$id",
        title = "Книга",
        downloadUrl = "https://host/book.fb2",
        targetSourceId = "source-1",
        targetRelativeDir = "",
        state = state,
        createdAt = 1L,
    )

    @Test
    fun add_catalog_saves_credentials_and_sends_them_right_away() = runTest {
        val result = repository().addCatalog("Закрытый", "https://host/opds", "reader", "s3cret")

        val catalog = result.getOrNull()
        assertThat(catalog).isNotNull()
        // Первый же запрос идёт с заголовком: иначе закрытый каталог не добавился бы вовсе.
        assertThat(http.lastCredentials)
            .isEqualTo(io.github.bookshelf.core.opds.auth.OpdsCredentials("reader", "s3cret"))
        // Секрет сохранён, а в строке каталога — только ссылка на него.
        assertThat(credentials.saved[catalog?.id])
            .isEqualTo(io.github.bookshelf.core.opds.auth.OpdsCredentials("reader", "s3cret"))
        // В строке каталога — только ссылка на секрет, самого пароля там нет.
        assertThat(catalogDao.rows.values.single().passwordAlias).isEqualTo(catalog?.id)
    }

    @Test
    fun add_catalog_without_credentials_stores_no_secret() = runTest {
        val result = repository().addCatalog("Флибуста", "https://host/opds", null, null)

        // Публичный каталог: пустая пара не должна превращаться в «сохранён пустой пароль».
        assertThat(http.lastCredentials).isNull()
        assertThat(credentials.saved).isEmpty()
        assertThat(catalogDao.rows.values.single().passwordAlias).isNull()
    }

    @Test
    fun add_catalog_reports_a_secret_that_could_not_be_saved() = runTest {
        credentials.failSave = true

        val result = repository().addCatalog("Закрытый", "https://host/opds", "reader", "s3cret")

        // Каталог не должен появиться со ссылкой на несуществующий пароль: открытие отвечало бы 401.
        assertThat(result.isFailure).isTrue()
        assertThat(catalogDao.rows).isEmpty()
    }

    @Test
    fun browse_sends_the_saved_credentials() = runTest {
        val catalog = repository().addCatalog("Закрытый", "https://host/opds", "reader", "s3cret")
            .getOrNull()
        requireNotNull(catalog)
        http.lastCredentials = null

        repository().browse(catalog.id, null)

        assertThat(http.lastCredentials)
            .isEqualTo(io.github.bookshelf.core.opds.auth.OpdsCredentials("reader", "s3cret"))
    }

    @Test
    fun browse_reports_a_broken_secret_instead_of_going_unauthenticated() = runTest {
        val catalog = repository().addCatalog("Закрытый", "https://host/opds", "reader", "s3cret")
            .getOrNull()
        requireNotNull(catalog)
        credentials.failLoad = true

        val result = repository().browse(catalog.id, null)

        // Молчаливый запрос без заголовка дал бы 401 без объяснений.
        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun remove_catalog_deletes_the_secret() = runTest {
        val catalog = repository().addCatalog("Закрытый", "https://host/opds", "reader", "s3cret")
            .getOrNull()
        requireNotNull(catalog)

        repository().removeCatalog(catalog.id)

        assertThat(credentials.saved).isEmpty()
        assertThat(catalogDao.rows).isEmpty()
    }

    @Test
    fun remove_catalog_keeps_the_catalog_when_the_secret_cannot_be_deleted() = runTest {
        val catalog = repository().addCatalog("Закрытый", "https://host/opds", "reader", "s3cret")
            .getOrNull()
        requireNotNull(catalog)
        credentials.failDelete = true

        val result = repository().removeCatalog(catalog.id)

        // Иначе пароль остался бы в хранилище без владельца, и убрать его было бы нечем.
        assertThat(result.isFailure).isTrue()
        assertThat(catalogDao.rows).hasSize(1)
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

    @Test
    fun browse_page_carries_search_template_and_effective_url() = runTest {
        catalogDao.rows["c1"] = entity("c1")
        parser.searchTemplate = SEARCH_TEMPLATE

        val page = repository().browsePage("c1", url = "https://host/opds/new").getOrNull()

        // Шаблон нужен экрану, чтобы знать, умеет ли каталог искать; адрес — карточке записи,
        // чтобы перечитать именно эту страницу фида.
        assertThat(page?.searchUrl).isEqualTo(SEARCH_TEMPLATE)
        assertThat(page?.url).isEqualTo("https://host/opds/new")
    }

    @Test
    fun search_substitutes_the_query_into_the_template() = runTest {
        catalogDao.rows["c1"] = entity("c1")

        val page = repository().search("c1", "Лукьяненко", SEARCH_TEMPLATE).getOrNull()

        assertThat(page?.entries).hasSize(1)
        // Запрос обязан уехать в адрес закодированным: с литералом {searchTerms} сервер вернул бы
        // пустой список, и поиск выглядел бы «не работает» на каталоге, который его поддерживает.
        assertThat(http.lastUrl).isEqualTo(
            "https://host/opds/search?searchTerm=%D0%9B%D1%83%D0%BA%D1%8C%D1%8F%D0%BD%D0%B5%D0%BD%D0%BA%D0%BE",
        )
        assertThat(http.lastUrl).doesNotContain("{searchTerms}")
    }

    @Test
    fun search_without_template_is_reported_as_unsupported() = runTest {
        catalogDao.rows["c1"] = entity("c1")
        // Объявление поиска без пригодного шаблона: подставлять запрос некуда, и врать «ничего не
        // найдено» нельзя — экран по этой ошибке перейдёт к поиску по загруженному фиду.
        http.descriptorBody = "<OpenSearchDescription><ShortName>Без поиска</ShortName></OpenSearchDescription>"

        val result = repository().search("c1", "Лем", DESCRIPTOR_URL)

        assertThat(result.isFailure).isTrue()
        // За объявлением сходили один раз, но самого поискового запроса не было.
        assertThat(http.calls).isEqualTo(1)
        assertThat(http.lastUrl).isEqualTo(DESCRIPTOR_URL)
    }

    @Test
    fun search_with_blank_query_does_not_go_to_network() = runTest {
        catalogDao.rows["c1"] = entity("c1")

        assertThat(repository().search("c1", "   ", SEARCH_TEMPLATE).isFailure).isTrue()
        assertThat(http.calls).isEqualTo(0)
    }

    @Test
    fun search_resolves_the_template_from_the_opensearch_description() = runTest {
        catalogDao.rows["c1"] = entity("c1")
        http.descriptorBody = DESCRIPTOR_BODY

        val page = repository().search("c1", "Dickens", DESCRIPTOR_URL).getOrNull()

        // У Gutenberg в фиде только ссылка на дескриптор: без его разбора поиск не заработал бы,
        // хотя каталог его поддерживает. Взят шаблон Atom, а не первый text/html.
        assertThat(page).isNotNull()
        assertThat(http.lastUrl).isEqualTo("http://m.host/ebooks/search.opds/?query=Dickens")
    }

    @Test
    fun search_fetches_the_description_only_once() = runTest {
        catalogDao.rows["c1"] = entity("c1")
        http.descriptorBody = DESCRIPTOR_BODY
        val repository = repository()

        repository.search("c1", "Dickens", DESCRIPTOR_URL)
        repository.search("c1", "Verne", DESCRIPTOR_URL)

        // Дескриптор запрошен один раз, дальше шаблон берётся из памяти: иначе каждый поиск
        // начинался бы с лишнего сетевого захода.
        assertThat(http.calls).isEqualTo(3)
        assertThat(http.lastUrl).isEqualTo("http://m.host/ebooks/search.opds/?query=Verne")
    }

    @Test
    fun entry_metadata_reaches_the_book_card() = runTest {
        catalogDao.rows["c1"] = entity("c1")
        cache.stored = CachedFeed(feed = feedFixture(), fetchedAt = FIXED_NOW, etag = null, lastModified = null)

        val entry = repository().browse("c1", url = null).getOrNull().orEmpty().first()

        // Всё, что нужно карточке книги: описание, серия с адресом её фида, фид автора, язык,
        // год, жанры, все форматы и размер выбранного файла.
        assertThat(entry.summary).isEqualTo("Корабль поколений уходит в полёт.")
        assertThat(entry.language).isEqualTo("ru")
        assertThat(entry.issued).isEqualTo("2026")
        assertThat(entry.seriesName).isEqualTo("Ксили")
        assertThat(entry.seriesUrl).isEqualTo("https://host/opds/sequencebooks/24147")
        assertThat(entry.authorUrl).isEqualTo("https://host/opds/author/750")
        assertThat(entry.genres).containsExactly("Космическая фантастика")
        // Форматы читаются человеком: «MOBI», а не «X-MOBIPOCKET-EBOOK».
        assertThat(entry.formats).containsExactly("FB2", "EPUB", "MOBI")
        assertThat(entry.expectedSize).isEqualTo(1_500_000L)
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
                // Метаданные карточки книги: всё это фид присылает, и всё это обязано доехать
                // до домена — иначе карточка показывает только автора и формат.
                summary = "Корабль поколений уходит в полёт.",
                language = "ru",
                issued = "2026",
                seriesName = "Ксили",
                seriesUrl = "https://host/opds/sequencebooks/24147",
                authorUrl = "https://host/opds/author/750",
                genres = listOf("Космическая фантастика"),
                coverUrl = "https://host/covers/1.jpg",
                links = listOf(
                    OpdsLink(rel = "http://opds-spec.org/acquisition/buy", href = "https://host/buy/1"),
                    OpdsLink(
                        rel = "http://opds-spec.org/acquisition/open-access",
                        href = "https://host/dl/1-open.fb2.zip",
                        type = "application/fb2+zip",
                        length = 1_500_000L,
                    ),
                    OpdsLink(
                        rel = "http://opds-spec.org/acquisition/open-access",
                        href = "https://host/dl/1-open.epub",
                        type = "application/epub+zip",
                    ),
                    OpdsLink(
                        rel = "http://opds-spec.org/acquisition/open-access",
                        href = "https://host/dl/1-open.mobi",
                        type = "application/x-mobipocket-ebook",
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

        /** Последний запрошенный адрес: по нему проверяется подстановка поискового запроса. */
        var lastUrl: String? = null

        /** Учётные данные последнего запроса: по ним проверяется авторизация каталога. */
        var lastCredentials: io.github.bookshelf.core.opds.auth.OpdsCredentials? = null

        /**
         * Тело дескриптора OpenSearch.
         *
         * Отдаётся по любому адресу: разбор фида в тестах заменён фейком, поэтому важно только то,
         * что репозиторий получает настоящий дескриптор и достаёт из него шаблон.
         */
        var descriptorBody: String? = null

        override suspend fun get(
            url: String,
            conditional: ConditionalGet?,
            credentials: io.github.bookshelf.core.opds.auth.OpdsCredentials?,
        ): AppResult<HttpResponse> {
            calls++
            lastUrl = url
            lastCredentials = credentials
            this.conditional = conditional
            failWith?.let { return AppResult.failure(it) }
            descriptorBody?.let { body ->
                return AppResult.success(HttpResponse(200, body, null, null, url))
            }
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

        /** Шаблон OpenSearch, который «объявил» фид; null — каталог поиск не поддерживает. */
        var searchTemplate: String? = null

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
                    searchUrl = searchTemplate,
                ),
            )
        }
    }

    @Test
    fun browse_takes_the_cache_age_from_settings() = runTest {
        catalogDao.rows["c1"] = entity("c1")
        settingsRepository.value = AppSettings(opds = OpdsSettings(feedCacheMaxAgeMinutes = 5))

        repository().browse("c1", url = "https://host/opds/new")

        // Пять минут из настройки, а не прежняя константа: приложение обязано делать то, что
        // показывает экран настроек.
        assertThat(cache.lastMaxAgeMillis).isEqualTo(5 * 60 * 1000L)
    }

    @Test
    fun browse_falls_back_to_the_documented_default_when_settings_are_unreadable() = runTest {
        catalogDao.rows["c1"] = entity("c1")
        settingsRepository.failure = IllegalStateException("настройки недоступны")

        repository().browse("c1", url = "https://host/opds/new")

        // Каталог обязан открываться и при недоступном хранилище настроек: тогда действует
        // документированное умолчание (60 минут), а не мгновенно устаревший кэш или отказ.
        assertThat(cache.lastMaxAgeMillis).isEqualTo(60 * 60 * 1000L)
    }

    /** Настройки для теста: только OPDS-группа, значение задаётся полем. */
    private class FakeSettingsRepository : SettingsRepository {
        var value: AppSettings = AppSettings()
        var failure: Throwable? = null

        override val settings: Flow<AppSettings> = flowOf(value)

        override suspend fun current(): AppSettings {
            failure?.let { throw it }
            return value
        }

        override suspend fun update(transform: (AppSettings) -> AppSettings) {
            value = transform(value)
        }

        override suspend fun resetToDefaults() = Unit
    }

    private class FakeCache : OpdsFeedCache {
        var stored: CachedFeed? = null
        var saved: CachedFeed? = null

        /** Возраст кэша, который запросил репозиторий: проверяется связь с настройкой. */
        var lastMaxAgeMillis: Long? = null

        override suspend fun get(urlHash: String, maxAgeMillis: Long): AppResult<CachedFeed?> {
            lastMaxAgeMillis = maxAgeMillis
            return AppResult.success(stored)
        }

        override suspend fun put(feed: OpdsFeed, etag: String?, lastModified: String?) {
            saved = CachedFeed(feed, FIXED_NOW, etag, lastModified)
        }
    }

    /** Подставное хранилище паролей: проверяется проводка, а не шифрование (оно в `:core:opds`). */
    private class FakeCredentialStore : io.github.bookshelf.core.opds.auth.CredentialStore {

        val saved = linkedMapOf<String, io.github.bookshelf.core.opds.auth.OpdsCredentials>()
        var failSave = false
        var failLoad = false
        var failDelete = false

        override suspend fun save(
            catalogId: String,
            credentials: io.github.bookshelf.core.opds.auth.OpdsCredentials,
        ): AppResult<Unit> {
            if (failSave) return AppResult.failure(AppError.Unknown(message = "ключ недоступен"))
            saved[catalogId] = credentials
            return AppResult.success(Unit)
        }

        override suspend fun load(catalogId: String): AppResult<io.github.bookshelf.core.opds.auth.OpdsCredentials?> {
            if (failLoad) return AppResult.failure(AppError.Unknown(message = "пароль повреждён"))
            return AppResult.success(saved[catalogId])
        }

        override suspend fun delete(catalogId: String): AppResult<Unit> {
            if (failDelete) return AppResult.failure(AppError.Unknown(message = "не удалось удалить"))
            saved.remove(catalogId)
            return AppResult.success(Unit)
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

        /** Очистка истории: терминальные записи убираются, активные остаются. */
        override suspend fun deleteFinished(): Int {
            val finished = rows.values.filter {
                it.state == OpdsDownloadState.DONE ||
                    it.state == OpdsDownloadState.FAILED ||
                    it.state == OpdsDownloadState.CANCELLED
            }
            finished.forEach { rows.remove(it.id) }
            return finished.size
        }
    }

    private companion object {
        const val FIXED_NOW = 1_000_000L

        /** Реальный вид ссылки Флибусты: маркер заменяется запросом пользователя. */
        const val SEARCH_TEMPLATE = "https://host/opds/search?searchTerm={searchTerms}"

        /** Адрес дескриптора OpenSearch: так его объявляет Project Gutenberg. */
        const val DESCRIPTOR_URL = "https://host/catalog/osd-books.xml"

        /**
         * Дескриптор OpenSearch в том виде, в каком его объявляет Project Gutenberg.
         *
         * Первым `<Url>` идёт `text/html`: если взять первый попавшийся шаблон, откроется
         * HTML-страница поиска вместо фида. Настоящий дескриптор Gutenberg разбирается отдельным
         * тестом в `:core:opds` — здесь проверяется поведение репозитория, а не парсер.
         */
        val DESCRIPTOR_BODY: String = """
            <?xml version="1.0" encoding="UTF-8"?>
            <OpenSearchDescription xmlns="http://a9.com/-/spec/opensearch/1.1/">
              <ShortName>Project Gutenberg</ShortName>
              <Url type="text/html" template="https://host/ebooks/search/?query={searchTerms}"/>
              <Url type="application/atom+xml"
                   template="http://m.host/ebooks/search.opds/?query={searchTerms}"/>
            </OpenSearchDescription>
        """.trimIndent()
    }
}
