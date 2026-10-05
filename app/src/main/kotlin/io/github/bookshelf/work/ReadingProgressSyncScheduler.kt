package io.github.bookshelf.work

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.bookshelf.core.data.sync.ProgressSyncSchedule
import io.github.bookshelf.core.data.sync.isProgressSyncEnabled
import io.github.bookshelf.core.datastore.ProgressSyncInterval
import io.github.bookshelf.core.datastore.SettingsRepository
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Расписание опроса читалки ради прогресса чтения.
 *
 * Почему цепочка одноразовых работ, а не периодическая: `WorkManager` не запускает периодические
 * работы чаще пятнадцати минут, а интервал настраивается (2/5/10/15 минут) — просить «раз в две
 * минуты» периодической работой значило бы соврать в настройках.
 *
 * Подводный камень, из-за которого цепочка рвётся: продолжать её через `REPLACE` нельзя — работа
 * отменила бы саму себя; `KEEP` тоже не годится — работа с этим именем уже выполняется, и новая
 * посылка молча потеряется. Поэтому каждая работа добавляется в конец через `APPEND_OR_REPLACE`:
 * следующая встанет после текущей, а если цепочку отменили — начнётся заново.
 */
@Singleton
class ReadingProgressSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) : ProgressSyncSchedule {

    /**
     * Пересмотреть расписание после смены настройки.
     *
     * Здесь именно `REPLACE`, а не добавление в конец: пользователь мог только что сменить
     * интервал, и ждать прежней задержки ему незачем. Цепочки это не ломает — следующее
     * срабатывание продолжит себя обычным путём.
     */
    override suspend fun review() {
        val minutes = minutesOrNull() ?: run {
            cancel()
            return
        }
        val request = OneTimeWorkRequestBuilder<ReadingProgressSyncWorker>()
            .setInitialDelay(minutes, TimeUnit.MINUTES)
            .addTag(ReadingProgressSyncWorker.WORK_TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            ReadingProgressSyncWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    /**
     * Поставить следующее срабатывание по текущим настройкам.
     *
     * Выключенная синхронизация не просто «не ставит» работу, а отменяет цепочку: иначе уже
     * запланированное срабатывание разбудило бы читалку после того, как человек это запретил.
     */
    suspend fun scheduleNext() {
        val minutes = minutesOrNull() ?: run {
            cancel()
            return
        }
        val request = OneTimeWorkRequestBuilder<ReadingProgressSyncWorker>()
            .setInitialDelay(minutes, TimeUnit.MINUTES)
            .addTag(ReadingProgressSyncWorker.WORK_TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            ReadingProgressSyncWorker.WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
    }

    /**
     * Начать цепочку, если её ещё нет.
     *
     * Вызывается при старте приложения: `KEEP` не даёт наплодить несколько цепочек, а уже идущая
     * работа не сбивается с ритма.
     */
    suspend fun startIfEnabled() {
        val minutes = minutesOrNull() ?: run {
            cancel()
            return
        }
        val request = OneTimeWorkRequestBuilder<ReadingProgressSyncWorker>()
            .setInitialDelay(minutes, TimeUnit.MINUTES)
            .addTag(ReadingProgressSyncWorker.WORK_TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            ReadingProgressSyncWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    /**
     * Интервал опроса или `null`, если синхронизировать не с чем.
     *
     * Проверка одна на все три входа (пересмотр, продолжение цепочки, первый запуск): раньше она
     * была скопирована трижды и смотрела только на флаг FBReader — с появлением второго источника
     * копию пришлось бы править в трёх местах, а забытая копия молча выключила бы синхронизацию.
     */
    private suspend fun minutesOrNull(): Long? {
        val readerSettings = settings.current().reader
        if (!readerSettings.isProgressSyncEnabled()) return null
        return ProgressSyncInterval
            .minutesOrDefault(readerSettings.progressSyncIntervalMinutes)
            .toLong()
    }

    /** Забыть цепочку: вызывается при выключении синхронизации. */
    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(ReadingProgressSyncWorker.WORK_NAME)
    }
}
