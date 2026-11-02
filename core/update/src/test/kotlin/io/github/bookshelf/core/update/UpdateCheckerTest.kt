package io.github.bookshelf.core.update

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.time.AppClock
import io.github.bookshelf.core.datastore.update.UpdateStore
import io.github.bookshelf.core.update.checker.UpdateCheck
import io.github.bookshelf.core.update.checker.UpdateChecker
import io.github.bookshelf.core.update.model.AppFlavor
import io.github.bookshelf.core.update.model.AppVersion
import io.github.bookshelf.core.update.model.ReleaseAsset
import io.github.bookshelf.core.update.model.ReleaseInfo
import io.github.bookshelf.core.update.source.ReleaseSource
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Правила проверки обновлений (ADR-018).
 *
 * Проверяются решения, а не сеть: выключенная настройка, суточный троттлинг, flavor Play,
 * сравнение версий и ошибка сети. Свой порт `ReleaseSource` подменяется фейком, чужие типы
 * не мокаются.
 */
class UpdateCheckerTest {

    private val now = 1_000_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    private val current = AppVersion(2, 0, 1)

    private val release = ReleaseInfo(
        version = AppVersion(2, 0, 2),
        versionName = "BookShelf 2.0.2",
        notes = "Что нового",
        pageUrl = "https://example.org/release",
        assets = listOf(ReleaseAsset("BookShelf-2.0.2-full.apk", "https://example.org/full.apk")),
    )

    private inner class FakeSource(private val result: () -> ReleaseInfo?) : ReleaseSource {
        var calls = 0
        override suspend fun latest(): ReleaseInfo? {
            calls++
            return result()
        }
    }

    private inner class FakeStore(private var last: Long? = null) : UpdateStore {
        var marked: Long? = null
        override suspend fun lastCheckEpochMs(): Long? = last
        override suspend fun markChecked(epochMs: Long) {
            marked = epochMs
            last = epochMs
        }
    }

    private inner class FakeClock(private val now: Long) : AppClock {
        override fun nowMillis(): Long = now
    }

    private fun checker(source: ReleaseSource, store: UpdateStore, clock: AppClock = FakeClock(now)) =
        UpdateChecker(source = source, store = store, clock = clock)

    @Test
    fun disabled_setting_means_no_network_at_all() = runBlocking {
        val source = FakeSource { release }

        val result = checker(source, FakeStore()).check(current, AppFlavor.FULL, enabled = false, force = false)

        assertThat(result).isEqualTo(UpdateCheck.Disabled)
        assertThat(source.calls).isEqualTo(0)
    }

    @Test
    fun play_flavor_is_never_checked() = runBlocking {
        val source = FakeSource { release }

        val result = checker(source, FakeStore()).check(current, AppFlavor.PLAY, enabled = true, force = true)

        assertThat(result).isEqualTo(UpdateCheck.Disabled)
        assertThat(source.calls).isEqualTo(0)
    }

    @Test
    fun recent_check_is_throttled_for_a_day() = runBlocking {
        val source = FakeSource { release }
        val store = FakeStore(last = now - day / 2)

        val result = checker(source, store).check(current, AppFlavor.FULL, enabled = true, force = false)

        assertThat(result).isEqualTo(UpdateCheck.Throttled)
        assertThat(source.calls).isEqualTo(0)
    }

    @Test
    fun forced_check_ignores_throttling() = runBlocking {
        val source = FakeSource { release }

        val result = checker(source, FakeStore(last = now)).check(
            current,
            AppFlavor.FULL,
            enabled = true,
            force = true,
        )

        assertThat(result).isInstanceOf(UpdateCheck.Available::class.java)
        assertThat(source.calls).isEqualTo(1)
    }

    @Test
    fun newer_release_is_available_with_the_flavor_asset() = runBlocking {
        val result = checker(FakeSource { release }, FakeStore()).check(
            current,
            AppFlavor.FULL,
            enabled = true,
            force = false,
        )

        val available = result as UpdateCheck.Available
        assertThat(available.release.version).isEqualTo(AppVersion(2, 0, 2))
        assertThat(available.asset?.name).isEqualTo("BookShelf-2.0.2-full.apk")
    }

    @Test
    fun same_version_is_not_an_update() = runBlocking {
        val result = checker(FakeSource { release.copy(version = current) }, FakeStore()).check(
            current,
            AppFlavor.FULL,
            enabled = true,
            force = false,
        )

        assertThat(result).isEqualTo(UpdateCheck.None)
    }

    @Test
    fun network_failure_is_reported_and_still_throttles() = runBlocking {
        val store = FakeStore()
        val source = FakeSource { throw java.io.IOException("offline") }

        val result = checker(source, store).check(current, AppFlavor.FULL, enabled = true, force = false)

        assertThat(result).isInstanceOf(UpdateCheck.Failed::class.java)
        assertThat(store.marked).isEqualTo(now)
    }

    @Test
    fun successful_check_remembers_the_time() = runBlocking {
        val store = FakeStore()

        checker(FakeSource { release }, store).check(current, AppFlavor.FULL, enabled = true, force = false)

        assertThat(store.marked).isEqualTo(now)
    }
}
