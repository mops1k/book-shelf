package io.github.bookshelf.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import io.github.bookshelf.core.database.entity.AuthorEntity
import io.github.bookshelf.core.database.entity.BookAuthorCrossRef
import kotlinx.coroutines.flow.Flow

/** Авторы и связи «книга — автор». */
@Dao
interface AuthorDao {

    /** Пакетная вставка/обновление авторов, найденных при разборе метаданных. */
    @Upsert
    suspend fun upsertAll(authors: List<AuthorEntity>)

    /** Наблюдать всех авторов — список и фасетный фильтр. */
    @Query("SELECT * FROM authors ORDER BY sortName COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<AuthorEntity>>

    /** Прочитать автора по id. */
    @Query("SELECT * FROM authors WHERE id = :id")
    suspend fun getById(id: String): AuthorEntity?

    /** Найти автора по точному имени — дедупликация при импорте метаданных. */
    @Query("SELECT * FROM authors WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): AuthorEntity?

    /** Удалить автора. */
    @Query("DELETE FROM authors WHERE id = :id")
    suspend fun deleteById(id: String)

    /** Пересчитать денормализованный счётчик книг автора. */
    // STUB(Sprint 4): вызывать после массового импорта/удаления авторов из репозитория.
    @Query(
        """
        UPDATE authors
        SET bookCount = (
            SELECT COUNT(*) FROM book_authors WHERE book_authors.authorId = authors.id
        )
        """,
    )
    suspend fun refreshBookCounts()

    /** Привязать авторов к книге (порядок — через `position`). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookAuthors(refs: List<BookAuthorCrossRef>)

    /** Снять все связи книги; вызывается перед повторной записью авторов. */
    @Query("DELETE FROM book_authors WHERE bookId = :bookId")
    suspend fun deleteBookAuthors(bookId: String)

    /** Связи книги с авторами в порядке из файла. */
    @Query("SELECT * FROM book_authors WHERE bookId = :bookId ORDER BY position ASC")
    suspend fun getBookAuthors(bookId: String): List<BookAuthorCrossRef>
}
