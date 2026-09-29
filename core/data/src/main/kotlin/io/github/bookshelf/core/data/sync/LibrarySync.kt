package io.github.bookshelf.core.data.sync

import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.data.repository.ScanRepository
import io.github.bookshelf.core.data.repository.SourceRepository
import io.github.bookshelf.core.common.time.AppClock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Синхронизация библиотеки с файловой системой (docs/architecture.md §4.3, ADR-010).
 *
 * Полка читает книги из базы и на старте ничего не сканирует — это осознанно: разбор двух тысяч
 * файлов на каждом запуске не нужен. Но тогда добавленная на устройство книга появлялась бы
 * только после ручного «Пересканировать». [LibrarySync] закрывает эту дыру: по возврату в
 * приложение и по фоновому расписанию он ставит **инкрементальный** скан источников, который
 * сам добавляет новые файлы, обновляет изменённые и убирает исчезнувшие.
 *
 * Скан инкрементален внутри ([ScanScheduler] → `ScanSourceWorker`): неизменённые файлы только
 * отмечаются «виден», поэтому повторная синхронизация стоит обхода дерева, а не разбора книг.
 */
interface LibrarySync {

    /**
     * Поставить синхронизацию всех включённых источников.
     *
     * @return сколько источников поставлено в очередь (0 — включённых нет).
     */
    suspend fun syncNow(): AppResult<Int>

    /**
     * Синхронизировать, если с прошлого раза прошло не меньше [minIntervalMillis].
     *
     * Защита от частых повторов нужна потому, что вызов идёт на каждый возврат в приложение:
     * без неё переключение между окнами запускало бы обход дерева каждый раз.
     */
    suspend fun syncIfStale(
        minIntervalMillis: Long = DEFAULT_MIN_INTERVAL_MILLIS,
    ): AppResult<Int>

    companion object {
        /**
         * Пауза между синхронизациями — 30 секунд.
         *
         * Компромисс: этого достаточно, чтобы возврат в приложение после добавления книги
         * подхватывался сразу, и мало для того, чтобы переходы между окнами не запускали обход.
         */
        const val DEFAULT_MIN_INTERVAL_MILLIS: Long = 30_000
    }
}

/** Реализация [LibrarySync] поверх существующих репозиториев. */
@Singleton
class LibrarySyncImpl @Inject constructor(
    private val sourceRepository: SourceRepository,
    private val scanRepository: ScanRepository,
    private val clock: AppClock,
) : LibrarySync {

    /**
     * Время последней синхронизации.
     *
     * В памяти процесса, а не в настройках: холодный запуск обязан синхронизироваться, и
     * хранить время между запусками незачем. Фоновое расписание (раз в 15 минут) живёт своей
     * жизнью и на это поле не опирается.
     */
    private var lastSyncAt: Long = 0L

    override suspend fun syncNow(): AppResult<Int> {
        val sources = sourceRepository.observeSources().first()
            // Источник с потерянным доступом не сканируем: обход всё равно упадёт, а книги
            // удалять по неудачному проходу нельзя. Пометка `accessLost` — отдельная ветка.
            .filter { source -> source.enabled && !source.accessLost }
        lastSyncAt = clock.nowMillis()
        var scheduled = 0
        sources.forEach { source ->
            val started = scanRepository.startScan(source.id)
            if (started is AppResult.Success) scheduled += 1
        }
        return AppResult.success(scheduled)
    }

    override suspend fun syncIfStale(minIntervalMillis: Long): AppResult<Int> {
        if (clock.nowMillis() - lastSyncAt < minIntervalMillis) return AppResult.success(0)
        return syncNow()
    }
}
