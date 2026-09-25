package io.github.bookshelf.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Цвета интерфейса. Единственные носители смысла — чёрный и белый: на grayscale-панели
 * полутона «выцветают», а тени и прозрачность дают артефакты перерисовки.
 *
 * См. `docs/eink-ux.md` §2.1. Запрещено: градиенты, полупрозрачные оверлеи, серый текст
 * светлее `#767676`, цвет как единственный признак состояния.
 */
@Immutable
data class EinkColors(
    val background: Color,
    val foreground: Color,
    val divider: Color,
    val disabledBorder: Color,
    val errorBackground: Color,
    val errorForeground: Color,
) {
    /** Инверсия блока: так показывается нажатие, выбор и фокус. */
    val invertedBackground: Color get() = foreground
    val invertedForeground: Color get() = background
}

/** Светлый режим: чёрное на белом. */
val LightEinkColors: EinkColors = EinkColors(
    background = Color.White,
    foreground = Color.Black,
    divider = Color.Black,
    disabledBorder = Color.Black,
    errorBackground = Color.Black,
    errorForeground = Color.White,
)

/** Тёмный режим: белое на чёрном (для чтения в темноте и для устройств без подсветки). */
val DarkEinkColors: EinkColors = EinkColors(
    background = Color.Black,
    foreground = Color.White,
    divider = Color.White,
    disabledBorder = Color.White,
    errorBackground = Color.White,
    errorForeground = Color.Black,
)

val LocalEinkColors = staticCompositionLocalOf { LightEinkColors }
