package io.github.bookshelf.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.github.bookshelf.core.database.entity.ReaderProfileEntity
import kotlinx.coroutines.flow.Flow

/** Профили внешних читалок (docs/reader-integration.md). */
@Dao
interface ReaderProfileDao {

    /** Пакетная вставка/обновление профилей: presets синхронизируются при старте. */
    @Upsert
    suspend fun upsertAll(profiles: List<ReaderProfileEntity>)

    /** Наблюдать все профили: сначала более приоритетные. */
    @Query("SELECT * FROM reader_profiles ORDER BY priority DESC, name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<ReaderProfileEntity>>

    /** Наблюдать только включённые профили — список выбора читалки. */
    @Query("SELECT * FROM reader_profiles WHERE enabled = 1 ORDER BY priority DESC, name COLLATE NOCASE ASC")
    fun observeEnabled(): Flow<List<ReaderProfileEntity>>

    /** Прочитать профиль по id. */
    @Query("SELECT * FROM reader_profiles WHERE id = :id")
    suspend fun getById(id: String): ReaderProfileEntity?

    /** Удалить пользовательский профиль. */
    @Query("DELETE FROM reader_profiles WHERE id = :id")
    suspend fun deleteById(id: String)
}
