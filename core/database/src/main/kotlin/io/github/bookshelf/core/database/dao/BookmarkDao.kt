package io.github.bookshelf.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.github.bookshelf.core.database.entity.BookmarkEntity
import kotlinx.coroutines.flow.Flow

/** Закладки, выделения и заметки (задел под встроенную читалку). */
@Dao
interface BookmarkDao {

    /** Добавить или обновить закладку. */
    @Upsert
    suspend fun upsert(bookmark: BookmarkEntity)

    /** Наблюдать закладки книги в порядке создания. */
    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY createdAt ASC")
    fun observeByBook(bookId: String): Flow<List<BookmarkEntity>>

    /** Число закладок книги — бейдж в карточке. */
    @Query("SELECT COUNT(*) FROM bookmarks WHERE bookId = :bookId")
    suspend fun countByBook(bookId: String): Int

    /** Удалить закладку. */
    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteById(id: String)
}
