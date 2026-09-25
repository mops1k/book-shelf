package io.github.bookshelf.work

import android.content.Context
import androidx.hilt.work.HiltWorker
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
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = Result.success()
}
