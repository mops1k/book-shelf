package io.github.bookshelf.core.data.repository.impl

import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.common.error.appRunCatching
import io.github.bookshelf.core.common.time.AppClock
import io.github.bookshelf.core.data.model.DownloadState
import io.github.bookshelf.core.data.model.OpdsCatalog
import io.github.bookshelf.core.data.model.OpdsDownload
import io.github.bookshelf.core.data.model.OpdsEntry
import io.github.bookshelf.core.data.model.OpdsPage
import io.github.bookshelf.core.data.repository.OpdsRepository
import io.github.bookshelf.core.data.sync.DownloadScheduler
import io.github.bookshelf.core.database.dao.OpdsCatalogDao
import io.github.bookshelf.core.database.dao.OpdsDownloadDao
import io.github.bookshelf.core.database.entity.OpdsCatalogEntity
import io.github.bookshelf.core.database.entity.OpdsDownloadEntity
import io.github.bookshelf.core.database.model.OpdsDownloadState
import io.github.bookshelf.core.opds.cache.OpdsFeedCache
import io.github.bookshelf.core.opds.client.ConditionalGet
import io.github.bookshelf.core.opds.client.OpdsHttpClient
import io.github.bookshelf.core.opds.model.OpdsFeed
import io.github.bookshelf.core.opds.model.OpdsLink
import io.github.bookshelf.core.opds.model.isAcquisition
import io.github.bookshelf.core.opds.model.isNavigation
import io.github.bookshelf.core.opds.parser.OpdsFeedParser
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Каталоги OPDS (docs/opds.md).
 *
 * Три решения, которые иначе дали бы тихую поломку:
 *
 * **1. Каталог проверяется при добавлении.** Без проверки адрес сохранился бы, а пустой список
 * появлялся бы при каждом открытии: пользователь ввёл адрес, кажется — всё прошло, а книг нет.
 * Единственное место, где можно сказать «адрес неправильный», — момент ввода.
 *
 * **2. Открытие идёт «кэш → сеть», и сеть — условная.** Без `If-None-Match` каталог на 2000 книг
 * скачивался бы целиком при каждом заходе. Ответ 304 означает «покажи кэш», а не ошибку.
 *
 * **3. Ссылка для скачивания берётся из rel=acquisition.** У записи их несколько (купить,
 * одолжить, скачать) — берём ту, у которой `type` похож на книгу, иначе пользователю
 * предложат купить вместо того, чтобы скачать.
 */
