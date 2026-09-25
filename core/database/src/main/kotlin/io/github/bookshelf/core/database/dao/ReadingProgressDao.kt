package io.github.bookshelf.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.github.bookshelf.core.database.entity.ReadingProgressEntity
import kotlinx.coroutines.flow.Flow

/**
 * Прогресс чтения.
 *
 * Заполняется только если читалка отдаёт позицию (AIDL FBReader) либо пользователь отметил
 * вручную; отсутствие прогресса — нормальное состояние (docs/reader-integration.md).
 */
@Dao
interface ReadingProgressDao {

    /** Записать или обновить прогресс книги. */
    @Upsert
    suspend fun upsert(progress: ReadingProgressEntity)

    /** Наблюдать прогресс книги — полка и карточка. */
    @Query("SELECT * FROM reading_progress WHERE bookId = :bookId")
    fun observeByBook(bookId: String): Flow<ReadingProgressEntity?>

    /** Прочитать прогресс книги. */
    @Query("SELECT * FROM reading_progress WHERE bookId = :bookId")
    suspend fun getByBook(bookId: String): ReadingProgressEntity?

    /** Сбросить прогресс (например, при «Отметить непрочитанным»). */
    @Query("DELETE FROM reading_progress WHERE bookId = :bookId")
    suspend fun deleteByBook(bookId: String)
}
