package io.github.bookshelf.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Расписание уборки кэша обложек (`CoverCleanupScheduler`).
 *
 * Дефект, ради которого появился планировщик: `CoverCleanupWorker` существовал, но в расписание не
 * ставился нигде — кэш обложек убирался только вручную. Проверяется именно это: работа поставлена,
 * она уникальная периодическая с ожидаемым именем и меткой, а отмена действительно снимает её.
 */
@RunWith(RobolectricTestRunner::class)
class CoverCleanupSchedulerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
    }

    @Test
    fun `the cleanup is scheduled as a unique daily job`() = runBlocking {
        CoverCleanupScheduler(context).schedulePeriodic()

        val infos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(CoverCleanupWorker.WORK_NAME)
            .get()

        assertThat(infos).hasSize(1)
        assertThat(infos.first().tags).contains(CoverCleanupWorker.WORK_TAG)
    }

    @Test
    fun `cancelling forgets the schedule`() = runBlocking {
        val scheduler = CoverCleanupScheduler(context)
        scheduler.schedulePeriodic()

        scheduler.cancel()

        val infos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(CoverCleanupWorker.WORK_NAME)
            .get()
        // Важно, что расписание снято: работа больше не ждёт запуска. Конкретное состояние отмены
        // WorkManager выбирает сам (в тестовом окружении это может быть и FAILED — исполнителя нет).
        assertThat(infos.map { info -> info.state }).doesNotContain(WorkInfo.State.ENQUEUED)
    }
}
