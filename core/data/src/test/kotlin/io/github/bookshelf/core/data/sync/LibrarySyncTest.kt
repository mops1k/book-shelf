package io.github.bookshelf.core.data.sync

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.common.error.getOrNull
import io.github.bookshelf.core.common.testing.FakeAppClock
import io.github.bookshelf.core.data.model.Source
import io.github.bookshelf.core.data.model.SourceKind
import io.github.bookshelf.core.data.repository.ScanRepository
import io.github.bookshelf.core.data.repository.SourceRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Синхронизация библиотеки с файловой системой.
 *
 * Проверяется поведение, из-за которого она вообще появилась: при возврате в приложение
 * инкрементальный скан ставится для включённых источников, повторные вызовы подряд ничего не
 * делают, а источник с потерянным доступом не сканируется (иначе книги удалялись бы по
 * неудачному проходу).
 */
class LibrarySyncTest {

    private val clock = FakeAppClock(START_MILLIS)
    private val sources = mockk<SourceRepository>()
    private val scans = mockk<ScanRepository>(relaxed = true)

    @Test
    fun `sync schedules a scan for every enabled source`() = runTest {
        every { sources.observeSources() } returns flowOf(
            listOf(source(id = "s1"), source(id = "s2")),
        )
        coEvery { scans.startScan(any()) } returns AppResult.success(Unit)

        val scheduled = LibrarySyncImpl(sources, scans, clock).syncNow()

        assertThat(scheduled.getOrNull()).isEqualTo(2)
        coVerify(exactly = 1) { scans.startScan("s1") }
        coVerify(exactly = 1) { scans.startScan("s2") }
    }

    @Test
    fun `disabled and access-lost sources are skipped`() = runTest {
        every { sources.observeSources() } returns flowOf(
            listOf(
                source(id = "enabled"),
                source(id = "disabled", enabled = false),
                source(id = "lost", accessLost = true),
            ),
        )
        coEvery { scans.startScan(any()) } returns AppResult.success(Unit)

        val scheduled = LibrarySyncImpl(sources, scans, clock).syncNow()

        assertThat(scheduled.getOrNull()).isEqualTo(1)
        coVerify(exactly = 0) { scans.startScan("disabled") }
        coVerify(exactly = 0) { scans.startScan("lost") }
    }

    @Test
    fun `second call within the interval does nothing`() = runTest {
        every { sources.observeSources() } returns flowOf(listOf(source(id = "s1")))
        coEvery { scans.startScan(any()) } returns AppResult.success(Unit)
        val sync = LibrarySyncImpl(sources, scans, clock)
        sync.syncNow()

        clock.advanceByMillis(LibrarySync.DEFAULT_MIN_INTERVAL_MILLIS - 1)
        val skipped = sync.syncIfStale()

        assertThat(skipped.getOrNull()).isEqualTo(0)
        coVerify(exactly = 1) { scans.startScan("s1") }
    }

    @Test
    fun `call after the interval syncs again`() = runTest {
        every { sources.observeSources() } returns flowOf(listOf(source(id = "s1")))
        coEvery { scans.startScan(any()) } returns AppResult.success(Unit)
        val sync = LibrarySyncImpl(sources, scans, clock)
        sync.syncNow()

        clock.advanceByMillis(LibrarySync.DEFAULT_MIN_INTERVAL_MILLIS)
        sync.syncIfStale()

        // Возврат в приложение после добавления книги обязан обновить библиотеку, а не отправить
        // пользователя жать «Пересканировать».
        coVerify(exactly = 2) { scans.startScan("s1") }
    }

    @Test
    fun `library without sources schedules nothing`() = runTest {
        every { sources.observeSources() } returns flowOf(emptyList())

        val scheduled = LibrarySyncImpl(sources, scans, clock).syncNow()

        assertThat(scheduled.getOrNull()).isEqualTo(0)
        coVerify(exactly = 0) { scans.startScan(any()) }
    }

    @Test
    fun `broken source does not break the others`() = runTest {
        every { sources.observeSources() } returns flowOf(listOf(source(id = "bad"), source(id = "good")))
        coEvery { scans.startScan("bad") } returns AppResult.failure(
            AppError.AccessLost,
        )
        coEvery { scans.startScan("good") } returns AppResult.success(Unit)

        val scheduled = LibrarySyncImpl(sources, scans, clock).syncNow()

        // Один недоступный источник не должен отменять синхронизацию остальных.
        assertThat(scheduled.getOrNull()).isEqualTo(1)
        coVerify(exactly = 1) { scans.startScan("good") }
    }

    private fun source(
        id: String,
        enabled: Boolean = true,
        accessLost: Boolean = false,
    ): Source = Source(
        id = id,
        uri = "content://tree/$id",
        displayName = "Книги $id",
        kind = SourceKind.SAF_TREE,
        enabled = enabled,
        accessLost = accessLost,
    )

    private companion object {
        const val START_MILLIS = 1_790_000_000_000L
    }
}
