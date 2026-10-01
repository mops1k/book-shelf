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
import io.github.bookshelf.core.storage.volume.RemovableVolume
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import io.github.bookshelf.core.testing.fake.FakeRemovableShelfRepository
import io.github.bookshelf.core.testing.fake.FakeScanRepository
import io.github.bookshelf.core.testing.fake.FakeSourceRepository

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
    // Свои интерфейсы — только фейками (ADR-012, правило 2): mockk скрывал бы изменения
    // интерфейса, а здесь видно, какие источники тест реально просил просканировать.
    private val sources = FakeSourceRepository()
    private val scans = FakeScanRepository()
    private val shelf = FakeRemovableShelfRepository()
    private val volumes = FakeVolumeMonitor()

    @Test
    fun `sync schedules a scan for every enabled source`() = runTest {
        sources.sources = listOf(source(id = "s1"), source(id = "s2"))

        val scheduled = LibrarySyncImpl(sources, scans, clock, shelf, volumes).syncNow()

        assertThat(scheduled.getOrNull()).isEqualTo(2)
        assertThat(scans.started.count { id -> id == "s1" }).isEqualTo(1)
        assertThat(scans.started.count { id -> id == "s2" }).isEqualTo(1)
    }

    @Test
    fun `a source of an absent medium is hidden instead of scanned`() = runTest {
        // Адрес источника съёмного тома: UUID карты внутри адреса дерева.
        sources.sources = listOf(
            source(id = "card", uri = "content://com.android.externalstorage.documents/tree/1A2B-3C4D%3A"),
        )
        // Карты в устройстве нет: в списке томов пусто.
        volumes.volumes = emptyList()

        LibrarySyncImpl(sources, scans, clock, shelf, volumes).syncNow()

        // Книги карты скрываются (помечаются пропавшими), а не удаляются, и источник не сканируется
        // вслепую: обход недоступного дерева всё равно упал бы.
        assertThat(shelf.ejected.map { volume -> volume.uuid }).containsExactly("1A2B-3C4D")
        assertThat(scans.started).isEmpty()
    }

    @Test
    fun `a mounted medium is not hidden`() = runTest {
        sources.sources = listOf(
            source(id = "card", uri = "content://com.android.externalstorage.documents/tree/1A2B-3C4D%3A"),
        )
        volumes.volumes = listOf(
            io.github.bookshelf.core.storage.volume.RemovableVolume(
                uuid = "1A2B-3C4D",
                label = "SD-карта",
                path = "/storage/1A2B-3C4D",
            ),
        )

        LibrarySyncImpl(sources, scans, clock, shelf, volumes).syncNow()

        // Носитель на месте: ничего не скрываем, источник сканируется как обычно.
        assertThat(shelf.ejected).isEmpty()
        assertThat(scans.started).contains("card")
    }

    @Test
    fun `disabled and access-lost sources are skipped`() = runTest {
        sources.sources = listOf(
            source(id = "enabled"),
            source(id = "disabled", enabled = false),
            source(id = "lost", accessLost = true),
        )

        val scheduled = LibrarySyncImpl(sources, scans, clock, shelf, volumes).syncNow()

        assertThat(scheduled.getOrNull()).isEqualTo(1)
        assertThat(scans.started.count { id -> id == "disabled" }).isEqualTo(0)
        assertThat(scans.started.count { id -> id == "lost" }).isEqualTo(0)
    }

    @Test
    fun `second call within the interval does nothing`() = runTest {
        sources.sources = listOf(source(id = "s1"))
        val sync = LibrarySyncImpl(sources, scans, clock, shelf, volumes)
        sync.syncNow()

        clock.advanceByMillis(LibrarySync.DEFAULT_MIN_INTERVAL_MILLIS - 1)
        val skipped = sync.syncIfStale()

        assertThat(skipped.getOrNull()).isEqualTo(0)
        assertThat(scans.started.count { id -> id == "s1" }).isEqualTo(1)
    }

    @Test
    fun `call after the interval syncs again`() = runTest {
        sources.sources = listOf(source(id = "s1"))
        val sync = LibrarySyncImpl(sources, scans, clock, shelf, volumes)
        sync.syncNow()

        clock.advanceByMillis(LibrarySync.DEFAULT_MIN_INTERVAL_MILLIS)
        sync.syncIfStale()

        // Возврат в приложение после добавления книги обязан обновить библиотеку, а не отправить
        // пользователя жать «Пересканировать».
        assertThat(scans.started.count { id -> id == "s1" }).isEqualTo(2)
    }

    @Test
    fun `library without sources schedules nothing`() = runTest {
        sources.sources = emptyList()

        val scheduled = LibrarySyncImpl(sources, scans, clock, shelf, volumes).syncNow()

        assertThat(scheduled.getOrNull()).isEqualTo(0)
        assertThat(scans.started).isEmpty()
    }

    @Test
    fun `broken source does not break the others`() = runTest {
        sources.sources = listOf(source(id = "bad"), source(id = "good"))
        // Недоступный источник отвечает отказом: так ведёт себя потерявшая доступ папка.
        scans.startResults["bad"] = AppResult.failure(AppError.AccessLost)

        val scheduled = LibrarySyncImpl(sources, scans, clock, shelf, volumes).syncNow()

        // Один недоступный источник не должен отменять синхронизацию остальных.
        assertThat(scheduled.getOrNull()).isEqualTo(1)
        assertThat(scans.started.count { id -> id == "good" }).isEqualTo(1)
    }

    private fun source(
        id: String,
        enabled: Boolean = true,
        accessLost: Boolean = false,
        uri: String? = null,
    ): Source = Source(
        id = id,
        uri = uri ?: "content://tree/$id",
        displayName = "Книги $id",
        kind = SourceKind.SAF_TREE,
        enabled = enabled,
        accessLost = accessLost,
    )

    private companion object {
        const val START_MILLIS = 1_790_000_000_000L
    }
}

/** Тома системы: тест задаёт их сам. */
private class FakeVolumeMonitor : io.github.bookshelf.core.storage.volume.RemovableVolumeMonitor {
    var volumes: List<io.github.bookshelf.core.storage.volume.RemovableVolume> = emptyList()
    override fun observe():
        kotlinx.coroutines.flow.Flow<List<RemovableVolume>> =
        kotlinx.coroutines.flow.flowOf(volumes)
}
