package io.github.bookshelf.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.github.bookshelf.core.database.entity.CoverCacheEntryEntity

/**
 * Лёгкая таблица учёта дискового кэша обложек для LRU-очистки
 * (docs/architecture.md §5). Сами файлы лежат в `filesDir/covers`.
 */
@Dao
interface CoverCacheEntryDao {

    /** Зарегистрировать файл обложки или обновить его размер. */
    @Upsert
    suspend fun upsert(entry: CoverCacheEntryEntity)

    /** Прочитать запись по книге; `null` — обложки в кэше нет. */
    @Query("SELECT * FROM cover_cache WHERE bookId = :bookId")
    suspend fun getByBookId(bookId: String): CoverCacheEntryEntity?

    /** Отметить обращение к обложке — основа LRU. */
    @Query("UPDATE cover_cache SET lastAccessedAt = :accessedAt WHERE bookId = :bookId")
    suspend fun touch(bookId: String, accessedAt: Long)

    /**
     * Кандидаты на удаление: самые давно не использованные обложки.
     *
     * Возвращает не более `limit` записей; решение об удалении принимает вызывающий
     * с учётом дискового лимита.
     */
    @Query("SELECT * FROM cover_cache ORDER BY lastAccessedAt ASC LIMIT :limit")
    suspend fun findLeastRecentlyUsed(limit: Int): List<CoverCacheEntryEntity>

    /**
     * Удалить учётные записи после стирания файлов.
     *
     * ВНИМАНИЕ: пустой список передавать нельзя — Room подставит `IN ()`, а SQLite не принимает
     * пустой `IN` (синтаксическая ошибка). Вызывающий обязан проверить `bookIds.isNotEmpty()`.
     */
    @Query("DELETE FROM cover_cache WHERE bookId IN (:bookIds)")
    suspend fun deleteByBookIds(bookIds: List<String>)

    /** Суммарный размер кэша обложек в байтах — строка «Занято кэшем» в настройках. */
    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM cover_cache")
    suspend fun totalSizeBytes(): Long
}
