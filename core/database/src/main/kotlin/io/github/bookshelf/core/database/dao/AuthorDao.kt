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

    /**
     * Прочитать авторов пачкой id — проверка существования перед записью связей.
     *
     * Нужна, чтобы не спрашивать базу по одному автору на книгу: разовый проход по существующей
     * библиотеке пишет авторов тысячами, и запрос на каждого превратился бы в тысячи обращений.
     */
    @Query("SELECT * FROM authors WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<AuthorEntity>

    /** Найти автора по точному имени — дедупликация при импорте метаданных. */
    @Query("SELECT * FROM authors WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): AuthorEntity?

    /** Удалить автора. */
    @Query("DELETE FROM authors WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * Пересчитать денормализованный счётчик книг у указанных авторов.
     *
     * Точечно, а не по всей таблице: правка авторов одной книги не должна переписывать строки
     * десятков тысяч чужих авторов (на каждую запись книги это был бы полный проход по `authors`).
     */
    @Query(
        """
        UPDATE authors
        SET bookCount = (
            SELECT COUNT(*) FROM book_authors WHERE book_authors.authorId = authors.id
        )
        WHERE id IN (:ids)
        """,
    )
    suspend fun refreshBookCounts(ids: List<String>)

    /** Привязать авторов к книге (порядок — через `position`). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookAuthors(refs: List<BookAuthorCrossRef>)

    /** Снять все связи книги; вызывается перед повторной записью авторов. */
    @Query("DELETE FROM book_authors WHERE bookId = :bookId")
    suspend fun deleteBookAuthors(bookId: String)

    /** Связи книги с авторами в порядке из файла. */
    @Query("SELECT * FROM book_authors WHERE bookId = :bookId ORDER BY position ASC")
    suspend fun getBookAuthors(bookId: String): List<BookAuthorCrossRef>

    /**
     * Книги, у которых авторы записаны строкой, но связей с авторами нет.
     *
     * Это условие разового прохода по существующей библиотеке: до перехода на связи авторское
     * измерение жило строкой `books.authorsText`, и `authors`/`book_authors` у собранных книг пусты.
     * Выборка по данным, а не флаг в настройках, делает проход идемпотентным: после него она пуста,
     * и повторный запуск (в том числе на каждом следующем старте) не пишет ничего. Книги без
     * авторов вовсе сюда не попадают — им связи не нужны.
     */
    @Query(
        """
        SELECT books.id FROM books
        LEFT JOIN book_authors ON book_authors.bookId = books.id
        WHERE book_authors.bookId IS NULL AND books.authorsText <> ''
        """,
    )
    suspend fun idsWithoutAuthors(): List<String>
}
