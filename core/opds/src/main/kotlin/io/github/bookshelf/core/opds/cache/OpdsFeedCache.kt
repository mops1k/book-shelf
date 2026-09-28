package io.github.bookshelf.core.opds.cache

import io.github.bookshelf.core.common.di.OpdsCacheDir
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.common.time.AppClock
import io.github.bookshelf.core.database.dao.OpdsFeedCacheDao
import io.github.bookshelf.core.database.entity.OpdsFeedCacheEntity
import kotlinx.serialization.json.Json
import io.github.bookshelf.core.opds.model.OpdsFeed
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Кэш просмотренных фидов — таблица `opds_feed_cache` (docs/opds.md §2).
 *
 * Нужен для двух сценариев: мгновенный возврат назад по хлебным крошкам (без сети) и офлайн-просмотр
 * уже открытых каталогов. Хранится не сырое тело, а нормализованный JSON модели, поэтому кэш не
 * зависит от версии OPDS и от формата ответа.
 */
interface OpdsFeedCache {

    /**
     * Прочитать кэш.
     *
     * @param urlHash хэш URL фида — первичный ключ.
     * @param maxAgeMillis допустимый возраст; запись старше считается отсутствующей.
     * @return `Success(null)`, если кэша нет или он устарел.
     */
    suspend fun get(urlHash: String, maxAgeMillis: Long): AppResult<CachedFeed?>

    /** Сохранить фид вместе с заголовками conditional GET для следующего запроса. */
    suspend fun put(feed: OpdsFeed, etag: String?, lastModified: String?)
}

/**
 * Запись кэша.
 *
 * @param feed нормализованный фид.
 * @param fetchedAt время получения, мс от эпохи.
 * @param etag `ETag` ответа; null, если сервер его не отдал.
 * @param lastModified `Last-Modified` ответа; null, если сервер его не отдал.
 */
data class CachedFeed(
    val feed: OpdsFeed,
    val fetchedAt: Long,
    val etag: String?,
    val lastModified: String?,
)

/**
 * Кэш фидов на БД плюс дисковое тело.
 *
 * **Тело хранится файлом, а не в таблице.** При условном GET нужен весь ответ целиком: каталог
 * на 2000 книг — это мегабайты, и держать их в строке БД означало бы раздувать базу на каждый
 * открытый каталог. В таблице остаются только метаданные для проверки свежести.
 */
@Singleton
class DefaultOpdsFeedCache @Inject constructor(
    private val dao: OpdsFeedCacheDao,
    @OpdsCacheDir private val cacheDir: File,
    private val clock: AppClock,
) : OpdsFeedCache {

    override suspend fun get(urlHash: String, maxAgeMillis: Long): AppResult<CachedFeed?> {
        val row = dao.getByUrlHash(urlHash) ?: return AppResult.success(null)
        val age = clock.nowMillis() - row.fetchedAt
        if (age > maxAgeMillis) return AppResult.success(null)
        val body = fileFor(row.bodyPath).takeIf { it.isFile }?.readTextOrNull()
            ?: return AppResult.success(null)
        val feed = runCatching { json.decodeFromString(OpdsFeed.serializer(), body) }.getOrNull()
            ?: return AppResult.success(null)
        return AppResult.success(
            CachedFeed(
                feed = feed,
                fetchedAt = row.fetchedAt,
                etag = row.etag,
                lastModified = row.lastModified,
            ),
        )
    }

    override suspend fun put(feed: OpdsFeed, etag: String?, lastModified: String?) {
        // Ключ — адрес фида: self-ссылка у Flibusta есть, но полагаться только на неё нельзя,
        // часть каталогов её не отдаёт, и тогда записи не находились бы друг друга.
        val url = feed.links.firstOrNull { it.rel == "self" }?.href ?: feed.id
        val hash = url.hashKey()
        val file = fileFor(hash)
        val text = json.encodeToString(OpdsFeed.serializer(), feed)
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(file)) error("не удалось переименовать временный файл кэша")
        }.onFailure { return }
        dao.upsert(
            OpdsFeedCacheEntity(
                urlHash = hash,
                url = url,
                etag = etag,
                lastModified = lastModified,
                bodyPath = file.absolutePath,
                fetchedAt = clock.nowMillis(),
            ),
        )
    }

    private fun fileFor(path: String): File = if (path.startsWith("/")) File(path) else File(cacheDir, path)

    private fun File.readTextOrNull(): String? = runCatching { readText() }.getOrNull()

    private companion object {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        /** Основание системы счисления ключа URL: шестнадцатеричная строка компактнее. */
        const val HEX_RADIX = 16

        /** Стабильный ключ в пределах JVM; между запусками совпадение не требуется. */
        fun String.hashKey(): String = hashCode().toUInt().toString(HEX_RADIX)
    }
}
