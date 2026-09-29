package io.github.bookshelf.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.errorOrNull
import io.github.bookshelf.core.common.error.isFailure
import io.github.bookshelf.core.common.error.isSuccess
import io.github.bookshelf.core.common.testing.FakeAppClock
import io.github.bookshelf.core.common.testing.TestAppDispatchers
import io.github.bookshelf.core.data.model.ScanStatus
import io.github.bookshelf.core.data.repository.impl.ScanRepositoryImpl
import io.github.bookshelf.core.data.sync.ScanCoordinatorImpl
import io.github.bookshelf.core.data.sync.ScanScheduler
import io.github.bookshelf.core.database.BookshelfDatabase
import io.github.bookshelf.core.database.entity.ScanRunEntity
import io.github.bookshelf.core.database.entity.SourceEntity
import io.github.bookshelf.core.database.model.ScanRunStatus
import io.github.bookshelf.core.database.model.SourceKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Очередь сканирования, наблюдаемый прогресс, история и координатор возобновления.
 *
 * Проверяется весь контракт между `:core:data` и Android-слоем: `:core:data` только передаёт
 * `sourceId` порту, а устойчивость прогона и его курсор остаются в Room.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class ScanRepositoryTest {

    private lateinit var database: BookshelfDatabase
    private lateinit var scheduler: ScanRecordingScanScheduler
    private lateinit var clock: FakeAppClock
    private lateinit var repository: ScanRepository
    private lateinit var coordinator: ScanCoordinatorImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BookshelfDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        scheduler = ScanRecordingScanScheduler()
        clock = FakeAppClock()
        repository = ScanRepositoryImpl(
            sourceDao = database.sourceDao(),
            scanRunDao = database.scanRunDao(),
            scanScheduler = scheduler,
            appDispatchers = TestAppDispatchers(),
        )
        coordinator = ScanCoordinatorImpl(
            sourceDao = database.sourceDao(),
            scanRunDao = database.scanRunDao(),
            scanScheduler = scheduler,
            appClock = clock,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun startScan_enabled_source_reaches_scheduler() = runBlocking {
        givenSource()

        val result = repository.startScan(SOURCE_ID)

        assertThat(result.isSuccess).isTrue()
        assertThat(scheduler.enqueuedSourceIds).isEqualTo(listOf(SOURCE_ID))
    }

    @Test
    fun startScan_unknown_source_returns_not_found_and_does_not_schedule() = runBlocking {
        val result = repository.startScan("нет-такого-источника")

        assertThat(result.isFailure).isTrue()
        assertThat(result.errorOrNull()).isInstanceOf(AppError.NotFound::class.java)
        assertThat(scheduler.enqueuedSourceIds).isEmpty()
    }

    @Test
    fun startScan_disabled_source_is_rejected_and_does_not_schedule() = runBlocking {
        givenSource(enabled = false)

        val result = repository.startScan(SOURCE_ID)

        assertThat(result.isFailure).isTrue()
        assertThat(result.errorOrNull()).isInstanceOf(AppError.InvalidArgument::class.java)
        assertThat(scheduler.enqueuedSourceIds).isEmpty()
    }

    @Test
    fun pause_stops_resumable_run_and_reaches_scheduler() = runBlocking {
        givenSource()
        database.scanRunDao().insert(
            run(id = "running-run", startedAt = 100L, status = ScanRunStatus.RUNNING),
        )

        repository.pause(SOURCE_ID)

        assertThat(database.scanRunDao().getById("running-run")?.status).isEqualTo(ScanRunStatus.PAUSED)
        assertThat(scheduler.cancelledSourceIds).isEqualTo(listOf(SOURCE_ID))
    }

    @Test
    fun observeProgress_without_runs_returns_idle_zero_state() = runBlocking {
        val progress = repository.observeProgress(SOURCE_ID).first()

        assertThat(progress.sourceId).isEqualTo(SOURCE_ID)
        assertThat(progress.status).isEqualTo(ScanStatus.IDLE)
        assertThat(progress.filesSeen).isEqualTo(0)
        assertThat(progress.added).isEqualTo(0)
        assertThat(progress.updated).isEqualTo(0)
        assertThat(progress.removed).isEqualTo(0)
        assertThat(progress.failed).isEqualTo(0)
        assertThat(progress.totalKnown).isEqualTo(0)
        assertThat(progress.startedAt).isEqualTo(0L)
        assertThat(progress.finishedAt).isNull()
    }

    @Test
    fun observeProgress_prefers_running_run_and_maps_all_counters() = runBlocking {
        givenSource()
        database.scanRunDao().insert(
            run(
                id = "run-running",
                startedAt = 200L,
                status = ScanRunStatus.RUNNING,
                cursor = "folder-2",
                filesSeen = 12,
                added = 7,
                updated = 3,
                removed = 1,
                failed = 2,
            ),
        )

        val progress = repository.observeProgress(SOURCE_ID).first()

        assertThat(progress.status).isEqualTo(ScanStatus.RUNNING)
        assertThat(progress.filesSeen).isEqualTo(12)
        assertThat(progress.added).isEqualTo(7)
        assertThat(progress.updated).isEqualTo(3)
        assertThat(progress.removed).isEqualTo(1)
        assertThat(progress.failed).isEqualTo(2)
        assertThat(progress.currentPath).isEqualTo("folder-2")
        assertThat(progress.startedAt).isEqualTo(200L)
    }

    @Test
    fun observeProgress_uses_latest_finished_run_when_nothing_is_running() = runBlocking {
        givenSource()
        database.scanRunDao().insert(
            run(
                id = "run-finished",
                startedAt = 300L,
                finishedAt = 350L,
                status = ScanRunStatus.COMPLETED,
                filesSeen = 20,
            ),
        )

        val progress = repository.observeProgress(SOURCE_ID).first()

        assertThat(progress.status).isEqualTo(ScanStatus.FINISHED)
        assertThat(progress.filesSeen).isEqualTo(20)
        assertThat(progress.finishedAt).isEqualTo(350L)
    }

    @Test
    fun observeReports_returns_full_history_sorted_by_start_time_desc() = runBlocking {
        database.scanRunDao().insert(run("run-1", startedAt = 100L, status = ScanRunStatus.COMPLETED))
        database.scanRunDao().insert(run("run-3", startedAt = 300L, status = ScanRunStatus.FAILED))
        database.scanRunDao().insert(run("run-2", startedAt = 200L, status = ScanRunStatus.CANCELLED))
        database.scanRunDao().insert(run("run-running", startedAt = 400L, status = ScanRunStatus.RUNNING))
        database.scanRunDao().insert(run("run-paused", startedAt = 350L, status = ScanRunStatus.PAUSED))

        val reports = repository.observeReports().first()

        assertThat(reports.map { it.runId }).isEqualTo(listOf("run-3", "run-2", "run-1"))
        assertThat(reports.map { it.status }).isEqualTo(
            listOf(ScanStatus.FAILED, ScanStatus.CANCELLED, ScanStatus.FINISHED),
        )
    }

    @Test
    fun coordinator_start_creates_running_run_before_enqueueing() = runBlocking {
        givenSource()

        val result = coordinator.start(SOURCE_ID)

        val storedRun = database.scanRunDao().findResumable(SOURCE_ID)
        assertThat(result.isSuccess).isTrue()
        assertThat(storedRun?.status).isEqualTo(ScanRunStatus.RUNNING)
        assertThat(storedRun?.startedAt).isEqualTo(clock.nowMillis())
        assertThat(scheduler.enqueuedSourceIds).isEqualTo(listOf(SOURCE_ID))
    }

    @Test
    fun coordinator_start_does_not_duplicate_an_active_run() = runBlocking {
        givenSource()

        coordinator.start(SOURCE_ID)
        val secondResult = coordinator.start(SOURCE_ID)

        assertThat(secondResult.isSuccess).isTrue()
        assertThat(database.scanRunDao().observeRunning().first()).hasSize(1)
    }

    @Test
    fun coordinator_resume_marks_paused_run_running_without_losing_cursor() = runBlocking {
        givenSource()
        database.scanRunDao().insert(
            run(
                id = "paused-run",
                startedAt = 100L,
                status = ScanRunStatus.PAUSED,
                cursor = "saved-cursor",
                filesSeen = 42,
            ),
        )

        val result = coordinator.resume(SOURCE_ID)

        val storedRun = database.scanRunDao().getById("paused-run")
        assertThat(result.isSuccess).isTrue()
        assertThat(storedRun?.status).isEqualTo(ScanRunStatus.RUNNING)
        assertThat(storedRun?.cursorDocumentId).isEqualTo("saved-cursor")
        assertThat(storedRun?.filesSeen).isEqualTo(42)
        assertThat(scheduler.enqueuedSourceIds).isEqualTo(listOf(SOURCE_ID))
    }

    @Test
    fun coordinator_pause_stops_resumable_run_and_cancels_work() = runBlocking {
        givenSource()
        coordinator.start(SOURCE_ID)
        val runId = requireNotNull(database.scanRunDao().findResumable(SOURCE_ID)).id

        coordinator.pause(SOURCE_ID)

        val storedRun = database.scanRunDao().getById(runId)
        assertThat(storedRun?.status).isEqualTo(ScanRunStatus.PAUSED)
        assertThat(storedRun?.finishedAt).isNull()
        assertThat(scheduler.cancelledSourceIds).isEqualTo(listOf(SOURCE_ID))
    }

    private suspend fun givenSource(enabled: Boolean = true) {
        database.sourceDao().insert(
            SourceEntity(
                id = SOURCE_ID,
                uri = TREE_URI,
                displayName = "Книги",
                kind = SourceKind.SAF_TREE,
                enabled = enabled,
            ),
        )
    }

    @Suppress("LongParameterList")
    private fun run(
        id: String,
        startedAt: Long,
        finishedAt: Long? = null,
        status: ScanRunStatus = ScanRunStatus.RUNNING,
        cursor: String? = null,
        filesSeen: Int = 0,
        added: Int = 0,
        updated: Int = 0,
        removed: Int = 0,
        failed: Int = 0,
        error: String? = null,
    ) = ScanRunEntity(
        id = id,
        sourceId = SOURCE_ID,
        startedAt = startedAt,
        finishedAt = finishedAt,
        status = status,
        cursorDocumentId = cursor,
        filesSeen = filesSeen,
        added = added,
        updated = updated,
        removed = removed,
        failedCount = failed,
        error = error,
    )

    private companion object {
        const val SOURCE_ID = "source-1"
        const val TREE_URI = "content://com.android.externalstorage.documents/tree/books"
    }
}

/** Запись вызовов намеренно без общего состояния с тестом источников: каждый набор проверяет один контракт. */
private class ScanRecordingScanScheduler : ScanScheduler {

    val enqueuedSourceIds = mutableListOf<String>()
    val cancelledSourceIds = mutableListOf<String>()
    val restartedSourceIds = mutableListOf<String>()

    override suspend fun enqueueScan(sourceId: String, restart: Boolean) {
        enqueuedSourceIds += sourceId
        if (restart) restartedSourceIds += sourceId
    }

    override suspend fun cancelScan(sourceId: String) {
        cancelledSourceIds += sourceId
    }
}
