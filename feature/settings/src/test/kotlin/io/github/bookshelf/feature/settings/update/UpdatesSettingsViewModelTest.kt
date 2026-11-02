package io.github.bookshelf.feature.settings.update

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.time.AppClock
import io.github.bookshelf.core.datastore.SettingsRepository
import io.github.bookshelf.core.datastore.model.AppSettings
import io.github.bookshelf.core.datastore.update.UpdateStore
import io.github.bookshelf.core.testing.rule.MainDispatcherRule
import io.github.bookshelf.core.update.checker.UpdateChecker
import io.github.bookshelf.core.update.model.AppBuildInfo
import io.github.bookshelf.core.update.model.AppFlavor
import io.github.bookshelf.core.update.model.AppVersion
import io.github.bookshelf.core.update.model.ReleaseInfo
import io.github.bookshelf.core.update.source.ReleaseSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/**
 * Ручная проверка обновлений в «О приложении» (ADR-018).
 *
 * Кнопка обязана дать ответ: «Обновлений нет», «Доступна версия 2.0.2», «Не удалось проверить»
 * или «Проверка отключена». Троттлинг здесь не действует — нажатие всегда идёт в сеть.
 */
class UpdatesSettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val buildInfo = AppBuildInfo(versionName = "2.0.1", flavor = AppFlavor.FULL)

    private val release = ReleaseInfo(
        version = AppVersion(2, 0, 2),
        versionName = "BookShelf 2.0.2",
        notes = "",
        pageUrl = "https://example.org/release",
        assets = emptyList(),
    )

    private inner class FakeSource(private val result: () -> ReleaseInfo?) : ReleaseSource {
        var calls = 0
        override suspend fun latest(): ReleaseInfo? {
            calls++
            return result()
        }
    }

    private inner class FakeStore : UpdateStore {
        private var last: Long? = null
        override suspend fun lastCheckEpochMs(): Long? = last
        override suspend fun markChecked(epochMs: Long) {
            last = epochMs
        }
    }

    private inner class FakeClock : AppClock {
        override fun nowMillis(): Long = 1_000_000_000_000L
    }

    private inner class FakeSettings(private val enabled: Boolean = true) : SettingsRepository {
        private val state = MutableStateFlow(
            AppSettings().let { it.copy(advanced = it.advanced.copy(checkUpdates = enabled)) },
        )
        override val settings: Flow<AppSettings> = state
        override suspend fun update(transform: (AppSettings) -> AppSettings) {
            state.value = transform(state.value)
        }
        override suspend fun current(): AppSettings = state.first()
        override suspend fun resetToDefaults() {
            state.value = AppSettings()
        }
    }

    private fun viewModel(
        source: ReleaseSource,
        settings: FakeSettings = FakeSettings(),
    ): UpdatesSettingsViewModel {
        val checker = UpdateChecker(source = source, store = FakeStore(), clock = FakeClock())
        return UpdatesSettingsViewModel(checker = checker, settingsRepository = settings, buildInfo = buildInfo)
    }

    @Test
    fun reports_available_version() = runTest {
        val model = viewModel(FakeSource { release })

        model.onCheck()
        advanceUntilIdle()

        assertThat(model.uiState.value.result).isEqualTo(UpdateResult.AVAILABLE)
        assertThat(model.uiState.value.availableVersion).isEqualTo("2.0.2")
    }

    @Test
    fun reports_no_update() = runTest {
        val model = viewModel(FakeSource { release.copy(version = AppVersion(2, 0, 1)) })

        model.onCheck()
        advanceUntilIdle()

        assertThat(model.uiState.value.result).isEqualTo(UpdateResult.NO_UPDATE)
    }

    @Test
    fun reports_network_failure() = runTest {
        val model = viewModel(FakeSource { throw java.io.IOException("offline") })

        model.onCheck()
        advanceUntilIdle()

        assertThat(model.uiState.value.result).isEqualTo(UpdateResult.FAILED)
    }

    @Test
    fun disabled_setting_reports_unavailable_and_makes_no_request() = runTest {
        val source = FakeSource { release }
        val model = viewModel(source, settings = FakeSettings(enabled = false))

        model.onCheck()
        advanceUntilIdle()

        assertThat(model.uiState.value.result).isEqualTo(UpdateResult.UNAVAILABLE)
        assertThat(source.calls).isEqualTo(0)
    }

    @Test
    fun second_press_while_checking_is_ignored() = runTest {
        // Запрос «висит» на gate: так видно состояние «проверяю», иначе Unconfined-диспетчер
        // выполняет проверку целиком до второго нажатия и защита не проверяется.
        val gate = CompletableDeferred<ReleaseInfo?>()
        val source = object : ReleaseSource {
            var calls = 0
            override suspend fun latest(): ReleaseInfo? {
                calls++
                return gate.await()
            }
        }
        val model = viewModel(source)

        model.onCheck()
        assertThat(model.uiState.value.checking).isTrue()
        model.onCheck()

        assertThat(source.calls).isEqualTo(1)

        gate.complete(null)
        advanceUntilIdle()
    }
}
