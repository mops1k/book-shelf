package io.github.bookshelf.core.common.coroutine

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * Боевые диспетчеры.
 *
 * Параллелизм парсеров фиксирован на уровне процесса: создавать новый пул на каждый скан нельзя
 * (на e-ink это лишние переключения контекста и расход батареи).
 *
 * STUB(Sprint 3): число потоков должно браться из настроек (`SettingsRepository.metadataParallelism`),
 * поэтому в Спринте 3 конструктор получит зависимость на чтение настройки, а `metadata` —
 * перестраиваемый диспетчер.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultAppDispatchers(
    parallelism: Int = DEFAULT_METADATA_PARALLELISM,
) : AppDispatchers, AutoCloseable {

    private val metadataExecutor = Executors.newFixedThreadPool(parallelism) { runnable ->
        Thread(runnable, "bookshelf-metadata").apply { isDaemon = true }
    }

    private val metadataDispatcher: CoroutineDispatcher = metadataExecutor.asCoroutineDispatcher()

    override val io: CoroutineDispatcher = Dispatchers.IO

    override val default: CoroutineDispatcher = Dispatchers.Default

    override val main: CoroutineDispatcher = Dispatchers.Main.immediate

    override val metadata: CoroutineDispatcher = metadataDispatcher.limitedParallelism(parallelism)

    override fun close() {
        metadataExecutor.shutdown()
    }

    companion object {
        /** На e-ink по умолчанию 2: берегём батарею и не мешаем UI. */
        const val DEFAULT_METADATA_PARALLELISM: Int = 2
    }
}
