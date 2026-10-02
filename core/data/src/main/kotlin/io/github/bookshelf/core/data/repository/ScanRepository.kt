package io.github.bookshelf.core.data.repository

import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.data.model.ScanProgress
import io.github.bookshelf.core.data.model.ScanReport
import kotlinx.coroutines.flow.Flow

/**
 * Сканирование библиотеки («Архитектура» §4, ADR-010).
 *
 * Скан **прерываемый и возобновляемый**: курсор обхода хранится в `scan_runs`, работа идёт
 * порциями по времени, а не «пока не закончится». Это требование Android 15, где `dataSync`
 * ограничен 6 часами за 24 часа, и `Service.onTimeout` обязан приводить к `stopSelf()`.
 *
 * Мягкая отмена: уже найденное сохраняется, следующий запуск продолжает с курсора.
 */
interface ScanRepository {

    /**
     * Запустить (или возобновить) скан источника.
     *
     * @param restart заменить уже стоящую работу источника — так делает ручное пересканирование,
     *   чтобы запрос человека не терялся, пока сбойный прогон ждёт повтора.
     */
    suspend fun startScan(sourceId: String, restart: Boolean = false): AppResult<Unit>

    /** Наблюдать прогресс скана; эмитит не чаще раза в секунду («E-ink UX» §5). */
    fun observeProgress(sourceId: String): Flow<ScanProgress>

    /**
     * Поставить скан на паузу: остановиться и сохранить курсор.
     *
     * Именно пауза, а не отмена: прогон остаётся возобновляемым, и «Продолжить» возвращается к
     * тому же месту. Отмены сканирования в интерфейсе нет (решение Александра, 28.09.2026).
     */
    suspend fun pause(sourceId: String)

    /** История отчётов сканирования (экран «Сканирование и отчёт»). */
    fun observeReports(): Flow<List<ScanReport>>
}
