package io.github.bookshelf.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp

/**
 * Корневая тема приложения.
 *
 * Задача темы — не «украсить», а *выключить* всё, что плохо работает на электронной бумаге:
 * переходы, тени, полупрозрачность, цветные состояния. Material 3 берётся как система раскладки
 * и доступности, но его палитра полностью переопределена двумя цветами.
 */
@Composable
fun EinkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    density: EinkDensity = EinkDensity.LARGE,
    fontScale: FontScale = FontScale.S100,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkEinkColors else LightEinkColors
    val dimens = remember(density) { EinkDimens.forDensity(density) }
    val typography = remember(fontScale) { einkTypography(fontScale) }

    CompositionLocalProvider(
        LocalEinkColors provides colors,
        LocalEinkDimens provides dimens,
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterialColorScheme(darkTheme),
            typography = typography,
            shapes = EinkShapes,
            content = content,
        )
    }
}

/**
 * Палитра Material 3 сводится к двум цветам. Важно переопределить именно все «поверхности»:
 * если оставить значения по умолчанию, серые оттенки M3 протекут в компоненты и на панели
 * появятся серые плашки.
 */
private fun EinkColors.toMaterialColorScheme(dark: Boolean): ColorScheme = if (dark) {
    darkColorScheme(
        primary = foreground,
        onPrimary = background,
        primaryContainer = background,
        onPrimaryContainer = foreground,
        secondary = foreground,
        onSecondary = background,
        tertiary = foreground,
        onTertiary = background,
        background = background,
        onBackground = foreground,
        surface = background,
        onSurface = foreground,
        surfaceVariant = background,
        onSurfaceVariant = foreground,
        surfaceTint = background,
        outline = divider,
        outlineVariant = divider,
        error = errorForeground,
        onError = errorBackground,
        errorContainer = errorBackground,
        onErrorContainer = errorForeground,
        scrim = foreground,
        inverseSurface = foreground,
        inverseOnSurface = background,
    )
} else {
    lightColorScheme(
        primary = foreground,
        onPrimary = background,
        primaryContainer = background,
        onPrimaryContainer = foreground,
        secondary = foreground,
        onSecondary = background,
        tertiary = foreground,
        onTertiary = background,
        background = background,
        onBackground = foreground,
        surface = background,
        onSurface = foreground,
        surfaceVariant = background,
        onSurfaceVariant = foreground,
        surfaceTint = background,
        outline = divider,
        outlineVariant = divider,
        error = errorForeground,
        onError = errorBackground,
        errorContainer = errorBackground,
        onErrorContainer = errorForeground,
        scrim = foreground,
        inverseSurface = foreground,
        inverseOnSurface = background,
    )
}

/** Скругления по умолчанию нулевые: резкие границы чище выглядят и меньше «грязнят» панель. */
private val EinkShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp),
)
