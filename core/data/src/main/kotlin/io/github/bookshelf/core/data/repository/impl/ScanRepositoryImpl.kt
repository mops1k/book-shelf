package io.github.bookshelf.core.data.repository.impl

import androidx.room.InvalidationTracker
import io.github.bookshelf.core.common.coroutine.AppDispatchers
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.common.error.appRunCatching
import io.github.bookshelf.core.common.error.getOrNull
import io.github.bookshelf.core.data.model.ScanProgress
import io.github.bookshelf.core.data.model.ScanReport
import io.github.bookshelf.core.data.model.ScanStatus
import io.github.bookshelf.core.data.repository.ScanRepository
import io.github.bookshelf.core.data.sync.ScanScheduler
import io.github.bookshelf.core.database.BookshelfDatabase
import io.github.bookshelf.core.database.dao.ScanRunDao
import io.github.bookshelf.core.database.dao.SourceDao
import io.github.bookshelf.core.database.entity.ScanRunEntity
import io.github.bookshelf.core.database.model.ScanRunStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.sample

/**
 * Сканирование библиотеки: постановка уникальной работы, прогресс и история прогонов.
 *
 * Репозиторий намеренно не знает про WorkManager: `ScanSourceWorker` живёт в `:app`, а связь с ним
 * идёт через порт [ScanScheduler]. Поэтому постановку и отмену можно проверить подставным фейком,
 * не поднимая Android-инфраструктуру.
 *
 * Прогресс — это **проекция `scan_runs`**, а не состояние воркера в памяти: после убийства процесса
 * и при переходе на возобновляемый скан БД остаётся единственным источником истины (ADR-010).
 */
@Singleton
class ScanRepositoryImpl @Inject constructor(
    private val sourceDao: SourceDao,
    private val scanRunDao: ScanRunDao,
    private val scanScheduler: ScanScheduler,
    private val appDispatchers: AppDispatchers,
) : ScanRepository {

    /**
     * Запуск сканирования существующего включённого источника.
     *
     * Проверка до `enqueueScan` обязательна: `KEEP` принял бы запрос для несуществующей папки и тихо
     * ничего бы не сделал, а UI сообщил бы об успешном запуске.
     */
    override suspend fun startScan(sourceId: String, restart: Boolean): AppResult<Unit> {
        val found = appRunCatching { sourceDao.getById(sourceId) }
        if (found is AppResult.Failure) return found
        val source = found.getOrNull()
        ?: return AppResult.failure(AppError.NotFound(what = "source", message = "Источник не найден"))
        if (!source.enabled) {
            return AppResult.failure(
                AppError.InvalidArgument(what = "source", message = "Источник $sourceId отключён"),
            )
        }
        return appRunCatching { scanScheduler.enqueueScan(sourceId, restart) }
    }

    /**
     * Прогресс текущего либо последнего прогона источника.
     *
     * `throttleLatest` ограничивает UI не чаще раза в секунду: на e-ink частая перерисовка строки
     * «Сканирование… 412/1200» вызывает ghosting и расходует батарею («E-ink UX» §5).
     * `distinctUntilChanged` дополнительно отсекает повторные запросы Room с тем же значением.
     */
    override fun observeProgress(sourceId: String): Flow<ScanProgress> =
        combine(
            scanRunDao.observeRunning(),
            scanRunDao.observeLastRun(sourceId),
            sourceDao.observeBookCount(sourceId),
        ) { runningRuns, lastRun, totalKnown ->
            val run = runningRuns.firstOrNull { it.sourceId == sourceId } ?: lastRun
            run.toProgress(sourceId = sourceId, totalKnown = totalKnown)
        }
            .distinctUntilChanged()
            .sample(PROGRESS_EMIT_INTERVAL_MILLIS)

    /**
     * Пауза.
     *
     * Сначала resumable-прогон переводится в `PAUSED`, затем отправляется команда очереди. Так
     * состояние не зависит от того, успел ли воркер записать остановку до получения команды.
     * Возвращаемого значения у метода нет по контракту, поэтому исключения реализации
     * [ScanScheduler] превращаются в `AppResult` и не выходят наружу.
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

    /**
     * Полная история прогонов, свежие сверху.
     *
     * `ScanRunDao` умеет наблюдать только последний прогон источника, а экран отчёта обязан видеть
     * все завершённые. Поэтому здесь — узкое чтение терминальных строк `scan_runs` с подпиской на
     * инвалидацию Room. `RUNNING`/`PAUSED` остаются прогрессом, а не попадают в историю отчётов.
     * Контракт `:core:database` не меняется: запрос живёт в слое, который уже видит Room.
     */
    override fun observeReports(): Flow<List<ScanReport>> =
        scanRunDao.observeAllRuns()
            .map { runs -> runs.filter { it.status.isTerminal }.map { it.toReport() } }
            .flowOn(appDispatchers.io)
            .distinctUntilChanged()

    private companion object {
        /**
         * Период троттлинга прогресса, мс.
         *
         * На e-ink перерисовка кадра ощутима, поэтому прогресс не должен обновляться чаще: без
         * троттлинга быстрый скан обновлял бы экран десятки раз в секунду («E-ink UX» §5).
         */
        const val PROGRESS_EMIT_INTERVAL_MILLIS = 1000L
    }
}

/**
 * Прогон завершён окончательно, а не просто приостановлен.
 *
 * `RUNNING` и `PAUSED` — это текущее состояние работы, а не история: их место на экране
 * прогресса. Отчёт показывает только то, что уже не изменится.
 */
private val ScanRunStatus.isTerminal: Boolean
    get() = this == ScanRunStatus.COMPLETED ||
        this == ScanRunStatus.CANCELLED ||
        this == ScanRunStatus.FAILED

/** Терминальный прогон становится строкой отчёта. */
private fun ScanRunEntity.toReport(): ScanReport = ScanReport(
    runId = id,
    sourceId = sourceId,
    startedAt = startedAt,
    finishedAt = finishedAt,
    status = status.toDomain(),
    filesSeen = filesSeen,
    added = added,
    updated = updated,
    removed = removed,
    failedCount = failedCount,
    error = error,
)

/** Пустой прогон — корректное состояние экрана для источника, который ещё ни разу не сканировали. */
private fun ScanRunEntity?.toProgress(sourceId: String, totalKnown: Int): ScanProgress = ScanProgress(
    sourceId = sourceId,
    status = this?.status?.toDomain() ?: ScanStatus.IDLE,
    filesSeen = this?.filesSeen ?: 0,
    added = this?.added ?: 0,
    updated = this?.updated ?: 0,
    removed = this?.removed ?: 0,
    failed = this?.failedCount ?: 0,
    totalKnown = totalKnown,
    currentPath = this?.cursorDocumentId,
    startedAt = this?.startedAt ?: 0L,
    finishedAt = this?.finishedAt,
)

/** Константа БД и доменный статус расходятся намеренно: в БД прогон `COMPLETED`, в UI он завершён. */
private fun ScanRunStatus.toDomain(): ScanStatus = when (this) {
    ScanRunStatus.RUNNING -> ScanStatus.RUNNING
    ScanRunStatus.PAUSED -> ScanStatus.PAUSED
    ScanRunStatus.COMPLETED -> ScanStatus.FINISHED
    ScanRunStatus.CANCELLED -> ScanStatus.CANCELLED
    ScanRunStatus.FAILED -> ScanStatus.FAILED
}

