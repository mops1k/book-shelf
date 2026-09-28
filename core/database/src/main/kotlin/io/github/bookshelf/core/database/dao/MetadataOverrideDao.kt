package io.github.bookshelf.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.github.bookshelf.core.database.entity.MetadataOverrideEntity
import kotlinx.coroutines.flow.Flow

/**
 * Хранение ручных правок метаданных (docs/architecture.md §3.1).
 *
 * Строк на книгу ровно одна, поэтому `@Upsert` — это и есть «сохранить или заменить»: отдельного
 * `insert с ON CONFLICT` не требуется. `clear` нужен, чтобы пользователь мог вернуть книгу к
 * метаданным файла одной кнопкой (docs/eink-ux.md §6.7).
 */
@Dao
interface MetadataOverrideDao {

    @Upsert
    suspend fun upsert(override: MetadataOverrideEntity)

    @Query("SELECT * FROM metadata_overrides WHERE bookId = :bookId")
    fun observe(bookId: String): Flow<MetadataOverrideEntity?>

    @Query("SELECT * FROM metadata_overrides WHERE bookId = :bookId")
    suspend fun getByBookId(bookId: String): MetadataOverrideEntity?

    @Query("DELETE FROM metadata_overrides WHERE bookId = :bookId")
    suspend fun clear(bookId: String)

    @Query("SELECT * FROM metadata_overrides")
    suspend fun getAll(): List<MetadataOverrideEntity>
}
