package io.github.bookshelf.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Размеры интерфейса («E-ink UX» §2.3).
 *
 * Значения не «на глаз»: [touchMin] — требование доступности (48 dp), [touchComfort] — размер
 * строки списка, по которой реально попадает палец на устройстве с медленным тачем.
 */
@Immutable
data class EinkDimens(
    val touchMin: Dp = 48.dp,
    val touchComfort: Dp = 64.dp,
    val gutterSmall: Dp = 12.dp,
    val gutter: Dp = 16.dp,
    val gutterLarge: Dp = 24.dp,
    val radius: Dp = 0.dp,
    val focusRing: Dp = 2.dp,
    val border: Dp = 1.dp,
    val borderAccent: Dp = 2.dp,
    /**
     * Минимальная ширина ячейки сетки обложек.
     *
     * Число колонок не задаётся константой: `GridCells.Adaptive` укладывает столько обложек,
     * сколько влезает при этой минимальной ширине. На экране Bigme B6 (571 dp по ширине) это ровно
     * четыре книги в ряд, на узком телефоне — две-три, и обложки нигде не вырождаются в иконки.
     */
    val coverMinWidth: Dp = 130.dp,
    val rowHeight: Dp = touchComfort,
) {
    companion object {
        fun forDensity(mode: EinkDensity): EinkDimens = when (mode) {
            // Крупная плотность — четыре книги в ряд: три колонки на телефоне давали обложки
            // на пол-экрана, а помещалось их меньше, чем влезает при той же читаемости текста.
            EinkDensity.LARGE -> EinkDimens()
            EinkDensity.NORMAL -> EinkDimens(
                touchComfort = 56.dp,
                coverMinWidth = 118.dp,
                rowHeight = 56.dp,
            )
            EinkDensity.COMPACT -> EinkDimens(
                touchComfort = 48.dp,
                coverMinWidth = 104.dp,
                rowHeight = 48.dp,
                gutter = 12.dp,
            )
        }
    }
}

/**
 * Три режима плотности из настроек. [COMPACT] не опускается ниже `touchMin` — экономия места
 * не может ломать попадание пальцем.
 */
enum class EinkDensity {
    LARGE,
    NORMAL,
    COMPACT,
}

val LocalEinkDimens = staticCompositionLocalOf { EinkDimens() }
