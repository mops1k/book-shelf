package io.github.bookshelf

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.bookshelf.core.designsystem.theme.EinkTheme
import io.github.bookshelf.core.eink.EinkController
import io.github.bookshelf.navigation.BookShelfNavHost
import io.github.bookshelf.ui.EinkRefreshBridge
import io.github.bookshelf.ui.MainViewModel
import javax.inject.Inject

/**
 * Единственная Activity.
 *
 * Здесь сходятся три вещи, которые больше нигде не должны встречаться:
 * * тема применяется ко всему приложению — смена плотности/кегля/тёмного режима работает
 *   без перезапуска (docs/eink-ux.md §2);
 * * `EinkRefreshBridge` связывает дизайн-систему с вендорским слоем e-ink (docs/architecture.md §6);
 * * навигационный граф собирается из feature-модулей, сама Activity про экраны не знает.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    @Inject
    lateinit var einkController: EinkController

    override fun onCreate(savedInstanceState: Bundle?) {
        // На e-ink splash — статичный белый кадр без иконки и анимации.
        installSplashScreen()
        super.onCreate(savedInstanceState)

        setContent {
            val themeState by viewModel.themeState.collectAsStateWithLifecycle()

            EinkTheme(
                darkTheme = themeState.darkTheme,
                density = themeState.density,
                fontScale = themeState.fontScale,
            ) {
                EinkRefreshBridge(controller = einkController) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        BookShelfNavHost()
                    }
                }
            }
        }
    }
}
