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
        displayLarge = einkTextStyle(
            fontFamily = fontFamily, fontSize = body * 1.6f, lineHeight = line * 1.4f,
            weight = FontWeight.Normal,
        ),
        headlineMedium = einkTextStyle(
            fontFamily = fontFamily, fontSize = body * 1.35f, lineHeight = line * 1.2f,
            weight = FontWeight.Medium,
        ),
        titleLarge = einkTextStyle(
            fontFamily = fontFamily, fontSize = body * 1.2f, lineHeight = line * 1.1f,
            weight = FontWeight.Medium,
        ),
        titleMedium = einkTextStyle(
            fontFamily = fontFamily, fontSize = body, lineHeight = line,
            weight = FontWeight.Medium,
        ),
        bodyLarge = einkTextStyle(
            fontFamily = fontFamily, fontSize = body, lineHeight = line,
            weight = FontWeight.Normal,
        ),
        bodyMedium = einkTextStyle(
            fontFamily = fontFamily, fontSize = body * 0.94f, lineHeight = line,
            weight = FontWeight.Normal,
        ),
        bodySmall = einkTextStyle(
            fontFamily = fontFamily, fontSize = body * 0.82f, lineHeight = line * 0.95f,
            weight = FontWeight.Normal,
        ),
        labelLarge = einkTextStyle(
            fontFamily = fontFamily, fontSize = body * 0.94f, lineHeight = line,
            weight = FontWeight.Medium,
        ),
        labelMedium = einkTextStyle(
            fontFamily = fontFamily, fontSize = body * 0.82f, lineHeight = line * 0.95f,
            weight = FontWeight.Normal,
        ),
        // Самый мелкий кегль интерфейса. Им рисуются бейджи на обложке (формат, статус чтения):
        // они лежат поверх картинки и не должны спорить с названием книги под плиткой. По просьбе
        // Александра (28.09.2026) кегль бейджей уменьшен заметно: при 0.72 высота прописных
        // выходила ≈78 % от названия, то есть читалась как второй заголовок, а не как пометка.
        labelSmall = einkTextStyle(
            fontFamily = fontFamily, fontSize = body * 0.6f, lineHeight = line * 0.75f,
            weight = FontWeight.Normal,
        ),
    )
}


/**
 * Стиль текста e-ink: семейство, кегль, высота строки и начертание.
 *
 * Вынесено из `einkTypography` отдельной функцией ради предела длины detekt и заодно убрало
 * двенадцать одинаковых блоков по шесть строк: значения остались теми же.
 */
private fun einkTextStyle(
    fontFamily: FontFamily,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    weight: FontWeight,
): TextStyle = TextStyle(
    fontFamily = fontFamily,
    fontWeight = weight,
    fontSize = fontSize,
    lineHeight = lineHeight,
)
