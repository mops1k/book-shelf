package io.github.bookshelf.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.github.bookshelf.core.database.entity.BookReaderOverrideEntity

/** Персональный выбор читалки для конкретной книги; приоритетнее дефолта формата. */
@Dao
interface BookReaderOverrideDao {

    /** Запомнить выбранную читалку для книги. */
    @Upsert
    suspend fun upsert(entry: BookReaderOverrideEntity)

    /** Читалка, выбранная для книги; `null` — используется дефолт формата. */
    @Query("SELECT * FROM book_reader_override WHERE bookId = :bookId")
    suspend fun getByBook(bookId: String): BookReaderOverrideEntity?

    /** Сбросить персональный выбор. */
    @Query("DELETE FROM book_reader_override WHERE bookId = :bookId")
    suspend fun deleteByBook(bookId: String)
}
