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

    /** Найти тег по идентификатору (детерминированный id — «tag:» + имя в нижнем регистре). */
    @Query("SELECT * FROM tags WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): TagEntity?

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

    /**
     * Жанры/теги всей библиотеки одним запросом — выгрузка библиотеки в файл.
     *
     * По одной книге спрашивать нельзя по той же причине, что и у авторов: в файл попадает вся
     * библиотека. Значения — имена тегов, то есть те же коды жанров (`sf_space`), что лежат
     * в фильтрах и правилах умных полок.
     */
    @Query(
        """
        SELECT book_tags.bookId AS bookId, tags.name AS name
        FROM book_tags
        INNER JOIN tags ON tags.id = book_tags.tagId
        ORDER BY tags.name COLLATE NOCASE ASC
        """,
    )
    suspend fun getAllBookTags(): List<BookTagName>
}

/** Строка результата [TagDao.getAllBookTags]: книга и её жанр/тег. */
data class BookTagName(
    val bookId: String,
    val name: String,
)
