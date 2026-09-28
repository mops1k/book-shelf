package io.github.bookshelf.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import io.github.bookshelf.core.covers.cache.CoverCacheCleaner
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Уборка кэша обложек по лимиту диска (по умолчанию 200 МБ).
 *
 * STUB(Sprint 3): вызов `CoverCacheCleaner.evictToLimit(limit)` появится вместе с реализацией
 * кэша; сейчас работа завершается успешно и ничего не делает.
 */
@HiltWorker
class CoverCleanupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val cleaner: CoverCacheCleaner,
) : CoroutineWorker(appContext, params) {

    /**
     * Чистка кэша обложек по лимиту.
     *
     * Раньше работа возвращала `Result.success()`, ничего не делая: кэш рос без границы, и на
     * устройстве с 2 ГБ памяти это кончилось бы нехваткой места. Молчаливое «успешно» неотличимо
     * от успешной уборки, поэтому вызов подключён.
     */
    override suspend fun doWork(): Result = runCatching {
        cleaner.evictToLimit(limitBytes = LIMIT_BYTES)
        Result.success()
    }.getOrElse { Result.retry() }

    private companion object {
        /** 128 МБ: обложки — производные данные, их всегда можно скачать заново. */
        const val LIMIT_BYTES = 128L * 1024L * 1024L
    }
}
