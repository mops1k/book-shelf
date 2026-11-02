package io.github.bookshelf.feature.launcher

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.applist.model.InstalledApp
import org.junit.Test

/**
 * Скрытые приложения: список для экрана возврата (ADR-015, решение 7).
 *
 * «Скрыть из списка» без обратного пути — ловушка, поэтому скрытые модели выводятся отдельно
 * и по названию: на экране «Скрытые приложения» их ищут глазами.
 */
class LauncherUiStateTest {

    private fun app(packageName: String, label: String) = InstalledApp(
        packageName = packageName,
        activityName = "$packageName.Main",
        label = label,
        versionCode = 1L,
        isSystem = false,
    )

    @Test
    fun hidden_apps_are_derived_from_the_hidden_set() {
        val state = LauncherUiState(
            apps = listOf(app("b", "Браузер"), app("a", "Архиватор")),
            hidden = setOf("a"),
        )

        assertThat(state.hiddenApps.map { it.packageName }).containsExactly("a")
    }

    @Test
    fun hidden_apps_are_sorted_by_label() {
        val state = LauncherUiState(
            apps = listOf(app("z", "Аврора"), app("a", "Яндекс")),
            hidden = setOf("z", "a"),
        )

        assertThat(state.hiddenApps.map { it.label }).containsExactly("Аврора", "Яндекс").inOrder()
    }

    @Test
    fun nothing_is_hidden_when_the_set_is_empty() {
        val state = LauncherUiState(apps = listOf(app("a", "Архиватор")))

        assertThat(state.hiddenApps).isEmpty()
    }
}
