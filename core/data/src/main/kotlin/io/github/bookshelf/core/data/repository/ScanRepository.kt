package io.github.bookshelf.core.data.repository

import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.data.model.ScanProgress
import io.github.bookshelf.core.data.model.ScanReport
import kotlinx.coroutines.flow.Flow

/**
 * Сканирование библиотеки (docs/architecture.md §4, ADR-010).
 *
 * Скан **прерываемый и возобновляемый**: курсор обхода хранится в `scan_runs`, работа идёт
 * порциями по времени, а не «пока не закончится». Это требование Android 15, где `dataSync`
 * ограничен 6 часами за 24 часа, и `Service.onTimeout` обязан приводить к `stopSelf()`.
 *
 * Мягкая отмена: уже найденное сохраняется, следующий запуск продолжает с курсора.
 */
interface ScanRepository {

    /** Запустить (или возобновить) скан источника. */
    suspend fun startScan(sourceId: String): AppResult<Unit>

    /** Наблюдать прогресс скана; эмитит не чаще раза в секунду (docs/eink-ux.md §5). */
    fun observeProgress(sourceId: String): Flow<ScanProgress>

    /** Мягкая отмена: остановиться и сохранить курсор. */
    suspend fun cancel(sourceId: String)

    /** История отчётов сканирования (экран «Сканирование и отчёт»). */
    fun observeReports(): Flow<List<ScanReport>>
}
