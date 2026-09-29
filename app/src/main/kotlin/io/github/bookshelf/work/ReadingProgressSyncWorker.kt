package io.github.bookshelf.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import android.util.Log
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.bookshelf.core.common.error.getOrNull
import io.github.bookshelf.core.data.sync.SyncReadingProgress

/**
 * Опрос читалки ради прогресса чтения.
 *
 * Работает цепочкой: каждая работа ставит следующую с задержкой из настроек, потому что
 * периодические работы WorkManager не умеют интервал меньше пятнадцати минут, а читают с разной
 * скоростью (решение Александра, 29.09.2026).
 *
 * Ошибки не роняют работу: недоступная читалка — обычная жизнь, а `Result.retry()` превратился бы в
 * немедленные повторы. Следующее срабатывание просто попробует снова.
 */
@HiltWorker
class ReadingProgressSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val sync: SyncReadingProgress,
    private val scheduler: ReadingProgressSyncScheduler,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val outcome = sync.syncNow().getOrNull()
        // Итог пишем в журнал: без него не отличить «читалка не отдала данные» от «нечего
        // записывать», а это разные причины, и разбираться с ними приходится по устройству.
        Log.i(TAG, "синхронизация прогресса чтения: $outcome")
        // Продолжение цепочки: планировщик сам отменит её, если синхронизацию выключили.
        scheduler.scheduleNext()
        return Result.success()
    }

    companion object {

        /** Тот же тег, что у остальных журналов приложения. */
        private const val TAG = "BookShelf"

        /** Имя цепочки: по нему её продолжают и отменяют; имя одно на все срабатывания. */
        const val WORK_NAME = "reading-progress-sync"

        const val WORK_TAG = "reading-progress-sync"
    }
}
