package io.github.bookshelf.core.data.sync

import io.github.bookshelf.core.common.error.AppResult

/**
 * Координатор сканирования: запуск, возобновление и отмена («Архитектура» §4.5, ADR-010).
 *
 * Скан обязан быть **прерываемым и возобновляемым**:
 * * курсор обхода (последний обработанный `documentId`/путь) сохраняется в `scan_runs`;
 * * работа идёт ограниченными порциями по времени, а не «до победного конца»;
 * * `Service.onTimeout(int, int)` на Android 15 (лимит `dataSync` — 6 часов за 24 часа) приводит
 *   к сохранению курсора и `stopSelf()`, а не к исключению;
 * * после убийства процесса или потери foreground-режима [resume] продолжает с сохранённого места;
 * * повторный запуск `dataSync` до сброса лимита бросает
 *   `ForegroundServiceStartNotAllowedException` — это тоже штатная ситуация, а не падение.
 *
 * Отличие [start] от [resume]: `start` начинает новый прогон (`scan_runs`), `resume` продолжает
 * незавершённый, если он есть, иначе ведёт себя как [start].
 */
interface ScanCoordinator {

    suspend fun start(sourceId: String): AppResult<Unit>

    suspend fun resume(sourceId: String): AppResult<Unit>

    /** Пауза: остановиться и сохранить курсор для последующего [resume]. */
    suspend fun pause(sourceId: String)
}
