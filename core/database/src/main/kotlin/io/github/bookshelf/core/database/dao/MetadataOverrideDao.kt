package io.github.bookshelf.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.github.bookshelf.core.database.entity.MetadataOverrideEntity
import kotlinx.coroutines.flow.Flow

/**
 * Хранение ручных правок метаданных («Архитектура» §3.1).
 *
 * Строк на книгу ровно одна, поэтому `@Upsert` — это и есть «сохранить или заменить»: отдельного
 * `insert с ON CONFLICT` не требуется. `clear` нужен, чтобы пользователь мог вернуть книгу к
 * метаданным файла одной кнопкой («E-ink UX» §6.7).
 */
@Dao
interface MetadataOverrideDao {

    @Upsert
    suspend fun upsert(override: MetadataOverrideEntity)

    @Query("SELECT * FROM metadata_overrides WHERE bookId = :bookId")
    fun observe(bookId: String): Flow<MetadataOverrideEntity?>

    @Query("SELECT * FROM metadata_overrides WHERE bookId = :bookId")
    suspend fun getByBookId(bookId: String): MetadataOverrideEntity?

    /**
     * Ручные правки сразу по списку книг — одна выдача полки.
     *
     * Рядом с [getByBookId] именно ради числа запросов: полка читала правки по книге, и на окне
     * в 500 книг это давало 500 обращений к базе на каждое обновление таблицы `books`.
     * Пустой список вызывающий не передаёт: `IN ()` — синтаксическая ошибка SQLite.
     */
    @Query("SELECT * FROM metadata_overrides WHERE bookId IN (:bookIds)")
    suspend fun getOverridesForBooks(bookIds: List<String>): List<MetadataOverrideEntity>

    @Query("DELETE FROM metadata_overrides WHERE bookId = :bookId")
    suspend fun clear(bookId: String)

    @Query("SELECT * FROM metadata_overrides")
    suspend fun getAll(): List<MetadataOverrideEntity>
}
