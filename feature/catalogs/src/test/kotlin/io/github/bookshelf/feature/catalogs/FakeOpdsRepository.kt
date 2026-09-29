package io.github.bookshelf.feature.catalogs

import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.data.model.OpdsCatalog
import io.github.bookshelf.core.data.model.OpdsDownload
import io.github.bookshelf.core.data.model.OpdsEntry
import io.github.bookshelf.core.data.model.OpdsPage
import io.github.bookshelf.core.data.model.Source
import io.github.bookshelf.core.data.repository.OpdsRepository
import io.github.bookshelf.core.data.repository.SourceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Адрес фида автора в фейке: так его объявляет Флибуста. */
internal const val AUTHOR_FEED_URL: String = "https://host/opds/author/750"

/** Адрес фида серии в фейке. */
internal const val SERIES_FEED_URL: String = "https://host/opds/sequences/24147"

/** Шаблон OpenSearch в том виде, в каком его объявляет Флибуста. */
internal const val SEARCH_TEMPLATE: String = "https://host/opds/search?searchTerm={searchTerms}"

/** Запись фида для тестов: по умолчанию книга без метаданных. */
internal fun entry(
    id: String,
    title: String,
    authors: String? = null,
    summary: String? = null,
    seriesName: String? = null,
    seriesUrl: String? = null,
    authorUrl: String? = null,
    formats: List<String> = emptyList(),
    expectedSize: Long? = null,
) = OpdsEntry(
    id = id,
    title = title,
    authorsText = authors,
    summary = summary,
    seriesName = seriesName,
    seriesUrl = seriesUrl,
    authorUrl = authorUrl,
    formats = formats,
    expectedSize = expectedSize,
    acquisitionUrl = "https://host/dl/$id.fb2.zip",
    mimeType = "application/fb2+zip",
)

/** Что именно ушло на сервер: запрос и шаблон, в который он подставлялся. */
internal data class SearchCall(val query: String, val template: String)

/**
 * Каталог в памяти: корневой фид, шаблон поиска и ответы по адресам фидов.
 *
 * Общий для тестов поиска и переходов из карточки: один и тот же фейк проверяет и «запрос ушёл на
 * сервер», и «фид автора открылся в браузере».
 */
internal class FakeOpdsRepository : OpdsRepository {

    var searchTemplate: String? = SEARCH_TEMPLATE

    /** Записи корневого фида. */
    var rootEntries: List<OpdsEntry> = listOf(entry("1", "Черновик"), entry("2", "Дозор"))

    /** Ответы на конкретные адреса фидов: адрес → записи. */
    val urlEntries = linkedMapOf<String, List<OpdsEntry>>()

    val searchCalls = mutableListOf<SearchCall>()

    /** Адреса, по которым репозиторий реально ходил за фидом. */
    val requestedUrls = mutableListOf<String?>()

    override fun observeCatalogs(): Flow<List<OpdsCatalog>> = flowOf(
        listOf(OpdsCatalog(id = CATALOG_ID, title = "Flibusta", url = "https://host/opds")),
    )

    override suspend fun addCatalog(
        title: String,
        url: String,
        username: String?,
        password: String?,
    ): AppResult<OpdsCatalog> = AppResult.failure(AppError.Unknown(message = "не нужно в тестах"))

    override suspend fun updateCatalog(catalog: OpdsCatalog): AppResult<Unit> = AppResult.success(Unit)

    override suspend fun removeCatalog(catalogId: String): AppResult<Unit> = AppResult.success(Unit)

    override suspend fun browse(catalogId: String, url: String?): AppResult<List<OpdsEntry>> =
        AppResult.success(entriesFor(url))

    override suspend fun browsePage(catalogId: String, url: String?): AppResult<OpdsPage> {
        requestedUrls += url
        return AppResult.success(
            OpdsPage(
                entries = entriesFor(url),
                searchUrl = searchTemplate,
                url = url ?: "https://host/opds",
            ),
        )
    }

    override suspend fun search(
        catalogId: String,
        query: String,
        searchTemplate: String,
    ): AppResult<OpdsPage> {
        searchCalls += SearchCall(query = query, template = searchTemplate)
        return AppResult.success(
            OpdsPage(
                entries = listOf(entry(id = "10", title = "Черновик")),
                url = "https://host/opds/search?searchTerm=$query",
            ),
        )
    }

    override fun observeDownloads(): Flow<List<OpdsDownload>> = flowOf(emptyList())

    override suspend fun enqueueDownload(
        entry: OpdsEntry,
        catalogId: String,
        targetSourceId: String?,
        targetRelativeDir: String?,
    ): AppResult<Unit> = AppResult.success(Unit)

    override suspend fun cancelDownload(downloadId: String): AppResult<Unit> = AppResult.success(Unit)

    /** Сколько раз просили очистить историю загрузок. */
    var clearHistoryCalls: Int = 0

    /** Сколько записей «убрала» очистка; по умолчанию одна. */
    var clearHistoryRemoved: Int = 1

    override suspend fun clearDownloadHistory(): AppResult<Int> {
        clearHistoryCalls++
        return AppResult.success(clearHistoryRemoved)
    }

    private fun entriesFor(url: String?): List<OpdsEntry> =
        if (url == null) rootEntries else urlEntries[url] ?: rootEntries

    companion object {
        const val CATALOG_ID = "c1"
    }
}

/** Источники в этих тестах не участвуют, но ViewModel требует настоящий интерфейс. */
internal object FakeSourceRepository : SourceRepository {
    override fun observeSources(): Flow<List<Source>> = flowOf(emptyList())

    override suspend fun addSafSource(uri: String, displayName: String): AppResult<Source> =
        AppResult.failure(AppError.Unknown(message = "не нужно в тестах"))

    override suspend fun removeSource(sourceId: String): AppResult<Unit> = AppResult.success(Unit)

    override suspend fun rescan(sourceId: String): AppResult<Unit> = AppResult.success(Unit)

        /** В тестах каталогов запись в папку не проверяется: право считается выданным. */
        override suspend fun hasWriteAccess(sourceId: String): Boolean = true
}
