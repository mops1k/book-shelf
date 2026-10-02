package io.github.bookshelf.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Цвета интерфейса. Единственные носители смысла — чёрный и белый: на grayscale-панели
 * полутона «выцветают», а тени и прозрачность дают артефакты перерисовки.
 *
 * См. `«E-ink UX»` §2.1. Запрещено: градиенты, полупрозрачные оверлеи, серый текст
 * светлее `#767676`, цвет как единственный признак состояния.
 */
@Immutable
data class EinkColors(
    val background: Color,
    val foreground: Color,
    val divider: Color,
    /**
     * Заливка места, где обложки нет.
     *
     * Именно «слегка серый», а не цвет разделителя: тот в светлой теме чёрный, и пустая обложка
     * заливалась чёрным целиком (находка Александра 02.10.2026).
     */
    val placeholder: Color = LIGHT_PLACEHOLDER,
    val disabledBorder: Color,
    val errorBackground: Color,
    val errorForeground: Color,
) {
    /** Инверсия блока: так показывается нажатие, выбор и фокус. */
    val invertedBackground: Color get() = foreground
    val invertedForeground: Color get() = background
}

/** Светлый режим: чёрное на белом. */
/**
 * Серая заливка пустого места обложки в светлой теме.
 *
 * Заметно серее фона: оттенок 0xDDDDDD на e-ink читался почти как белый и место обложки было не
 * отличить от страницы (находка Александра 02.10.2026).
 */
private val LIGHT_PLACEHOLDER = Color(0xFFC0C0C0)

/** Серая заливка пустого места обложки в тёмной теме: чуть светлее фона. */
private val DARK_PLACEHOLDER = Color(0xFF333333)

val LightEinkColors: EinkColors = EinkColors(
    background = Color.White,
    foreground = Color.Black,
    divider = Color.Black,
    placeholder = LIGHT_PLACEHOLDER,
    disabledBorder = Color.Black,
    errorBackground = Color.Black,
    errorForeground = Color.White,
)

/** Тёмный режим: белое на чёрном (для чтения в темноте и для устройств без подсветки). */
val DarkEinkColors: EinkColors = EinkColors(
    background = Color.Black,
    foreground = Color.White,
    divider = Color.White,
    placeholder = DARK_PLACEHOLDER,
    disabledBorder = Color.White,
    errorBackground = Color.White,
    errorForeground = Color.Black,
)

val LocalEinkColors = staticCompositionLocalOf { LightEinkColors }
