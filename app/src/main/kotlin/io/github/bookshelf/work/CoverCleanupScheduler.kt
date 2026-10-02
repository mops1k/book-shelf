package io.github.bookshelf.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Расписание уборки кэша обложек.
 *
 * До 01.10.2026 [CoverCleanupWorker] не ставился в расписание нигде: кэш убирался только вручную
 * из настроек и лимитом при скачивании, то есть автоматически не убирался никогда. Работа раз в
 * сутки — ровно тот случай, для которого `WorkManager` и предназначен: уборка не нужна в
 * определённую секунду, а «раз в день, когда телефон не занят».
 *
 * Уборка уважает читалку: [CoverCleanupWorker] проверяет сессию чтения и пропускает проход
 * (обложки — производные данные, отложить их удаление безвредно). `KEEP` вместо `UPDATE` — как у
 * синхронизации библиотеки: пересоздавать расписание на каждом старте незачем, это сбрасывало бы
 * таймер ожидания.
 */
@Singleton
class CoverCleanupScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Поставить периодическую уборку, если её ещё нет. */
    fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<CoverCleanupWorker>(PERIOD_HOURS, TimeUnit.HOURS)
            .addTag(CoverCleanupWorker.WORK_TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            CoverCleanupWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** Забыть расписание: нужно тестам и будущему переключателю в настройках. */
    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(CoverCleanupWorker.WORK_NAME)
    }

    private companion object {
        /** Раз в сутки: чаще уборка не нужна, реже — кэш успевает вырасти заметно. */
        const val PERIOD_HOURS = 24L
    }
}
