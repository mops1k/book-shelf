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
 * Расписание фоновой синхронизации библиотеки.
 *
 * Ритм — пятнадцать минут, потому что это **минимальный** интервал периодических работ
 * WorkManager: чаще система всё равно не запустит, а просить чаще означало бы соврать в
 * настройках. Возврат в приложение синхронизирует отдельно и сразу (`LibrarySync` из
 * `MainActivity`), так что фоновый проход нужен только для случая «книгу добавили, приложение
 * не открывали».
 */
@Singleton
class LibrarySyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Поставить периодическую работу, если её ещё нет.
     *
     * `KEEP` вместо `UPDATE`: расписание уже могло быть создано прошлым запуском, и пересоздавать
     * его на каждом старте приложения незачем — это сбрасывало бы таймер ожидания.
     */
    fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<LibrarySyncWorker>(
            PERIOD_MINUTES,
            TimeUnit.MINUTES,
        )
            .addTag(LibrarySyncWorker.WORK_TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            LibrarySyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private companion object {
        /** Минимальный интервал периодических работ WorkManager. */
        const val PERIOD_MINUTES = 15L
    }
}
