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
        deleteBookAuthors(ids)
        deleteCollectionLinks(ids)
        deleteReadingProgress(ids)
        deleteReaderOverrides(ids)
        deleteBookmarks(ids)
        deleteCoverCacheEntries(ids)
        clearDownloadLinks(ids)
        deleteBooks(ids)
    }

    /**
     * Убрать из истории загрузок OPDS ссылку на удалённую книгу.
     *
     * Удаление книги физическое, поэтому ссылка обязана исчезнуть: иначе экран загрузок по
     * `bookId` рисует «открыть скачанную книгу» и открывает карточку книги, которой уже нет
     * (дефект Александра 02.10.2026). Саму запись загрузки не трогаем: это история о скачивании,
     * а не о книге, и `bookId` в схеме объявлен nullable ровно для такого случая.
     */
    @Query("UPDATE opds_downloads SET bookId = NULL WHERE bookId IN (:ids)")
    abstract suspend fun clearDownloadLinks(ids: List<String>)

    /** Удалить сами записи книг; `books_fts` очищает триггер `books_fts_delete`. */
    @Query("DELETE FROM books WHERE id IN (:ids)")
    abstract suspend fun deleteBooks(ids: List<String>)

    /**
     * Стереть всё, что читатель сказал о книге: оценку, избранное, статус чтения и дату добавления.
     *
     * Нужно, когда файл книги заменили и её считают новой книгой (`advanced.keepMetadataOnFileChange`
     * выключено). Одним запросом, а не четырьмя сеттерами: между ними книга успела бы побыть в
     * противоречивом состоянии. Живёт здесь, а не в `BookDao`: у того интерфейса уже порог detekt
     * по числу методов, а это — уборка данных, как и удаление книг, которое лежит рядом.
     */
    @Query(
        """
        UPDATE books SET rating = NULL, favorite = 0, readingStatus = :status,
            addedAt = :addedAt, updatedAt = :addedAt
        WHERE id = :id
        """,
    )
    abstract suspend fun resetUserData(id: String, status: String, addedAt: Long)

    /** Связи «книга — тег». */
    /**
     * Убрать связи «книга — автор».
     *
     * Внешнего ключа у `book_authors` нет (составной первичный ключ), поэтому `ON DELETE CASCADE`
     * её не чистит. Без этого шага после удаления книги оставались строки-сироты: на устройстве их
     * нашли 19 от 9 уже удалённых книг.
     */
    @Query("DELETE FROM book_authors WHERE bookId IN (:ids)")
    abstract suspend fun deleteBookAuthors(ids: List<String>)

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
