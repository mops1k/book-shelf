package io.github.bookshelf.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Сканирование источника.
 *
 * Ключевые требования (docs/architecture.md §4.5):
 * * уникальная работа на источник, `ExistingWorkPolicy.KEEP`;
 * * работа **возобновляемая**: курсор обхода хранится в `scan_runs`, поэтому скан переживает
 *   `Service.onTimeout` (Android 15 ограничивает `dataSync` шестью часами за 24 часа),
 *   убийство процесса и потерю foreground-режима;
 * * прогресс уходит в `setProgress` и в уведомление.
 *
 * STUB(Sprint 2): сейчас работа не делает ничего и завершается успешно — каркас для того,
 * чтобы на неё можно было написать первый падающий тест.
 */
@HiltWorker
class ScanSourceWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = Result.success()

    companion object {
        const val KEY_SOURCE_ID: String = "sourceId"
        const val KEY_FORCE_RESCAN: String = "forceRescan"

        /** TODO(Sprint 2): уникальное имя работы на источник. */
        fun uniqueWorkName(sourceId: String): String = "scan-$sourceId"
    }
}
