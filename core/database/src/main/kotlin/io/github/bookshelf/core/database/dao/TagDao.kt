package io.github.bookshelf.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import io.github.bookshelf.core.database.entity.BookTagCrossRef
import io.github.bookshelf.core.database.entity.TagEntity
import kotlinx.coroutines.flow.Flow

/** Теги и связи «книга — тег». */
@Dao
interface TagDao {

    /** Пакетная вставка/обновление тегов. */
    @Upsert
    suspend fun upsertAll(tags: List<TagEntity>)

    /** Наблюдать все теги по алфавиту. */
    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<TagEntity>>

    /** Найти тег по точному имени. */
    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): TagEntity?

    /** Удалить тег. */
    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun deleteById(id: String)

    /** Привязать теги к книге. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookTags(refs: List<BookTagCrossRef>)

    /** Снять тег с книги. */
    @Query("DELETE FROM book_tags WHERE bookId = :bookId AND tagId = :tagId")
    suspend fun removeBookTag(bookId: String, tagId: String)

    /** Теги книги. */
    @Query("SELECT * FROM book_tags WHERE bookId = :bookId")
    suspend fun getBookTags(bookId: String): List<BookTagCrossRef>
}
