package io.github.bookshelf.core.testing.fake

import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.data.model.ScanProgress
import io.github.bookshelf.core.data.model.ScanReport
import io.github.bookshelf.core.data.model.ScanStatus
import io.github.bookshelf.core.data.repository.ScanRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Фейк запуска скана для тестов.
 *
 * По правилу ADR-012 свои интерфейсы в тестах не мокаются: здесь видно, что именно проверил тест —
 * какие источники просили сканировать и сколько раз. Живёт в `:core:testing` (см. [FakeSourceRepository]).
 */
class FakeScanRepository : ScanRepository {

    /** Источники, для которых запросили скан, в порядке обращений. */
    val started: MutableList<String> = mutableListOf()

    /** Источники, для которых запросили замену стоящей работы (ручное пересканирование). */
    val restarted: MutableList<String> = mutableListOf()

    /** Источники, для которых запросили паузу. */
    val paused: MutableList<String> = mutableListOf()

    /** Что вернуть на [startScan], если для источника нет особого ответа; по умолчанию успех. */
    var startResult: AppResult<Unit> = AppResult.success(Unit)

    /**
     * Особые ответы по источникам: недоступная папка отвечает отказом, остальные — успехом.
     *
     * Нужны там, где проверяется, что один сломанный источник не мешает остальным.
     */
    val startResults: MutableMap<String, AppResult<Unit>> = mutableMapOf()

    /** Прогресс по источникам: тест кладёт сюда то, что должен увидеть экран. */
    val progress: MutableMap<String, MutableStateFlow<ScanProgress>> = mutableMapOf()

    /** Отчёты о прогонах, которые увидит экран. */
    var reports: List<ScanReport> = emptyList()

    override suspend fun startScan(sourceId: String, restart: Boolean): AppResult<Unit> {
        started += sourceId
        if (restart) restarted += sourceId
        return startResults[sourceId] ?: startResult
    }

    override fun observeProgress(sourceId: String): Flow<ScanProgress> =
        progress.getOrPut(sourceId) {
            MutableStateFlow(ScanProgress(sourceId = sourceId, status = ScanStatus.IDLE))
        }

    override suspend fun pause(sourceId: String) {
        paused += sourceId
    }

    override fun observeReports(): Flow<List<ScanReport>> = flowOf(reports)
}
