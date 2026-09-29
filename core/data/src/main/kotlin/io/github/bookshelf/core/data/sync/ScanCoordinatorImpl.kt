package io.github.bookshelf.core.data.sync

import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.common.error.appRunCatching
import io.github.bookshelf.core.common.error.errorOrNull
import io.github.bookshelf.core.common.error.getOrNull
import io.github.bookshelf.core.common.error.isSuccess
import io.github.bookshelf.core.common.time.AppClock
import io.github.bookshelf.core.database.dao.ScanRunDao
import io.github.bookshelf.core.database.dao.SourceDao
import io.github.bookshelf.core.database.entity.ScanRunEntity
import io.github.bookshelf.core.database.model.ScanRunStatus
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Координатор прогонов сканирования поверх `scan_runs` (docs/architecture.md §4.5, ADR-010).
 *
 * Возобновляемость держится на данных, а не на памяти процесса: строка прогона и
 * `cursorDocumentId` переживают убийство процесса и Android-таймаут. Поэтому координатор сначала
 * фиксирует состояние в БД и только потом отдаёт команду [ScanScheduler] — так воркер всегда
 * находит прогон, который ему положено вести.
 */
@Singleton
class ScanCoordinatorImpl @Inject constructor(
    private val sourceDao: SourceDao,
    private val scanRunDao: ScanRunDao,
    private val scanScheduler: ScanScheduler,
    private val appClock: AppClock,
) : ScanCoordinator {

    /**
     * Новый прогон с нулевым курсором.
     *
     * Если незавершённый прогон уже есть, [start] не создаёт второй: передаёт управление в [resume],
     * чтобы уникальная работа и `scan_runs` не расходились. При сбое постановки новый прогон
     * переводится в `FAILED`, иначе интерфейс источника навсегда показывал бы «идёт сканирование»
     * для работы, которой в WorkManager нет.
     */
    @Suppress("ReturnCount")
    override suspend fun start(sourceId: String): AppResult<Unit> {
        val allowed = requireEnabledSource(sourceId)
        if (!allowed.isSuccess) return allowed

        val existing = appRunCatching { scanRunDao.findResumable(sourceId) }
        if (existing is AppResult.Failure) return AppResult.failure(existing.error)
        if (existing.getOrNull() != null) return resume(sourceId)

        val run = ScanRunEntity(
            id = UUID.randomUUID().toString(),
            sourceId = sourceId,
            startedAt = appClock.nowMillis(),
            status = ScanRunStatus.RUNNING,
        )
        val inserted = appRunCatching { scanRunDao.insert(run) }
        if (!inserted.isSuccess) return inserted

        val scheduled = appRunCatching { scanScheduler.enqueueScan(sourceId) }
        if (!scheduled.isSuccess) {
            appRunCatching {
                scanRunDao.finish(
                    id = run.id,
                    status = ScanRunStatus.FAILED,
                    finishedAt = appClock.nowMillis(),
                    error = scheduled.errorOrNull()?.message
                        ?: "Не удалось поставить сканирование в очередь",
                )
            }
            return scheduled
        }
        return AppResult.success(Unit)
    }

    /**
     * Продолжение прерванного прогона с сохранённого курсора.
     *
     * `PAUSED` снова становится `RUNNING` до постановки работы: воркер читает состояние при старте.
     * Если очередь недоступна, прежний статус восстанавливается, и следующая попытка [resume]
     * снова найдёт этот прогон.
     */
    @Suppress("ReturnCount")
    override suspend fun resume(sourceId: String): AppResult<Unit> {
        val allowed = requireEnabledSource(sourceId)
        if (!allowed.isSuccess) return allowed

        val found = appRunCatching { scanRunDao.findResumable(sourceId) }
        if (found is AppResult.Failure) return AppResult.failure(found.error)
        val run = found.getOrNull() ?: return start(sourceId)

        val markedRunning = appRunCatching {
            scanRunDao.finish(
                id = run.id,
                status = ScanRunStatus.RUNNING,
                finishedAt = null,
                error = null,
            )
        }
        if (!markedRunning.isSuccess) return markedRunning

        val scheduled = appRunCatching { scanScheduler.enqueueScan(sourceId) }
        if (!scheduled.isSuccess) {
            appRunCatching {
                scanRunDao.finish(
                    id = run.id,
                    status = run.status,
                    finishedAt = run.finishedAt,
                    error = run.error,
                )
            }
            return scheduled
        }
        return AppResult.success(Unit)
    }

    /**
     * Пауза.
     *
     * Статус становится `PAUSED`, а не `CANCELLED`: только `RUNNING`/`PAUSED` находит
     * `findResumable`, поэтому после отмены следующий запуск обязан продолжить с курсора, а не
     * начать обход заново. Команда очереди отправляется даже без найденного прогона — воркер мог
     * не успеть записать состояние до остановки.
     */
    override suspend fun pause(sourceId: String) {
        appRunCatching {
            val run = scanRunDao.findResumable(sourceId)
            if (run != null) {
                scanRunDao.finish(
                    id = run.id,
                    status = ScanRunStatus.PAUSED,
                    finishedAt = null,
                    error = null,
                )
            }
        }
        appRunCatching { scanScheduler.cancelScan(sourceId) }
    }

    /** Источник должен существовать и быть включён: выключенный источник не сканируется. */
    private suspend fun requireEnabledSource(sourceId: String): AppResult<Unit> {
        val found = appRunCatching { sourceDao.getById(sourceId) }
        if (found is AppResult.Failure) return found
        val source = found.getOrNull()
            ?: return AppResult.failure(
                AppError.NotFound(what = "source", message = "Источник $sourceId не найден"),
            )
        if (!source.enabled) {
            return AppResult.failure(
                AppError.InvalidArgument(what = "source", message = "Источник $sourceId отключён"),
            )
        }
        return AppResult.success(Unit)
    }
}
