package io.github.bookshelf.feature.library.update

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.time.AppClock
import io.github.bookshelf.core.datastore.SettingsRepository
import io.github.bookshelf.core.datastore.model.AppSettings
import io.github.bookshelf.core.datastore.update.UpdateStore
import io.github.bookshelf.core.testing.rule.MainDispatcherRule
import io.github.bookshelf.core.update.checker.UpdateChecker
import io.github.bookshelf.core.update.download.UpdateDownloadState
import io.github.bookshelf.core.update.download.UpdateDownloader
import io.github.bookshelf.core.update.install.ApkInstaller
import io.github.bookshelf.core.update.install.InstallGate
import io.github.bookshelf.core.update.model.AppBuildInfo
import io.github.bookshelf.core.update.model.AppFlavor
import io.github.bookshelf.core.update.model.AppVersion
import io.github.bookshelf.core.update.model.ReleaseAsset
import io.github.bookshelf.core.update.model.ReleaseInfo
import io.github.bookshelf.core.update.source.ReleaseSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/**
 * Диалог обновления на полке (ADR-018).
 *
 * Проверяются правила, которые видит пользователь: новая версия открывает диалог, «нет обновлений»
 * молчит, ошибка сети не показывает ничего, «Позже» закрывает до следующей проверки, а выключенная
 * настройка не делает запрос. Свои типы (`ReleaseSource`, `UpdateStore`, `SettingsRepository`)
 * подменяются фейками, моков чужих типов нет.
 */
class UpdateViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val now = 1_000_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    private val buildInfo = AppBuildInfo(versionName = "2.0.1", flavor = AppFlavor.FULL)

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
        override suspend fun lastCheckEpochMs(): Long? = last
        override suspend fun markChecked(epochMs: Long) {
            last = epochMs
        }
    }

    private inner class FakeClock(private val now: Long) : AppClock {
        override fun nowMillis(): Long = now
    }

    private inner class FakeSettings(var enabled: Boolean = true) : SettingsRepository {
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

    private inner class FakeDownloader : UpdateDownloader {
        var downloads = 0
        override fun download(url: String, assetName: String): Flow<UpdateDownloadState> {
            downloads++
            return kotlinx.coroutines.flow.flowOf(UpdateDownloadState.Done(java.io.File("/tmp/$assetName")))
        }
    }

    private inner class FakeInstaller(private val gate: InstallGate = InstallGate.ALLOWED) : ApkInstaller {
        var permissionRequests = 0
        var installed: java.io.File? = null
        override fun gate(): InstallGate = gate
        override fun requestPermission() {
            permissionRequests++
        }
        override fun install(file: java.io.File) {
            installed = file
        }
    }

    private fun viewModel(
        source: ReleaseSource,
        store: UpdateStore = FakeStore(),
        settings: FakeSettings = FakeSettings(),
        downloader: UpdateDownloader = FakeDownloader(),
        installer: ApkInstaller = FakeInstaller(),
    ): UpdateViewModel {
        val checker = UpdateChecker(source = source, store = store, clock = FakeClock(now))
        return UpdateViewModel(
            checker = checker,
            settingsRepository = settings,
            buildInfo = buildInfo,
            downloader = downloader,
            installer = installer,
        )
    }

    @Test
    fun newer_version_opens_dialog_with_notes() = runTest {
        val model = viewModel(FakeSource { release })

        advanceUntilIdle()

        val state = model.uiState.value
        assertThat(state.visible).isTrue()
        assertThat(state.versionName).isEqualTo("2.0.2")
        assertThat(state.notes).isEqualTo("Что нового")
    }

    @Test
    fun no_newer_version_keeps_dialog_hidden() = runTest {
        val model = viewModel(FakeSource { release.copy(version = AppVersion(2, 0, 1)) })

        advanceUntilIdle()

        assertThat(model.uiState.value.visible).isFalse()
        assertThat(model.uiState.value.manualResult).isNull()
    }

    @Test
    fun network_error_keeps_dialog_hidden() = runTest {
        val model = viewModel(FakeSource { throw java.io.IOException("offline") })

        advanceUntilIdle()

        assertThat(model.uiState.value.visible).isFalse()
        assertThat(model.uiState.value.manualResult).isNull()
    }

    @Test
    fun disabled_setting_makes_no_request() = runTest {
        val source = FakeSource { release }
        val model = viewModel(source, settings = FakeSettings(enabled = false))

        advanceUntilIdle()

        assertThat(model.uiState.value.visible).isFalse()
        assertThat(source.calls).isEqualTo(0)
    }

    @Test
    fun manual_check_answers_text_for_settings_row() = runTest {
        val model = viewModel(FakeSource { release.copy(version = AppVersion(2, 0, 1)) })

        model.checkManually()
        advanceUntilIdle()

        assertThat(model.uiState.value.manualResult).isEqualTo(ManualCheckResult.NO_UPDATE)
    }

    @Test
    fun manual_check_reports_network_failure() = runTest {
        val model = viewModel(FakeSource { throw java.io.IOException("offline") })

        model.checkManually()
        advanceUntilIdle()

        assertThat(model.uiState.value.manualResult).isEqualTo(ManualCheckResult.FAILED)
    }

    @Test
    fun same_day_second_launch_is_throttled() = runTest {
        val source = FakeSource { release }
        val model = viewModel(source, store = FakeStore(last = now - day / 2))

        advanceUntilIdle()

        assertThat(model.uiState.value.visible).isFalse()
        assertThat(source.calls).isEqualTo(0)
    }

    @Test
    fun download_starts_when_permission_is_present() = runTest {
        val downloader = FakeDownloader()
        val model = viewModel(FakeSource { release }, downloader = downloader)
        advanceUntilIdle()

        model.onDownload()
        advanceUntilIdle()

        assertThat(downloader.downloads).isEqualTo(1)
        assertThat(model.uiState.value.download).isInstanceOf(UpdateDownloadState.Done::class.java)
    }

    @Test
    fun missing_permission_requests_it_instead_of_downloading() = runTest {
        val downloader = FakeDownloader()
        val installer = FakeInstaller(gate = InstallGate.REQUEST_PERMISSION)
        val model = viewModel(FakeSource { release }, downloader = downloader, installer = installer)
        advanceUntilIdle()

        model.onDownload()

        assertThat(model.uiState.value.permissionRequired).isTrue()
        assertThat(downloader.downloads).isEqualTo(0)
    }

    @Test
    fun done_download_is_installed_by_installer() = runTest {
        val installer = FakeInstaller()
        val model = viewModel(FakeSource { release }, installer = installer)
        advanceUntilIdle()
        model.onDownload()
        advanceUntilIdle()

        model.onInstall()

        assertThat(installer.installed).isNotNull()
    }

    @Test
    fun dismiss_hides_dialog() = runTest {
        val model = viewModel(FakeSource { release })
        advanceUntilIdle()

        model.onDismiss()

        assertThat(model.uiState.value.visible).isFalse()
    }
}
