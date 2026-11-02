package io.github.bookshelf.feature.launcher

import android.content.Intent
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.applist.model.AppCatalog
import io.github.bookshelf.core.applist.model.InstalledApp
import io.github.bookshelf.core.datastore.launcher.LauncherStore
import io.github.bookshelf.core.datastore.model.AppSortMode
import io.github.bookshelf.core.datastore.model.LauncherState
import io.github.bookshelf.core.testing.rule.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/**
 * `LauncherViewModel` на фейковом каталоге (ADR-015, ADR-012).
 *
 * Проверяется всё, что решает экран: что видно (себя, скрытые, системные), поиск, избранное,
 * сортировка, запуск со счётчиком, ошибки каталога и отсутствующий интент. Ни `PackageManager`,
 * ни DataStore здесь нет — модель проверяется без Android, как требует проект.
 */
class LauncherViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val own = app("io.github.bookshelf.debug", "BookShelf")
    private val coolReader = app("org.coolreader", "Cool Reader")
    private val koreader = app("org.koreader.launcher", "KOReader")
    private val settings = app("com.android.settings", "Настройки", isSystem = true)

    @Test
    fun load_shows_only_visible_apps_sorted_by_name() = runTest {
        val viewModel = viewModel(apps = listOf(own, koreader, coolReader, settings))

        val visible = viewModel.uiState.value.visible

        assertThat(visible.map { it.packageName }).containsExactly("org.coolreader", "org.koreader.launcher").inOrder()
    }

    @Test
    fun system_apps_appear_when_enabled_in_store() = runTest {
        val viewModel = viewModel(apps = listOf(coolReader, settings), state = LauncherState(showSystemApps = true))

        assertThat(viewModel.uiState.value.visible.map { it.packageName })
            .containsExactly("com.android.settings", "org.coolreader")
    }

    @Test
    fun hidden_app_disappears_and_returns_after_unhide() = runTest {
        val viewModel = viewModel(apps = listOf(coolReader, koreader))

        viewModel.onHide(coolReader)
        assertThat(viewModel.uiState.value.visible.map { it.packageName })
            .containsExactly("org.koreader.launcher")

        viewModel.onUnhide(coolReader.packageName)
        assertThat(viewModel.uiState.value.visible.map { it.packageName })
            .containsExactly("org.coolreader", "org.koreader.launcher")
    }

    @Test
    fun search_filters_by_label() = runTest {
        val viewModel = viewModel(apps = listOf(coolReader, koreader))

        viewModel.onQueryChange("kore")

        assertThat(viewModel.uiState.value.visible.map { it.packageName })
            .containsExactly("org.koreader.launcher")
    }

    @Test
    fun favorite_is_pinned_to_the_top() = runTest {
        val viewModel = viewModel(apps = listOf(coolReader, koreader))

        viewModel.onToggleFavorite(koreader)

        assertThat(viewModel.uiState.value.visible.map { it.label })
            .containsExactly("KOReader", "Cool Reader").inOrder()
    }

    @Test
    fun sort_mode_frequency_orders_by_launches() = runTest {
        val viewModel = viewModel(
            apps = listOf(coolReader, koreader),
            state = LauncherState(
                sortMode = AppSortMode.LAUNCH_FREQUENCY,
                launchCounts = mapOf("org.koreader.launcher" to 3),
            ),
        )

        assertThat(viewModel.uiState.value.visible.map { it.label })
            .containsExactly("KOReader", "Cool Reader").inOrder()
    }

    @Test
    fun launch_returns_intent_and_counts_the_launch() = runTest {
        val viewModel = viewModel(apps = listOf(coolReader))

        val intent = viewModel.launchIntent(coolReader)

        assertThat(intent).isNotNull()
        assertThat(viewModel.uiState.value.launchCounts["org.coolreader"]).isEqualTo(1)
    }

    @Test
    fun launch_without_resolvable_intent_reports_error() = runTest {
        val viewModel = viewModel(apps = listOf(coolReader), launchable = false)

        val intent = viewModel.launchIntent(coolReader)

        assertThat(intent).isNull()
        assertThat(viewModel.uiState.value.error).isEqualTo(LauncherError.LAUNCH_FAILED)
    }

    @Test
    fun catalog_failure_reports_error_and_retry_recovers() = runTest {
        val catalog = FakeAppCatalog(apps = listOf(coolReader), failing = true)
        val viewModel = LauncherViewModel(catalog = catalog, launcherStore = FakeLauncherStore())

        assertThat(viewModel.uiState.value.error).isEqualTo(LauncherError.LOAD_FAILED)

        catalog.failing = false
        viewModel.onRetry()

        assertThat(viewModel.uiState.value.error).isNull()
        assertThat(viewModel.uiState.value.visible.map { it.packageName }).containsExactly("org.coolreader")
    }

    private fun viewModel(
        apps: List<InstalledApp>,
        state: LauncherState = LauncherState(),
        launchable: Boolean = true,
    ) = LauncherViewModel(
        catalog = FakeAppCatalog(apps = apps, launchable = launchable),
        launcherStore = FakeLauncherStore(state),
    )

    private fun app(
        packageName: String,
        label: String,
        isSystem: Boolean = false,
    ) = InstalledApp(
        packageName = packageName,
        activityName = "$packageName.Main",
        label = label,
        versionCode = 1,
        isSystem = isSystem,
    )

    /** Фейк каталога: список, флаг падения и доступность интента. */
    private class FakeAppCatalog(
        private val apps: List<InstalledApp>,
        var launchable: Boolean = true,
        var failing: Boolean = false,
    ) : AppCatalog {
        override val ownPackage: String = "io.github.bookshelf.debug"

        override suspend fun apps(): List<InstalledApp> {
            if (failing) error("каталог недоступен")
            return apps
        }

        // `apply`, а не цепочка: в юнит-тестах методы android.jar возвращают значения по
        // умолчанию (`isReturnDefaultValues`), и `setClassName` вернул бы null вместо Intent.
        override fun launchIntent(app: InstalledApp): Intent? = if (launchable) {
            Intent(Intent.ACTION_MAIN).apply { setClassName(app.packageName, app.activityName) }
        } else {
            null
        }
    }

    /** Фейк хранилища: состояние в памяти, как у настоящего, но без DataStore. */
    private class FakeLauncherStore(initial: LauncherState = LauncherState()) : LauncherStore {
        private val mutableState = MutableStateFlow(initial)

        override val state: Flow<LauncherState> = mutableState

        override suspend fun update(transform: (LauncherState) -> LauncherState) {
            mutableState.update(transform)
        }

        override suspend fun current(): LauncherState = mutableState.value
    }
}
