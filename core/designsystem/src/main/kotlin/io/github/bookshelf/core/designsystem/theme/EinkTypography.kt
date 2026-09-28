package io.github.bookshelf.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** Базовый кегль UI — 17 sp, а не телефонные 14: на e-ink мелкий текст «рвётся». */
const val BASE_FONT_SIZE_SP = 17

/** Множитель межстрочного интервала: на e-ink тесные строки сливаются в серое пятно. */
private const val LINE_HEIGHT_FACTOR = 1.4f

/** Коэффициенты пользовательского масштаба: +15 %, +30 %, +50 % к базовому кеглю. */
private const val SCALE_FACTOR_115 = 1.15f
private const val SCALE_FACTOR_130 = 1.30f
private const val SCALE_FACTOR_150 = 1.50f

/** Пользовательский масштаб шрифта из настроек (docs/eink-ux.md §2.2). */
enum class FontScale(val factor: Float) {
    S100(1.0f),
    S115(SCALE_FACTOR_115),
    S130(SCALE_FACTOR_130),
    S150(SCALE_FACTOR_150),
}

/**
 * Типографика интерфейса. Никаких Light/Thin начертаний и разряжения букв: на электронной
 * бумаге тонкие штрихи пропадают.
 */
fun einkTypography(
    scale: FontScale = FontScale.S100,
    fontFamily: FontFamily = FontFamily.Default,
): Typography {
    val body: TextUnit = (BASE_FONT_SIZE_SP * scale.factor).sp
    val line: TextUnit = (BASE_FONT_SIZE_SP * scale.factor * LINE_HEIGHT_FACTOR).sp

    return Typography(
        displayLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = body * 1.6f,
            lineHeight = line * 1.4f,
        ),
        headlineMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = body * 1.35f,
            lineHeight = line * 1.2f,
        ),
        titleLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = body * 1.2f,
            lineHeight = line * 1.1f,
        ),
        titleMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = body,
            lineHeight = line,
        ),
        bodyLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = body,
            lineHeight = line,
        ),
        bodyMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = body * 0.94f,
            lineHeight = line,
        ),
        bodySmall = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = body * 0.82f,
            lineHeight = line * 0.95f,
        ),
        labelLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = body * 0.94f,
            lineHeight = line,
        ),
        labelMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = body * 0.82f,
            lineHeight = line * 0.95f,
        ),
    )
}
