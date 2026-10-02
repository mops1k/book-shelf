package io.github.bookshelf.core.data.repository.impl

import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.common.error.DownloadFailure
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
import io.github.bookshelf.core.datastore.SettingsRepository
import io.github.bookshelf.core.database.dao.OpdsDownloadDao
import io.github.bookshelf.core.database.entity.OpdsCatalogEntity
import io.github.bookshelf.core.database.entity.OpdsDownloadEntity
import io.github.bookshelf.core.database.model.OpdsDownloadState
import io.github.bookshelf.core.opds.cache.OpdsFeedCache
import io.github.bookshelf.core.opds.client.ConditionalGet
import io.github.bookshelf.core.opds.auth.CredentialStore
import io.github.bookshelf.core.opds.auth.OpdsCredentials
import io.github.bookshelf.core.opds.cache.CachedFeed
import io.github.bookshelf.core.opds.client.OpdsHttpClient
import io.github.bookshelf.core.opds.model.OpdsFeed
import io.github.bookshelf.core.opds.model.OpdsLink
import io.github.bookshelf.core.opds.model.isAcquisition
import io.github.bookshelf.core.opds.model.isNavigation
import io.github.bookshelf.core.opds.parser.OpdsFeedParser
import io.github.bookshelf.core.opds.search.OpdsSearchDescription
import io.github.bookshelf.core.opds.search.OpdsSearchUrl
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
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
    /**
     * Возраст кэша фидов — настройка `opds.feedCacheMaxAgeMinutes`.
     *
     * До этого здесь стояла константа пять минут, а настройка обещала шестьдесят: пользователь
     * (и экспорт настроек) видел одно, а приложение делало другое.
     */
    private val settingsRepository: SettingsRepository,
    /**
     * Пароли каталогов.
     *
     * В базе лежит только `passwordAlias`: сам пароль — в шифрованном хранилище. Без этого
     * каталог с авторизацией нельзя было ни добавить, ни открыть, ни скачать из него книгу
     * (все запросы уходили без заголовка, а введённый пароль просто терялся).
     */
    private val credentialStore: CredentialStore,
) : OpdsRepository {

    /**
     * Учётные данные из формы добавления.
     *
     * `null`, если логин или пароль пусты: каталог без авторизации — обычный случай (Флибуста),
     * и пустая пара не должна превращаться в «сохранён пустой пароль».
     */
    private fun credentialsOf(username: String?, password: String?): OpdsCredentials? {
        val user = username?.trim().orEmpty()
        val secret = password.orEmpty()
        return if (user.isEmpty() || secret.isEmpty()) null else OpdsCredentials(user, secret)
    }

    /**
     * Уже разобранные дескрипторы OpenSearch: адрес дескриптора → шаблон поиска.
     *
     * Память, а не база: это производная от фида, и держать её дольше сессии незачем. Нужна,
     * чтобы каждый поиск не начинался с лишнего сетевого захода за дескриптором.
     */
    private val resolvedTemplates = ConcurrentHashMap<String, String>()

    override fun observeCatalogs(): Flow<List<OpdsCatalog>> =
        catalogDao.observeAll().map { rows: List<OpdsCatalogEntity> -> rows.map { it.toDomain() } }

    override suspend fun addCatalog(
        title: String,
        url: String,
        username: String?,
        password: String?,
    ): AppResult<OpdsCatalog> {
        // Учётные данные проверяются сразу: если каталог закрыт, человек должен узнать это при
        // добавлении, а не при каждом открытии.
        val entered = credentialsOf(username, password)
        val response = when (val got = http.get(url, conditional = null, credentials = entered)) {
            is AppResult.Failure -> return got
            is AppResult.Success -> got.value
        }
        val feed = when (val got = parser.parse(response.body, response.finalUrl)) {
            is AppResult.Failure -> return got
            is AppResult.Success -> got.value
        }
        val entityId = UUID.randomUUID().toString()
        val entity = OpdsCatalogEntity(
            id = entityId,
            // Название: что ввёл пользователь → что назвал фид → хост. Последнее нужно, потому
            // что у части каталогов нет заголовка, и пустое имя в списке выглядит как ошибка.
            title = title.ifBlank { feed.title.ifBlank { hostOf(response.finalUrl) } },
            url = response.finalUrl,
            username = username,
            // Ссылка на секрет: сам пароль в БД не попадает. Пишется только вместе с сохранением
            // секрета — «есть пароль» и «пароль сохранён» должны совпадать.
            passwordAlias = entered?.let { entityId },
            requiresAuth = !username.isNullOrBlank(),
            enabled = true,
            sortOrder = 0,
        )
        // Порядок обязателен: сначала секрет, потом строка каталога. Наоборот оставило бы каталог
        // со ссылкой на несуществующий пароль — открытие отвечало бы 401 без объяснений.
        entered?.let { secrets ->
            when (val saved = credentialStore.save(entityId, secrets)) {
                is AppResult.Failure -> return saved
                is AppResult.Success -> Unit
            }
        }
        return appRunCatching {
            catalogDao.upsertAll(listOf(entity))
            // Фид кладём в кэш сразу: первый же заход в каталог не должен ждать второй запрос.
            cache.put(feed, response.etag, response.lastModified)
        }.mapResult { entity.toDomain() }
    }

    override suspend fun updateCatalog(catalog: OpdsCatalog, password: String?): AppResult<Unit> {
        val secret = password?.takeIf { it.isNotBlank() }
        // Порядок как при добавлении: сначала секрет, потом строка каталога. Иначе каталог мог бы
        // сослаться на пароль, которого нет.
        if (secret != null) {
            val username = catalog.username
                ?: return AppResult.failure(
                    AppError.InvalidArgument(
                        what = "catalog",
                        message = "Для нового пароля нужен логин",
                    ),
                )
            when (val saved = credentialStore.save(catalog.id, OpdsCredentials(username, secret))) {
                is AppResult.Failure -> return saved
                is AppResult.Success -> Unit
            }
        }
        val entity = catalog.toEntity().let { row ->
            // Ссылка на секрет появляется только тогда, когда пароль действительно есть.
            if (secret != null) row.copy(passwordAlias = catalog.id) else row
        }
        return appRunCatching { catalogDao.upsertAll(listOf(entity)) }
    }

    override suspend fun removeCatalog(catalogId: String): AppResult<Unit> {
        // Секрет удаляется первым: если это не удалось, каталог лучше оставить — иначе пароль
        // остался бы в хранилище без владельца, и убрать его было бы уже нечем.
        when (val removed = credentialStore.delete(catalogId)) {
            is AppResult.Failure -> return removed
            is AppResult.Success -> Unit
        }
        return appRunCatching { catalogDao.deleteById(catalogId) }
    }

    override suspend fun browse(catalogId: String, url: String?): AppResult<List<OpdsEntry>> =
        browsePage(catalogId, url).mapResult { page -> page.entries }

    /**
     * Кэшированный фид для адреса, если он ещё свежий; `null` — кэша нет или он истёк.
     *
     * Вынесено из `browsePage` отдельной функцией: разбор `AppResult` в `when` добавлял ветки
     * к сложности, а правило «сбой кэша — это отсутствие кэша» читается здесь целиком.
     */
    private suspend fun cachedFeed(key: String, maxAgeMinutes: Int): CachedFeed? =
        when (val got = cache.get(key, maxAgeMinutes * MILLIS_PER_MINUTE)) {
            is AppResult.Failure -> null
            is AppResult.Success -> got.value
        }

    /**
     * Ответ «304 Not Modified»: страница собирается из кэша, а без кэша это ошибка разбора.
     *
     * Вынесено из `browsePage` отдельной функцией ради порога `CyclomaticComplexMethod`.
     */
    private fun notModifiedResult(cached: CachedFeed?, target: String): AppResult<OpdsPage> =
        cached?.let { AppResult.success(it.feed.toPage(target)) }
            ?: AppResult.failure(
                AppError.Parse(format = "opds", message = "Сервер не прислал фид, а кэша нет"),
            )

    override suspend fun browsePage(catalogId: String, url: String?): AppResult<OpdsPage> {
        val catalog = catalogDao.getById(catalogId)
            ?: return AppResult.failure(
                AppError.NotFound(what = "catalog", message = "Каталог не найден"),
            )
        val credentials = when (val loaded = credentialStore.load(catalogId)) {
            is AppResult.Failure -> return loaded
            is AppResult.Success -> loaded.value
        }
        val target = url ?: catalog.url
        val key = target.hashCode().toUInt().toString(HEX_RADIX)
        // Настройки недоступны — берём документированное умолчание: каталог должен открываться
        // даже тогда, когда файл настроек не читается.
        val maxAgeMinutes = runCatching { settingsRepository.current().opds.feedCacheMaxAgeMinutes }
            .getOrDefault(DEFAULT_CACHE_MAX_AGE_MINUTES)
        val cached = cachedFeed(key = key, maxAgeMinutes = maxAgeMinutes)

        // Корневой заход отдаётся из кэша: список должен открываться мгновенно, а не после сети.
        if (cached != null && url == null) return AppResult.success(cached.feed.toPage(target))

        val response = when (
            val got = http.get(
                target,
                cached?.let { ConditionalGet(it.etag, it.lastModified) },
                credentials,
            )
        ) {
            is AppResult.Failure -> return got
            is AppResult.Success -> got.value
        }
        if (response.status == HTTP_NOT_MODIFIED) return notModifiedResult(cached = cached, target = target)

        val feed = when (val got = parser.parse(response.body, response.finalUrl)) {
            is AppResult.Failure -> return got
            is AppResult.Success -> got.value
        }
        // Адрес берётся из ответа, а не из запроса: каталог мог увести редиректом на зеркало, и
        // тогда следующая страница и карточка записи должны ходить по фактическому адресу.
        val page = feed.toPage(response.finalUrl)
        val touched = catalog.touched(clock.nowMillis())
        return appRunCatching {
            cache.put(feed, response.etag, response.lastModified)
            catalogDao.upsertAll(listOf(touched))
        }.mapResult { page }
    }

    /**
     * Поиск по OpenSearch: запрос подставляется в шаблон, дальше это обычное открытие страницы.
     *
     * Отдельного пути по сети нет намеренно: результат поиска — такой же фид (со своей ссылкой
     * `rel="next"` и своим шаблоном поиска), и вся логика страниц остаётся в одном месте.
     */
    override suspend fun search(
        catalogId: String,
        query: String,
        searchTemplate: String,
    ): AppResult<OpdsPage> {
        if (query.isBlank()) {
            return AppResult.failure(
                AppError.InvalidArgument(what = "query", message = "Введите запрос для поиска"),
            )
        }
        val credentials = when (val loaded = credentialStore.load(catalogId)) {
            is AppResult.Failure -> return loaded
            is AppResult.Success -> loaded.value
        }
        val template = resolveTemplate(searchTemplate, credentials)
            ?: return AppResult.failure(
                AppError.Unsupported(
                    what = "opds-search",
                    message = "Каталог не объявляет шаблон поиска OpenSearch",
                ),
            )
        val url = OpdsSearchUrl.build(template, query)
            ?: return AppResult.failure(
                AppError.Unsupported(
                    what = "opds-search",
                    message = "Шаблон поиска каталога не содержит {searchTerms}",
                ),
            )
        return browsePage(catalogId, url)
    }

    /**
     * Шаблон поиска из того, что объявил каталог.
     *
     * Каталог объявляет поиск двумя способами: готовым шаблоном с `{searchTerms}` (Флибуста) или
     * дескриптором OpenSearch, внутри которого шаблон и лежит (Project Gutenberg). Разница видна
     * пользователю: без второго случая на Gutenberg поиск «не работает» на каталоге, который его
     * поддерживает.
     *
     * Дескриптор запрашивается один раз: результат кладётся в память, и повторный поиск по тому же
     * каталогу уже не ходит за ним. Ошибка загрузки — это `null`, а не исключение: вызывающий код
     * сообщит «каталог не объявляет шаблон поиска», и экран перейдёт к отбору по загруженному фиду.
     */
    private suspend fun resolveTemplate(hint: String, credentials: OpdsCredentials?): String? {
        if (OpdsSearchUrl.TEMPLATE_MARKER in hint) return hint
        resolvedTemplates[hint]?.let { return it }
        // Дескриптор OpenSearch лежит на том же сервере, что и каталог: на закрытом каталоге
        // запрос без заголовка вернул бы 401, и поиск «не работал бы» без причины.
        val response = when (val got = http.get(hint, conditional = null, credentials = credentials)) {
            is AppResult.Failure -> return null
            is AppResult.Success -> got.value
        }
        val template = OpdsSearchDescription.templateOf(response.body) ?: return null
        resolvedTemplates[hint] = template
        return template
    }

    /**
     * Фид парсера → страница домена: записи, ссылка «дальше» (`rel="next"`), шаблон поиска и адрес.
     */
    private fun OpdsFeed.toPage(url: String): OpdsPage = OpdsPage(
        entries = entries.map { entry -> entry.toDomain() },
        nextUrl = nextPageUrl,
        searchUrl = searchUrl,
        url = url,
    )

    override suspend fun clearDownloadHistory(): AppResult<Int> =
        appRunCatching { downloadDao.deleteFinished() }

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
        // Уже скачанное тоже не качаем заново. Проверка по записи — обычный случай; проверка по
        // адресу ловит ту же книгу в другом фиде каталога (у неё другой `entryId`, а файл тот же).
        downloadDao.findFinished(catalogId, entry.id)?.let { return AppResult.success(Unit) }
        downloadDao.findFinishedByUrl(url)?.let { return AppResult.success(Unit) }
        return appRunCatching {
            val download = OpdsDownloadEntity(
                    id = UUID.randomUUID().toString(),
                    catalogId = catalogId,
                    entryId = entry.id,
                    title = entry.title,
                    authorsText = entry.authorsText,
                    seriesName = entry.seriesName,
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
            // Размер берётся у выбранной ссылки: у Флибусты его нет, у Project Gutenberg он есть
            // атрибутом length — и тогда в карточке видно, сколько книга весит.
            expectedSize = chosen?.length,
            // Метаданные карточки книги. Раньше всё это терялось при маппинге, и карточка
            // показывала только автора и формат — «посмотреть, что за книга» было негде.
            summary = summary?.takeIf { it.isNotBlank() },
            language = language,
            issued = issued,
            updated = updated,
            seriesName = seriesName,
            seriesUrl = seriesUrl,
            authorUrl = authorUrl,
            // Форматы — все ссылки приобретения, а не только выбранная: у Флибусты их шесть
            // (fb2, epub, mobi, txt, rtf, html), и пользователь вправе видеть выбор.
            formats = links.filter { it.isAcquisition() }
                .mapNotNull { link -> link.type?.let(::formatLabel) }
                .distinct(),
            genres = genres,
            identifier = dcIdentifier,
        )
    }

    /**
     * Подпись формата по MIME.
     *
     * `substringAfter('/').uppercase()` даёт «X-MOBIPOCKET-EBOOK» и «FB2+ZIP» — в карточке это
     * читается как мусор, поэтому известные типы сводятся к привычным именам.
     */
    private fun formatLabel(mime: String): String {
        val subtype = mime.substringAfter('/', mime).lowercase()
        return when {
            subtype.startsWith("fb2") -> "FB2"
            subtype.startsWith("epub") -> "EPUB"
            subtype.startsWith("mobi") || subtype.contains("mobipocket") -> "MOBI"
            subtype.startsWith("pdf") -> "PDF"
            subtype.startsWith("rtf") -> "RTF"
            subtype.startsWith("html") -> "HTML"
            subtype.startsWith("txt") || subtype.startsWith("plain") -> "TXT"
            subtype.contains("comicbook+zip") -> "CBZ"
            subtype.contains("comicbook-rar") -> "CBR"
            else -> subtype.uppercase()
        }
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
        seriesName = seriesName,
        state = state.toDomain(),
        bytesDownloaded = bytesDownloaded,
        failure = downloadFailureFromName(failure),
        error = error,
        bookId = bookId,
        createdAt = createdAt,
    )

    /**
     * Код отказа из имени entity-значения; значение не из справочника даёт
     * [DownloadFailure.UNKNOWN] — строка очереди покажет общую причину, а не пустоту.
     */
    private fun downloadFailureFromName(name: String?): DownloadFailure? = when {
        name == null -> null
        else -> DownloadFailure.entries.firstOrNull { it.name == name } ?: DownloadFailure.UNKNOWN
    }

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

        /** Сколько миллисекунд в минуте: возраст кэша хранится настройкой в минутах. */
        const val MILLIS_PER_MINUTE = 60 * 1000L

        /** Возраст кэша по умолчанию, если настройки недоступны: как в `SettingsKeys.Defaults`. */
        const val DEFAULT_CACHE_MAX_AGE_MINUTES = 60
    }
}
