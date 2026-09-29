package io.github.bookshelf.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction

/**
 * Удаление книг вместе со всем, что на них ссылается.
 *
 * Отдельный DAO, а не методы в [BookDao]: у `books` без малого полтора десятка зависимых таблиц,
 * и в схеме `ON DELETE CASCADE` объявлен только у `metadata_overrides`. У `collection_books`,
 * `book_tags`, `reading_progress`, `book_reader_override`, `bookmarks` и `cover_cache` внешних
 * ключей нет вовсе, поэтому удаление строки `books` в одиночку оставило бы висячие ссылки: полка
 * показывала бы несуществующие книги, а теги, прогресс и записи кэша копились бы мусором.
 *
 * Зачем это появилось: с автосинхронизацией библиотеки (28.09.2026) книги удаляются постоянно —
 * скан убирает то, чего больше нет на диске. Раньше книги только помечались «пропавшими», и
 * чистить связи было не нужно.
 */
@Dao
abstract class BookCleanupDao {

    /**
     * Удалить книги и все их связи одной транзакцией.
     *
     * Транзакция обязательна: половина удалённой книги (запись убрана, связи остались) — состояние,
     * из которого библиотека сама не выберется.
     *
     * ВНИМАНИЕ: пустой список передавать нельзя — `IN ()` недопустим в SQLite; метод сам выходит
     * на пустом списке, поэтому вызывающему проверять не нужно.
     */
    @Transaction
    open suspend fun deleteWithLinks(ids: List<String>) {
        if (ids.isEmpty()) return
        deleteBookTags(ids)
        deleteCollectionLinks(ids)
        deleteReadingProgress(ids)
        deleteReaderOverrides(ids)
        deleteBookmarks(ids)
        deleteCoverCacheEntries(ids)
        deleteBooks(ids)
    }

    /** Удалить сами записи книг; `books_fts` очищает триггер `books_fts_delete`. */
    @Query("DELETE FROM books WHERE id IN (:ids)")
    abstract suspend fun deleteBooks(ids: List<String>)

    /** Связи «книга — тег». */
    @Query("DELETE FROM book_tags WHERE bookId IN (:ids)")
    abstract suspend fun deleteBookTags(ids: List<String>)

    /** Связи «полка — книга». */
    @Query("DELETE FROM collection_books WHERE bookId IN (:ids)")
    abstract suspend fun deleteCollectionLinks(ids: List<String>)

    /** Прогресс чтения удалённой книги. */
    @Query("DELETE FROM reading_progress WHERE bookId IN (:ids)")
    abstract suspend fun deleteReadingProgress(ids: List<String>)

    /** Персональный выбор читалки для удалённой книги. */
    @Query("DELETE FROM book_reader_override WHERE bookId IN (:ids)")
    abstract suspend fun deleteReaderOverrides(ids: List<String>)

    /** Закладки и заметки удалённой книги. */
    @Query("DELETE FROM bookmarks WHERE bookId IN (:ids)")
    abstract suspend fun deleteBookmarks(ids: List<String>)

    /** Записи кэша обложек: файлы чистятся отдельно, в `:app` (там живёт `CoverCache`). */
    @Query("DELETE FROM cover_cache WHERE bookId IN (:ids)")
    abstract suspend fun deleteCoverCacheEntries(ids: List<String>)
}
