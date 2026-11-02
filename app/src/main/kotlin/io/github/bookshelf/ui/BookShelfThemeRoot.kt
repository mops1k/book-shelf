package io.github.bookshelf.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import io.github.bookshelf.core.datastore.model.ThemeMode
import io.github.bookshelf.core.designsystem.theme.EinkTheme
import io.github.bookshelf.core.designsystem.theme.LocalInvertCovers
import io.github.bookshelf.core.designsystem.theme.LocalVolumeKeysAsPageTurn

/**
 * Корень оформления BookShelf: тема и настройки, которые читают сами компоненты дизайн-системы.
 *
 * Вынесен из `MainActivity`, потому что корней теперь два: основное приложение и отдельная
 * активити «Приложения» для аппаратной кнопки (ADR-015, фаза 6). Копия этой обвязки во второй
 * активити разошлась бы с первой при первой же правке темы.
 */
@Composable
fun BookShelfThemeRoot(themeState: ThemeState, content: @Composable () -> Unit) {
    EinkTheme(
        darkTheme = themeState.themeMode.isDarkTheme(),
        density = themeState.density,
        fontScale = themeState.fontScale,
    ) {
        // Настройки, которые компоненты читают сами: инверсия обложек (`BookCover`) и клавиши
        // громкости (`EinkScaffold`). Прокидывать их параметрами пришлось бы через каждый экран.
        CompositionLocalProvider(
            LocalInvertCovers provides themeState.invertCovers,
            LocalVolumeKeysAsPageTurn provides themeState.volumeKeysAsPageTurn,
        ) {
            Surface(modifier = Modifier.fillMaxSize()) { content() }
        }
    }
}

/**
 * Тёмная тема с учётом системной настройки.
 *
 * `SYSTEM` нельзя разрешить в ViewModel: для этого нужен `isSystemInDarkTheme()`, то есть
 * композиция. Поэтому `ThemeState` хранит выбор пользователя, а не готовый флаг, и один и тот же
 * выбор даёт разный результат при смене системной темы — без перезапуска приложения.
 */
@Composable
fun ThemeMode.isDarkTheme(): Boolean = when (this) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
}
