package io.github.bookshelf.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.github.bookshelf.core.database.entity.OpdsFeedCacheEntity

/** Кэш разобранных фидов OPDS: мгновенный возврат назад и офлайн-просмотр. */
@Dao
interface OpdsFeedCacheDao {

    /** Положить фид в кэш (запись перезаписывается по хешу URL). */
    @Upsert
    suspend fun upsert(entry: OpdsFeedCacheEntity)

    /** Прочитать кэш фида по хешу URL; `null` — кэша нет. */
    @Query("SELECT * FROM opds_feed_cache WHERE urlHash = :urlHash")
    suspend fun getByUrlHash(urlHash: String): OpdsFeedCacheEntity?

    /** Удалить запись кэша. */
    @Query("DELETE FROM opds_feed_cache WHERE urlHash = :urlHash")
    suspend fun deleteByUrlHash(urlHash: String)

    /** Удалить записи старше порога — периодическая чистка кэша. */
    @Query("DELETE FROM opds_feed_cache WHERE fetchedAt < :threshold")
    suspend fun deleteFetchedBefore(threshold: Long)

    /** Число записей кэша — диагностика в настройках. */
    @Query("SELECT COUNT(*) FROM opds_feed_cache")
    suspend fun countAll(): Int
}
