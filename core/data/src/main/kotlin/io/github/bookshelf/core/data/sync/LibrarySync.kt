package io.github.bookshelf.core.data.sync

import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.data.repository.ScanRepository
import io.github.bookshelf.core.data.model.Source
import io.github.bookshelf.core.data.removable.RemovableShelfRepository
import io.github.bookshelf.core.data.removable.volumeUuidOf
import io.github.bookshelf.core.data.repository.SourceRepository
import io.github.bookshelf.core.storage.volume.RemovableVolume
import io.github.bookshelf.core.storage.volume.RemovableVolumeMonitor
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
    /**
     * Съёмные носители и книги: нужны, чтобы изъятый носитель скрывал свои книги.
     *
     * Без этой проверки книги карты оставались бы на полке после изъятия: скан такого источника
     * падает («нет доступа к дереву»), а падение, в отличие от полного прохода, книги не убирает.
     */
    private val removableShelf: RemovableShelfRepository,
    private val volumeMonitor: RemovableVolumeMonitor,
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
        val allSources = sourceRepository.observeSources().first()
        // Источники исчезнувших носителей: их книги скрываются, а сами они не сканируются —
        // обход недоступного дерева всё равно упал бы и оставил шум в повторах.
        val absent = absentMedia(allSources)
        absent.forEach { source ->
            removableShelf.onEjected(
                RemovableVolume(
                    uuid = volumeUuidOf(source.uri).orEmpty(),
                    label = source.displayName,
                    path = "",
                ),
            )
        }
        val sources = allSources
            // Источник с потерянным доступом не сканируем: обход всё равно упадёт, а книги
            // удалять по неудачному проходу нельзя. Пометка `accessLost` — отдельная ветка.
            .filter { source -> source.enabled && !source.accessLost }
            .filter { source -> absent.none { gone -> gone.id == source.id } }
        lastSyncAt = clock.nowMillis()
        var scheduled = 0
        sources.forEach { source ->
            val started = scanRepository.startScan(source.id)
            if (started is AppResult.Success) scheduled += 1
        }
        return AppResult.success(scheduled)
    }

    /**
     * Источники съёмных носителей, которых сейчас нет.
     *
     * Съёмный источник узнаётся по UUID тома внутри адреса: у обычной папки его нет, и её книги
     * пропавшими не помечаются (полка библиотеки не должна опустеть из-за вынутой карты, которой
     * никогда не было).
     */
    private suspend fun absentMedia(allSources: List<Source>): List<Source> {
        val mounted = volumeMonitor.observe().first().map { volume -> volume.uuid.uppercase() }
        return allSources.filter { source ->
            val uuid = volumeUuidOf(source.uri)
            uuid != null && uuid !in mounted
        }
    }

    override suspend fun syncIfStale(minIntervalMillis: Long): AppResult<Int> {
        if (clock.nowMillis() - lastSyncAt < minIntervalMillis) return AppResult.success(0)
        return syncNow()
    }
}
