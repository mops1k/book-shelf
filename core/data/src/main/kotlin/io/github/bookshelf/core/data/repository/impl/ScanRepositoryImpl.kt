package io.github.bookshelf.core.data.repository.impl

import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.data.model.ScanProgress
import io.github.bookshelf.core.data.model.ScanReport
import io.github.bookshelf.core.data.repository.ScanRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Заглушка [ScanRepository].
 *
 * Что должна делать (Sprint 2):
 * * `startScan` — запустить `ScanSourceWorker` (уникальная работа по `sourceId`, `KEEP`);
 * * `observeProgress` — собирать прогресс из `scan_runs` + `WorkManager.setProgress`, троттлить
 *   эмиссии до 1 раза в секунду (docs/eink-ux.md §5);
 * * `cancel` — мягкая отмена: сохранить курсор обхода в `scan_runs` и остановить воркер;
 * * `observeReports` — история прогонов для экрана отчёта.
 *
 * Возобновляемость (курсор + обработка `Service.onTimeout` на Android 15) — отдельное требование
 * Sprint 2 (ADR-010), а не «приятный бонус».
 */
@Singleton
class ScanRepositoryImpl @Inject constructor() : ScanRepository {

    override suspend fun startScan(sourceId: String): AppResult<Unit> =
        AppResult.failure(AppError.Unsupported("STUB(Sprint 2): ScanRepository.startScan"))

    override fun observeProgress(sourceId: String): Flow<ScanProgress> = flowOf()

    override suspend fun cancel(sourceId: String) {
        // STUB(Sprint 2): остановить ScanSourceWorker и сохранить курсор обхода в scan_runs.
    }

    override fun observeReports(): Flow<List<ScanReport>> = flowOf(emptyList())
}
