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
}
