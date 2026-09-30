package io.github.bookshelf.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import io.github.bookshelf.core.covers.cache.CoverCacheCleaner
import io.github.bookshelf.core.common.reader.ReaderSession
import io.github.bookshelf.core.datastore.SettingsRepository
import io.github.bookshelf.core.datastore.model.LibrarySettings
import io.github.bookshelf.core.datastore.model.megabytesToBytes
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Уборка кэша обложек по лимиту диска.
 *
 * Лимит — настройка `library.coverCacheLimitMb` (умолчание 200 МБ), а не константа: до этого
 * работа всегда чистила до 128 МБ, и лимит на экране настроек описывал не то, что происходило
 * на диске. Обложки — производные данные, поэтому превышение лимита не ошибка, а повод вытеснить
 * лишнее и завершиться успешно.
 */
@HiltWorker
class CoverCleanupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val cleaner: CoverCacheCleaner,
    private val settingsRepository: SettingsRepository,
    /** Сессия чтения: уборка читает и удаляет файлы, читалке это мешает так же, как скан. */
    private val readerSession: ReaderSession,
) : CoroutineWorker(appContext, params) {

    /**
     * Чистка кэша обложек по лимиту.
     *
     * Раньше работа возвращала `Result.success()`, ничего не делая: кэш рос без границы, и на
     * устройстве с 2 ГБ памяти это кончилось бы нехваткой места. Молчаливое «успешно» неотличимо
     * от успешной уборки, поэтому вызов подключён.
     *
     * Чтение настроек обёрнуто отдельно от уборки: сбой DataStore не должен оставлять кэш вообще
     * без чистки — на этот случай есть резервный лимит ([coverCacheLimitBytes]).
     */
    override suspend fun doWork(): Result = runCatching {
        // Открыта читалка — уборку откладываем: она ходит по файлам кэша, а читалке нужен процессор.
        // Работа периодическая, поэтому пропуск ничего не ломает: следующий проход сделает своё.
        if (readerSession.isActive()) {
            Log.w("BookShelf", "уборка кэша обложек пропущена: открыта читалка")
            return@runCatching Result.success()
        }
        val library = runCatching { settingsRepository.current().library }.getOrNull()
        cleaner.evictToLimit(limitBytes = coverCacheLimitBytes(library))
        Result.success()
    }.getOrElse { Result.retry() }
}

/**
 * Лимит кэша обложек в байтах: значение настройки, а при её недоступности — прежняя константа.
 *
 * Резерв осознанный: уборка — фоновая работа, и если настройки не читаются (сбой файла DataStore
 * при первом создании, отозванный доступ к хранилищу), вытеснить по прежним 128 МБ лучше, чем не
 * вытеснять вовсе и дать кэшу вырасти без границы. Живёт вне воркера, чтобы проверяться без
 * WorkManager и Android.
 */
internal fun coverCacheLimitBytes(settings: LibrarySettings?): Long =
    settings?.coverCacheLimitMb?.let(::megabytesToBytes) ?: FALLBACK_COVER_CACHE_LIMIT_BYTES

/** Прежний лимит уборки обложек (128 МБ) — резерв на случай недоступных настроек. */
internal const val FALLBACK_COVER_CACHE_LIMIT_BYTES: Long = 128L * 1024L * 1024L
