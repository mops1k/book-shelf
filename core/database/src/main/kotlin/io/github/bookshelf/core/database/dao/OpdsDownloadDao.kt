package io.github.bookshelf.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.github.bookshelf.core.database.entity.OpdsDownloadEntity
import kotlinx.coroutines.flow.Flow

/** Загрузки OPDS (docs/opds.md §DownloadWorker). */
@Dao
interface OpdsDownloadDao {

    /** Создать или обновить запись загрузки. */
    @Upsert
    suspend fun upsert(download: OpdsDownloadEntity)

    /** Наблюдать все загрузки — экран «Загрузки». */
    @Query("SELECT * FROM opds_downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<OpdsDownloadEntity>>

    /** Наблюдать незавершённые загрузки (в очереди, активные и приостановленные). */
    @Query(
        """
        SELECT * FROM opds_downloads
        WHERE state IN ('QUEUED', 'RUNNING', 'PAUSED')
        ORDER BY createdAt ASC
        """,
    )
    fun observeActive(): Flow<List<OpdsDownloadEntity>>

    /** Прочитать загрузку по id. */
    @Query("SELECT * FROM opds_downloads WHERE id = :id")
    suspend fun getById(id: String): OpdsDownloadEntity?

    /**
     * Незавершённая загрузка той же книги того же каталога.
     *
     * Повторное нажатие «Скачать» раньше плодило по записи на нажатие: в таблице оказывалось
     * десяток одинаковых строк в очереди, и пользователь скачивал одну книгу несколько раз.
     */
    @Query(
        """
        SELECT * FROM opds_downloads
        WHERE catalogId = :catalogId AND entryId = :entryId
          AND state IN ('QUEUED', 'RUNNING', 'PAUSED')
        LIMIT 1
        """,
    )
    suspend fun findActive(catalogId: String, entryId: String): OpdsDownloadEntity?

    /** Удалить запись о загрузке. */
    @Query("DELETE FROM opds_downloads WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * Убрать историю: завершённые, сбойные и отменённые записи.
     *
     * Активные (QUEUED/RUNNING/PAUSED) остаются: их удаление оставило бы работающую загрузку без
     * записи — прогресс и результат было бы некуда писать. Возвращается число убранных строк, чтобы
     * экран сказал человеку, сколько записей убрано.
     */
    @Query("DELETE FROM opds_downloads WHERE state IN ('DONE', 'FAILED', 'CANCELLED')")
    suspend fun deleteFinished(): Int
}
