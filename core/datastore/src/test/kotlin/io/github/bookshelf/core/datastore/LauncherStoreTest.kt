package io.github.bookshelf.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.testing.TestAppDispatchers
import io.github.bookshelf.core.datastore.launcher.DataStoreLauncherStore
import io.github.bookshelf.core.datastore.model.AppSortMode
import io.github.bookshelf.core.datastore.model.LauncherState
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Хранилище состояния лаунчера (ADR-015).
 *
 * Проверяется то, без чего список приложений ведёт себя непредсказуемо: пустой файл даёт
 * документированные умолчания, запись читается обратно без потерь, битый JSON не роняет
 * приложение, а незнакомые поля (файл от более новой версии) не мешают чтению.
 */
class LauncherStoreTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun dataStore(fileName: String = "launcher.preferences_pb"): DataStore<Preferences> {
        val file: File = temporaryFolder.newFolder("store-${System.nanoTime()}").resolve(fileName)
        return createLauncherDataStore(
            produceFile = { file },
            dispatchers = TestAppDispatchers(),
        )
    }

    private fun store(dataStore: DataStore<Preferences> = dataStore()) = DataStoreLauncherStore(dataStore)

    @Test
    fun empty_store_returns_documented_defaults() = runBlocking {
        assertThat(store().state.first()).isEqualTo(LauncherState())
    }

    @Test
    fun defaults_hide_system_apps_and_sort_by_name() {
        val defaults = LauncherState()

        assertThat(defaults.showSystemApps).isFalse()
        assertThat(defaults.sortMode).isEqualTo(AppSortMode.NAME)
        assertThat(defaults.favoritePackages).isEmpty()
        assertThat(defaults.hiddenPackages).isEmpty()
        assertThat(defaults.launchCounts).isEmpty()
        // Пункт «Приложения» для кнопок устройства выключен, пока пользователь не включит.
        assertThat(defaults.appsItemVisible).isFalse()
        // Решение 02.11.2026: в роли HOME BACK сворачивает приложение.
        assertThat(defaults.collapseInsteadOfExit).isTrue()
    }

    @Test
    fun update_round_trips_every_field() = runBlocking {
        val store = store()
        val target = LauncherState(
            favoritePackages = setOf("org.coolreader"),
            hiddenPackages = setOf("com.android.settings"),
            sortMode = AppSortMode.LAUNCH_FREQUENCY,
            showSystemApps = true,
            launchCounts = mapOf("org.coolreader" to 7),
            appsItemVisible = true,
            collapseInsteadOfExit = false,
        )

        store.update { target }

        assertThat(store.state.first()).isEqualTo(target)
    }

    @Test
    fun corrupt_json_falls_back_to_defaults() = runBlocking {
        val dataStore = dataStore()
        dataStore.edit { preferences -> preferences[LAUNCHER_STATE_KEY] = "{это не json" }

        assertThat(DataStoreLauncherStore(dataStore).state.first()).isEqualTo(LauncherState())
    }

    @Test
    fun unknown_fields_in_json_are_ignored() = runBlocking {
        val dataStore = dataStore()
        val withUnknownField = """
            {"favoritePackages":["org.coolreader"],"futureFlag":true}
        """.trimIndent()
        dataStore.edit { preferences -> preferences[LAUNCHER_STATE_KEY] = withUnknownField }

        val state = DataStoreLauncherStore(dataStore).state.first()

        assertThat(state.favoritePackages).containsExactly("org.coolreader")
        assertThat(state.sortMode).isEqualTo(AppSortMode.NAME)
    }

    @Test
    fun update_is_transactional_and_applies_function(): Unit = runBlocking {
        val store = store()

        store.update { current -> current.copy(hiddenPackages = current.hiddenPackages + "ru.hidden") }
        store.update { current -> current.copy(hiddenPackages = current.hiddenPackages + "ru.other") }

        assertThat(store.state.first().hiddenPackages).containsExactly("ru.hidden", "ru.other")
    }

    @Test
    fun launch_increments_counter_once_per_launch() {
        val once = LauncherState().withLaunch("org.coolreader")
        val twice = once.withLaunch("org.coolreader")

        assertThat(once.launchCounts["org.coolreader"]).isEqualTo(1)
        assertThat(twice.launchCounts["org.coolreader"]).isEqualTo(2)
    }

    @Test
    fun launch_does_not_touch_other_packages() {
        val state = LauncherState(launchCounts = mapOf("a" to 3)).withLaunch("b")

        assertThat(state.launchCounts).containsExactly("a", 3, "b", 1)
    }

    private companion object {
        /**
         * Тот же ключ, что и в бою: тест пишет битый/незнакомый JSON прямо в него, иначе проверять
         * устойчивость было бы нечем.
         */
        val LAUNCHER_STATE_KEY: Preferences.Key<String> = stringPreferencesKey("launcher.state_json")
    }
}
