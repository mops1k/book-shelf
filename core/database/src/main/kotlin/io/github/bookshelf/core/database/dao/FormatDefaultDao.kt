package io.github.bookshelf.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.github.bookshelf.core.database.entity.FormatDefaultEntity
import kotlinx.coroutines.flow.Flow

/** Читалка по умолчанию для формата. */
@Dao
interface FormatDefaultDao {

    /** Назначить или сменить читалку по умолчанию для формата. */
    @Upsert
    suspend fun upsert(entry: FormatDefaultEntity)

    /** Наблюдать все дефолты — экран «Читалки». */
    @Query("SELECT * FROM format_defaults ORDER BY format COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<FormatDefaultEntity>>

    /** Дефолт для формата; `null` — не задан. */
    @Query("SELECT * FROM format_defaults WHERE format = :format")
    suspend fun getByFormat(format: String): FormatDefaultEntity?

    /** Сбросить дефолт формата. */
    @Query("DELETE FROM format_defaults WHERE format = :format")
    suspend fun deleteByFormat(format: String)
}
