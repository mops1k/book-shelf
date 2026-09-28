package io.github.bookshelf.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import io.github.bookshelf.core.database.entity.ScanRunEntity
import io.github.bookshelf.core.database.model.ScanRunStatus
import kotlinx.coroutines.flow.Flow

/**
 * Прогоны сканирования.
 *
 * Главное требование: скан прерываемый и возобновляемый, поэтому курсор обхода живёт в БД,
 * а не в памяти процесса (docs/architecture.md §4.5).
 */
@Dao
interface ScanRunDao {

    /** Создать прогон в состоянии [ScanRunStatus.RUNNING]. */
    @Insert
    suspend fun insert(run: ScanRunEntity)

    /** Завершить прогон: статус, время окончания и текст ошибки. */
    @Query(
        """
        UPDATE scan_runs
        SET status = :status, finishedAt = :finishedAt, error = :error
        WHERE id = :id
        """,
    )
    suspend fun finish(
        id: String,
        status: ScanRunStatus,
        finishedAt: Long?,
        error: String?,
    )

    /**
     * Сохранить промежуточный прогресс и курсор обхода.
     *
     * Вызывается порциями по времени: после `onTimeout` или убийства процесса скан продолжается
     * с `cursorDocumentId` (docs/architecture.md §4.5).
     */
    @Query(
        """
        UPDATE scan_runs
        SET cursorDocumentId = :cursorDocumentId,
            filesSeen = :filesSeen,
            added = :added,
            updated = :updated,
            removed = :removed,
            failedCount = :failedCount
        WHERE id = :id
        """,
    )
    suspend fun updateProgress(
        id: String,
        cursorDocumentId: String?,
        filesSeen: Int,
        added: Int,
        updated: Int,
        removed: Int,
        failedCount: Int,
    )

    /** Прочитать прогон по id. */
    @Query("SELECT * FROM scan_runs WHERE id = :id")
    suspend fun getById(id: String): ScanRunEntity?

    /** Наблюдать последний прогон источника — экран прогресса и отчёт. */
    @Query("SELECT * FROM scan_runs WHERE sourceId = :sourceId ORDER BY startedAt DESC LIMIT 1")
    fun observeLastRun(sourceId: String): Flow<ScanRunEntity?>

    /**
     * Найти прерванный прогон, который можно продолжить.
     *
     * Сравнение со строковыми литералами корректно: статус хранится именем константы.
     */
    @Query(
        """
        SELECT * FROM scan_runs
        WHERE sourceId = :sourceId AND status IN ('RUNNING', 'PAUSED')
        ORDER BY startedAt DESC
        LIMIT 1
        """,
    )
    suspend fun findResumable(sourceId: String): ScanRunEntity?

    /**
     * Все прогоны, свежие сверху.
     *
     * Нужна истории отчётов: `observeLastRun` показывает только последний прогон источника,
     * а экран обязан видеть все завершённые. Room сам перевыдаёт поток при изменении таблицы,
     * поэтому отдельная подписка на инвалидацию не нужна.
     */
    @Query("SELECT * FROM scan_runs ORDER BY startedAt DESC")
    fun observeAllRuns(): Flow<List<ScanRunEntity>>

    /** Наблюдать все активные прогоны — индикатор в UI и foreground-уведомление. */
    @Query("SELECT * FROM scan_runs WHERE status = 'RUNNING' ORDER BY startedAt DESC")
    fun observeRunning(): Flow<List<ScanRunEntity>>
}