@Singleton
class OpdsRepositoryImpl @Inject constructor(
    private val catalogDao: OpdsCatalogDao,
    private val http: OpdsHttpClient,
    private val parser: OpdsFeedParser,
    private val cache: OpdsFeedCache,
    private val downloadDao: OpdsDownloadDao,
    private val clock: AppClock,
    private val downloadScheduler: DownloadScheduler,
) : OpdsRepository {

    override fun observeCatalogs(): Flow<List<OpdsCatalog>> =
        catalogDao.observeAll().map { rows: List<OpdsCatalogEntity> -> rows.map { it.toDomain() } }

    override suspend fun addCatalog(
        title: String,
        url: String,
        username: String?,
        password: String?,
    ): AppResult<OpdsCatalog> {
        val response = when (val got = http.get(url, conditional = null, credentials = null)) {
            is AppResult.Failure -> return got
            is AppResult.Success -> got.value
        }
        val feed = when (val got = parser.parse(response.body, response.finalUrl)) {
            is AppResult.Failure -> return got
            is AppResult.Success -> got.value
        }
        val entity = OpdsCatalogEntity(
            id = UUID.randomUUID().toString(),
            // Название: что ввёл пользователь → что назвал фид → хост. Последнее нужно, потому
            // что у части каталогов нет заголовка, и пустое имя в списке выглядит как ошибка.
            title = title.ifBlank { feed.title.ifBlank { hostOf(response.finalUrl) } },
            url = response.finalUrl,
            username = username,
            requiresAuth = !username.isNullOrBlank(),
            enabled = true,
            sortOrder = 0,
        )
        return appRunCatching {
            catalogDao.upsertAll(listOf(entity))
            // Фид кладём в кэш сразу: первый же заход в каталог не должен ждать второй запрос.
            cache.put(feed, response.etag, response.lastModified)
        }.mapResult { entity.toDomain() }
    }

    override suspend fun updateCatalog(catalog: OpdsCatalog): AppResult<Unit> =
        appRunCatching { catalogDao.upsertAll(listOf(catalog.toEntity())) }

    override suspend fun removeCatalog(catalogId: String): AppResult<Unit> =
        appRunCatching { catalogDao.deleteById(catalogId) }

    override suspend fun browse(catalogId: String, url: String?): AppResult<List<OpdsEntry>> =
        browsePage(catalogId, url).mapResult { page -> page.entries }

    override suspend fun browsePage(catalogId: String, url: String?): AppResult<OpdsPage> {
        val catalog = catalogDao.getById(catalogId)
            ?: return AppResult.failure(
                AppError.NotFound(what = "catalog", message = "Каталог не найден"),
            )
        val target = url ?: catalog.url
        val key = target.hashCode().toUInt().toString(HEX_RADIX)
        val cached = when (val got = cache.get(key, CACHE_MAX_AGE_MILLIS)) {
            is AppResult.Failure -> null
            is AppResult.Success -> got.value
        }

        // Корневой заход отдаётся из кэша: список должен открываться мгновенно, а не после сети.
        if (cached != null && url == null) return AppResult.success(cached.feed.toPage())

        val response = when (
            val got = http.get(target, cached?.let { ConditionalGet(it.etag, it.lastModified) })
        ) {
            is AppResult.Failure -> return got
            is AppResult.Success -> got.value
        }
        if (response.status == HTTP_NOT_MODIFIED) {
            return cached?.let { AppResult.success(it.feed.toPage()) }
                ?: AppResult.failure(
                    AppError.Parse(format = "opds", message = "Сервер не прислал фид, а кэша нет"),
                )
        }

        val feed = when (val got = parser.parse(response.body, response.finalUrl)) {
            is AppResult.Failure -> return got
            is AppResult.Success -> got.value
        }
        val page = feed.toPage()
        val touched = catalog.touched(clock.nowMillis())
        return appRunCatching {
            cache.put(feed, response.etag, response.lastModified)
            catalogDao.upsertAll(listOf(touched))
        }.mapResult { page }
    }

    /** Фид парсера → страница домена: записи и ссылка «дальше» (`rel="next"`). */
    private fun OpdsFeed.toPage(): OpdsPage = OpdsPage(
        entries = entries.map { entry -> entry.toDomain() },
        nextUrl = nextPageUrl,
    )

    override fun observeDownloads(): Flow<List<OpdsDownload>> =
        downloadDao.observeAll().map { rows: List<OpdsDownloadEntity> -> rows.map { it.toDomain() } }

    /**
     * Скачивание без ссылки для скачивания бессмысленно: у записи может не быть ни одного
     * `rel=acquisition` (например, только платные), и тогда сказать пользователю нечего.
     */
    override suspend fun enqueueDownload(
        entry: OpdsEntry,
        catalogId: String,
        targetSourceId: String?,
        targetRelativeDir: String?,
    ): AppResult<Unit> {
        val url = entry.acquisitionUrl
            ?: return AppResult.failure(
                AppError.InvalidArgument(what = "entry", message = "У записи нет ссылки для скачивания"),
            )
        val sourceId = targetSourceId
            ?: return AppResult.failure(
                AppError.InvalidArgument(
                    what = "targetSourceId",
                    message = "Выберите папку для сохранения книги",
                ),
            )
        // Повторное нажатие «Скачать» не плодит записи: незавершённая загрузка той же книги
        // просто переиспользуется.
        downloadDao.findActive(catalogId, entry.id)?.let { existing ->
            return AppResult.success(Unit)
        }
        return appRunCatching {
            val download = OpdsDownloadEntity(
                    id = UUID.randomUUID().toString(),
                    catalogId = catalogId,
                    entryId = entry.id,
                    title = entry.title,
                    authorsText = entry.authorsText,
                    downloadUrl = url,
                    mimeType = entry.mimeType,
                    expectedSize = entry.expectedSize,
                    targetSourceId = sourceId,
                    targetRelativeDir = targetRelativeDir.orEmpty(),
                    state = OpdsDownloadState.QUEUED,
                    createdAt = clock.nowMillis(),
                )
            downloadDao.upsert(download)
            // Без этого работа не стартует: строка в очереди оставалась мёртвой, и пользователь
            // видел «в очереди» вечно. Планировщик — единственное место, где появляется воркер.
            downloadScheduler.schedule(download.id)
        }
    }

    override suspend fun cancelDownload(downloadId: String): AppResult<Unit> {
        val existing = downloadDao.getById(downloadId)
            ?: return AppResult.failure(
                AppError.NotFound(what = "download", message = "Загрузка не найдена"),
            )
        return appRunCatching { downloadDao.upsert(existing.copy(state = OpdsDownloadState.CANCELLED)) }
    }

    /**
     * Ссылка для скачивания: берём `rel=acquisition`, у которой тип — книга.
     *
     * Порядок не алфавитный: у одной записи бывает ссылка на покупку и ссылка на скачивание, и
     * если взять первую попавшуюся, пользователю предложат купить вместо скачивания.
     */
    private fun io.github.bookshelf.core.opds.model.OpdsEntry.toDomain(): OpdsEntry {
        val acquisition = links.firstOrNull { it.isAcquisition() }
        val openAccess = links.firstOrNull {
            it.isAcquisition() && it.rel.contains("open-access", ignoreCase = true)
        }
        val chosen = openAccess ?: acquisition
        return OpdsEntry(
            id = id,
            title = title,
            authorsText = authors.takeIf { it.isNotEmpty() }?.joinToString(", "),
            coverUrl = coverUrl,
            acquisitionUrl = chosen?.href,
            isSection = links.none { link -> link.isAcquisition() },
            sectionHref = links.firstOrNull { link -> link.isNavigation() }?.href,
            mimeType = chosen?.type ?: (acquisition as? OpdsLink)?.type,
            expectedSize = null,
        )
    }

    private fun OpdsCatalogEntity.toDomain() = OpdsCatalog(
        id = id,
        title = title,
        url = url,
        requiresAuth = requiresAuth,
        username = username,
        enabled = enabled,
        sortOrder = sortOrder,
        lastOpenedAt = lastOpenedAt,
    )

    /** Отметка «каталог открыт» вынесена наружу: внутри лямбды private-члены не резолвятся. */
    private fun OpdsCatalogEntity.touched(now: Long): OpdsCatalogEntity =
        copy(lastOpenedAt = now)

    private fun OpdsCatalog.toEntity() = OpdsCatalogEntity(
        id = id,
        title = title,
        url = url,
        kind = KIND,
        username = username,
        requiresAuth = requiresAuth,
        enabled = enabled,
        lastOpenedAt = lastOpenedAt,
        sortOrder = sortOrder,
    )

    private fun OpdsDownloadEntity.toDomain() = OpdsDownload(
        id = id,
        catalogId = catalogId,
        entryId = entryId,
        title = title,
        downloadUrl = downloadUrl,
        state = state.toDomain(),
        bytesDownloaded = bytesDownloaded,
        error = error,
        bookId = bookId,
        createdAt = createdAt,
    )

    /** Оба enum'а описывают одно и то же, но живут в разных модулях — сопоставляем по имени. */
    private fun OpdsDownloadState.toDomain(): DownloadState =
        DownloadState.entries.firstOrNull { it.name == name } ?: DownloadState.QUEUED

    /** Преобразование успеха при сохранении ошибки: у [AppResult] нет своего `map`. */
    private inline fun <T, R> AppResult<T>.mapResult(transform: (T) -> R): AppResult<R> = when (this) {
        is AppResult.Success -> AppResult.success(transform(value))
        is AppResult.Failure -> this
    }

    /** Хост без схемы и слеша: понятное имя, когда у фида нет заголовка. */
    private fun hostOf(url: String): String = runCatching {
        java.net.URI(url).host?.removePrefix("www.").orEmpty()
    }.getOrDefault("").ifBlank { url }

    private companion object {
        const val KIND = "OPDS_1_2"
        const val HTTP_NOT_MODIFIED = 304

        /** Основание системы счисления ключа URL: шестнадцатеричная строка компактнее. */
        const val HEX_RADIX = 16

        /** Пять минут: открытый с полки каталог не должен устаревать мгновенно. */
        const val CACHE_MAX_AGE_MILLIS = 5 * 60 * 1000L
    }
}
