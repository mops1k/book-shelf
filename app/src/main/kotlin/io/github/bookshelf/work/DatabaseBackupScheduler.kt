package io.github.bookshelf.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.bookshelf.core.data.backup.DatabaseBackupSchedule
import io.github.bookshelf.core.datastore.SettingsRepository
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Расписание ежедневной копии базы.
 *
 * Периодическая работа раз в сутки — ровно тот случай, для которого `WorkManager` и предназначен:
 * копия нужна не в определённую секунду, а «раз в день, когда телефон не занят».
 *
 * Выключенная настройка не просто «не ставит» работу, а отменяет уже запланированную: иначе копия
 * создалась бы после того, как человек это запретил.
 */
@Singleton
class DatabaseBackupScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) : DatabaseBackupSchedule {

    /** Пересмотреть расписание: поставить, если включено, и снять, если выключено. */
    override suspend fun review() {
        if (!settings.current().advanced.dbBackupEnabled) {
            cancel()
            return
        }
        val request = PeriodicWorkRequestBuilder<DatabaseBackupWorker>(PERIOD_HOURS, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            DatabaseBackupWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** Забыть расписание. */
    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(DatabaseBackupWorker.WORK_NAME)
    }

    private companion object {
        /** Раз в сутки: чаще копия не нужна, реже — становится неактуальной. */
        const val PERIOD_HOURS = 24L
    }
}
