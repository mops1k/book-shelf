package io.github.bookshelf.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.github.bookshelf.core.database.entity.BookCopyEntity
import io.github.bookshelf.core.database.entity.BookEntity
import io.github.bookshelf.core.database.model.BookCopyWithTitle
import kotlinx.coroutines.flow.Flow

/**
 * Связи «файл-копия → книга, которую она повторяет» («Архитектура» §3.1).
 *
 * Отдельный DAO, а не методы в [BookDao]: у того порог detekt по числу функций уже на пределе, а
 * копии — своя сущность со своими запросами (скан, синхронизация прогресса, экран «Копии книг»).
 */
@Dao
interface BookCopyDao {

    /**
     * Книги того же размера, что и новый файл, — единственные, чьё содержимое может совпасть.
     *
     * Дешёвый отбор перед полным чтением: если размер уникален, совпадения быть не может, и файлы не
     * читаются вовсе. Порядок задаёт оригинал для одинаковых файлов: самая ранняя по дате добавления,
     * при равенстве — с меньшим путём (решение Александра 01.10.2026). Пустые файлы исключены: у них
     * нет содержимого, а нулевой размер совпадает у всех битых книг сразу.
     */
    @Query(
        "SELECT * FROM books WHERE sizeBytes = :sizeBytes AND sizeBytes > 0 " +
            "ORDER BY addedAt ASC, relativePath ASC",
    )
    suspend fun findBooksBySize(sizeBytes: Long): List<BookEntity>

    /**
     * Запомнить отпечаток содержимого книги.
     *
     * Нужно потому, что файл читается целиком: без записи отпечатка скан перечитывал бы ту же книгу
     * на каждом проходе ради того же результата.
     */
    @Query("UPDATE books SET contentHash = :contentHash WHERE id = :bookId")
    suspend fun rememberContentHash(bookId: String, contentHash: String)

    /** Копия по адресу документа: по нему её находит и скан, и синхронизация прогресса. */
    @Query("SELECT * FROM book_copies WHERE copyDocumentUri = :copyDocumentUri LIMIT 1")
    suspend fun findByUri(copyDocumentUri: String): BookCopyEntity?

    /** Записать или обновить связь: размер и время файла могли измениться. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(copy: BookCopyEntity)

    /** Копии, которые скан больше не видит в этом источнике: их файлов нет. */
    @Query("DELETE FROM book_copies WHERE sourceId = :sourceId AND copyDocumentUri NOT IN (:seenUris)")
    suspend fun deleteBySourceExcept(sourceId: String, seenUris: List<String>)

    /** Убрать связь по адресу: файл копии удалили (мы или человек). */
    @Query("DELETE FROM book_copies WHERE copyDocumentUri = :copyDocumentUri")
    suspend fun deleteByUri(copyDocumentUri: String)

    /** Все связи книги: нужны, когда книгу удаляют — вместе с ней уходят и её копии. */
    @Query("SELECT * FROM book_copies WHERE bookId = :bookId")
    suspend fun getByBookId(bookId: String): List<BookCopyEntity>

    /**
     * Копии вместе с названием книги: список показывает экран «Копии книг на устройстве».
     *
     * `LEFT JOIN`, а не `INNER`: связь без книги (её удалили, а каскад не сработал) должна быть видна
     * в списке, иначе такой файл останется на диске незамеченным.
     */
    @Query(
        "SELECT book_copies.copyDocumentUri AS copyDocumentUri, book_copies.bookId AS bookId, " +
            "COALESCE(books.title, '') AS title, COALESCE(books.authorsText, '') AS authorsText, " +
            "book_copies.sizeBytes AS sizeBytes " +
            "FROM book_copies LEFT JOIN books ON books.id = book_copies.bookId " +
            "ORDER BY book_copies.sizeBytes DESC",
    )
    suspend fun getAllWithTitles(): List<BookCopyWithTitle>

    /** То же для наблюдения: экран настроек не должен ждать ручного обновления. */
    @Query(
        "SELECT book_copies.copyDocumentUri AS copyDocumentUri, book_copies.bookId AS bookId, " +
            "COALESCE(books.title, '') AS title, COALESCE(books.authorsText, '') AS authorsText, " +
            "book_copies.sizeBytes AS sizeBytes " +
            "FROM book_copies LEFT JOIN books ON books.id = book_copies.bookId " +
            "ORDER BY book_copies.sizeBytes DESC",
    )
    fun observeAllWithTitles(): Flow<List<BookCopyWithTitle>>

    /** Все копии: список показывает экран «Копии книг на устройстве». */
    @Query("SELECT * FROM book_copies ORDER BY sizeBytes DESC")
    suspend fun getAll(): List<BookCopyEntity>

    /** Наблюдение за копиями: экран настроек не должен ждать ручного обновления. */
    @Query("SELECT * FROM book_copies ORDER BY sizeBytes DESC")
    fun observeAll(): Flow<List<BookCopyEntity>>
}
