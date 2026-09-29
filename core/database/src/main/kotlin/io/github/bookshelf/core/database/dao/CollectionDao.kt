package io.github.bookshelf.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import io.github.bookshelf.core.database.entity.CollectionBookCrossRef
import io.github.bookshelf.core.database.entity.CollectionEntity
import kotlinx.coroutines.flow.Flow

/** Коллекции (полки) и их состав. */
@Dao
interface CollectionDao {

    /** Пакетная вставка/обновление коллекций. */
    @Upsert
    suspend fun upsertAll(collections: List<CollectionEntity>)

    /** Наблюдать все коллекции в пользовательском порядке. */
    @Query("SELECT * FROM collections ORDER BY position ASC, name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<CollectionEntity>>

    /** Прочитать коллекцию по id. */
    @Query("SELECT * FROM collections WHERE id = :id")
    suspend fun getById(id: String): CollectionEntity?

    /** Удалить коллекцию; состав удаляется отдельным вызовом. */
    @Query("DELETE FROM collections WHERE id = :id")
    suspend fun deleteById(id: String)

    /** Добавить книги в коллекцию. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBooks(refs: List<CollectionBookCrossRef>)

    /** Убрать книгу из коллекции. */
    @Query("DELETE FROM collection_books WHERE collectionId = :collectionId AND bookId = :bookId")
    suspend fun removeBook(collectionId: String, bookId: String)

    /** Состав коллекции в пользовательском порядке. */
    @Query("SELECT * FROM collection_books WHERE collectionId = :collectionId ORDER BY position ASC")
    suspend fun getBooks(collectionId: String): List<CollectionBookCrossRef>

    /**
     * Сколько книг на каждой полке.
     *
     * Считается запросом, а не хранится в `collections`: отдельной колонки там нет вообще,
     * поэтому доменное поле `bookCount` всегда было нулём — полка показывала «0 книг», даже
     * когда книга только что добавлена. Считать по таблице связей дешевле, чем заводить
     * колонку, которую пришлось бы обновлять в каждом изменении состава.
     */
    @Query("SELECT collectionId, COUNT(*) AS bookCount FROM collection_books GROUP BY collectionId")
    fun observeBookCounts(): Flow<List<CollectionBookCount>>

    /**
     * Состав полки наблюдаемыми.
     *
     * `suspend getBooks` отдаёт снимок: после добавления или удаления книги подписка вернула бы
     * прежний список, и экран обновлялся бы только при пересоздании ViewModel.
     */
    @Query("SELECT * FROM collection_books WHERE collectionId = :collectionId ORDER BY position ASC")
    fun getBooksFlow(collectionId: String): Flow<List<CollectionBookCrossRef>>

    /**
     * Обложки первых книг полки — для строки списка полок.
     *
     * Книги без обложки пропускаются, а не занимают место в списке: строка показывает то,
     * что можно нарисовать. Порядок — пользовательский, как и у состава полки.
     */
    @Query(
        "SELECT books.coverPath FROM collection_books " +
            "JOIN books ON books.id = collection_books.bookId " +
            "WHERE collection_books.collectionId = :collectionId AND books.coverPath IS NOT NULL " +
            "ORDER BY collection_books.position ASC LIMIT :limit",
    )
    fun observePreviewCovers(collectionId: String, limit: Int): Flow<List<String>>
}

/** Строка результата [CollectionDao.observeBookCounts]: полка и число книг на ней. */
data class CollectionBookCount(
    val collectionId: String,
    val bookCount: Int,
)
