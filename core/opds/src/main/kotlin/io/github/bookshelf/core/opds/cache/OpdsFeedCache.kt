package io.github.bookshelf.core.opds.cache

import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.opds.model.OpdsFeed
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

/** Заглушка кэша фидов. */
@Singleton
class DefaultOpdsFeedCache @Inject constructor() : OpdsFeedCache {

    // STUB(Sprint 8): urlHash — хэш URL запроса; put вычисляет его из self-ссылки фида
    // (feed.links) и пишет строку в opds_feed_cache.

    override suspend fun get(urlHash: String, maxAgeMillis: Long): AppResult<CachedFeed?> =
        TODO("STUB(Sprint 8): прочитать opds_feed_cache с проверкой возраста")

    // STUB(Sprint 8): записать нормализованный JSON фида и заголовки conditional GET

    override suspend fun put(feed: OpdsFeed, etag: String?, lastModified: String?) {
        TODO("STUB(Sprint 8): записать фид в opds_feed_cache")
    }
}
