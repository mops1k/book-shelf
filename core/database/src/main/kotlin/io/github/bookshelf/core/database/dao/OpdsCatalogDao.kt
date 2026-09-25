package io.github.bookshelf.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.github.bookshelf.core.database.entity.OpdsCatalogEntity
import kotlinx.coroutines.flow.Flow

/** Каталоги OPDS (docs/opds.md). */
@Dao
interface OpdsCatalogDao {

    /** Пакетная вставка/обновление каталогов. */
    @Upsert
    suspend fun upsertAll(catalogs: List<OpdsCatalogEntity>)

    /** Наблюдать каталоги в пользовательском порядке. */
    @Query("SELECT * FROM opds_catalogs ORDER BY sortOrder ASC, title COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<OpdsCatalogEntity>>

    /** Прочитать каталог по id. */
    @Query("SELECT * FROM opds_catalogs WHERE id = :id")
    suspend fun getById(id: String): OpdsCatalogEntity?

    /** Удалить каталог; записи кэша фидов чистятся отдельно. */
    @Query("DELETE FROM opds_catalogs WHERE id = :id")
    suspend fun deleteById(id: String)
}
