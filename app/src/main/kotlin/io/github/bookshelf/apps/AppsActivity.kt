package io.github.bookshelf.apps

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.bookshelf.EXTRA_ROUTE
import io.github.bookshelf.MainActivity
import io.github.bookshelf.core.datastore.model.SystemBarsMode
import io.github.bookshelf.feature.launcher.LauncherViewModel
import io.github.bookshelf.feature.launcher.ui.LauncherScreen
import io.github.bookshelf.feature.settings.SettingsRoute
import io.github.bookshelf.ui.BookShelfThemeRoot
import io.github.bookshelf.ui.MainViewModel
import io.github.bookshelf.ui.SystemBarsPolicy

/**
 * Отдельный экран «Приложения» — для аппаратной кнопки устройства (ADR-015, фаза 6).
 *
 * Вендорский список назначения кнопок показывает приложения, а не компоненты, поэтому у BookShelf
 * должен быть собственный пункт запуска: «Приложения» открываются сразу списком, без полки
 * за спиной. Вид экрана тот же, что и у вкладки: по решению Александра 02.11.2026 внизу остаётся
 * панель «Полка | Приложения» — «Приложения» активна, «Полка» открывает основное приложение.
 *
 * Своей навигации здесь нет: экран один, а настройки и полка живут в [MainActivity], поэтому
 * переходы идут интентами с маршрутом в [EXTRA_ROUTE].
 */
@AndroidEntryPoint
class AppsActivity : ComponentActivity() {

    /** Тема и настройки общие с основным приложением: одно хранилище, один вид. */
    private val mainViewModel: MainViewModel by viewModels()

    private val launcherViewModel: LauncherViewModel by viewModels()

    private var systemBarsMode: SystemBarsMode = SystemBarsMode.HIDE_ALWAYS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applySystemBars()

        setContent {
            val themeState by mainViewModel.themeState.collectAsStateWithLifecycle()
            val launcherState by launcherViewModel.uiState.collectAsStateWithLifecycle()
            // Режим лаунчера выключен — этот вход ведёт на полку, а не в пустоту.
            LaunchedEffect(launcherState.loading, launcherState.launcherEnabled) {
                if (!launcherState.loading && !launcherState.launcherEnabled) openShelf()
            }
            LaunchedEffect(themeState.systemBarsMode) {
                systemBarsMode = themeState.systemBarsMode
                applySystemBars()
            }
            BookShelfThemeRoot(themeState = themeState) {
                LauncherScreen(
                    viewModel = launcherViewModel,
                    onOpenShelf = ::openShelf,
                    onOpenSettings = ::openSettings,
                )
            }
        }
    }

    /** «Полка» нижней панели: основное приложение, первый экран. */
    private fun openShelf() {
        openMain(route = null)
    }

    /** Шестерёнка рядом с поиском: основное приложение сразу на «Настройках». */
    private fun openSettings() {
        openMain(route = SettingsRoute.route)
    }

    /**
     * Открыть основное приложение (полку или настройки) и закрыть этот экран.
     *
     * `CLEAR_TOP` + `SINGLE_TOP`: если BookShelf уже запущен, он не пересоздаётся, а получает
     * маршрут через `onNewIntent` — так возврат из списка не теряет состояние полки.
     */
    private fun openMain(route: String?) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (route != null) putExtra(EXTRA_ROUTE, route)
        }
        startActivity(intent)
        finish()
    }

    /** Режим системных панелей тот же, что у вкладки «Приложения» в основном приложении. */
    private fun applySystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        val hideBars = SystemBarsPolicy.shouldHideBars(
            mode = systemBarsMode,
            isShelf = false,
            isApps = true,
        )
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (hideBars) {
                hide(WindowInsetsCompat.Type.systemBars())
            } else {
                show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    /** Возврат из фона снова применяет режим панелей: система могла показать их сама. */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applySystemBars()
    }
}
