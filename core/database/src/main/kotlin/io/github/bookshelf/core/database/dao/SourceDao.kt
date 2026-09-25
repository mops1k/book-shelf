package io.github.bookshelf.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.github.bookshelf.core.database.entity.SourceEntity
import kotlinx.coroutines.flow.Flow

/** Источники библиотеки: добавление, включение/выключение, потеря доступа (docs/architecture.md §4.1). */
@Dao
interface SourceDao {

    /** Наблюдать все источники; порядок — по отображаемому имени. */
    @Query("SELECT * FROM sources ORDER BY displayName COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<SourceEntity>>

    /** Добавить источник; повторная вставка того же id перезаписывает запись. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(source: SourceEntity)

    /** Обновить источник (имя, флаги, счётчик документов). */
    @Update
    suspend fun update(source: SourceEntity)

    /** Удалить источник; книги при этом удаляются отдельным сценарием, а не каскадом. */
    @Query("DELETE FROM sources WHERE id = :id")
    suspend fun delete(id: String)

    /** Прочитать источник по id. */
    @Query("SELECT * FROM sources WHERE id = :id")
    suspend fun getById(id: String): SourceEntity?

    /**
     * Отметить, что persistable-разрешение потеряно (или восстановлено).
     *
     * Скан по такому источнику не падает: UI показывает «Папка недоступна — выбрать заново».
     */
    @Query("UPDATE sources SET accessLost = :accessLost WHERE id = :id")
    suspend fun updateAccessLost(id: String, accessLost: Boolean)

    /** Общее число источников — онбординг и пустые состояния. */
    @Query("SELECT COUNT(*) FROM sources")
    suspend fun countAll(): Int

    /** Наблюдать число книг источника — строка прогресса и настройки. */
    @Query("SELECT COUNT(*) FROM books WHERE sourceId = :sourceId")
    fun observeBookCount(sourceId: String): Flow<Int>
}
