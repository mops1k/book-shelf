package io.github.bookshelf.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Размеры интерфейса (docs/eink-ux.md §2.3).
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
    val gridColumns: Int = 2,
    val rowHeight: Dp = touchComfort,
) {
    companion object {
        fun forDensity(mode: EinkDensity): EinkDimens = when (mode) {
            EinkDensity.LARGE -> EinkDimens()
            EinkDensity.NORMAL -> EinkDimens(touchComfort = 56.dp, gridColumns = 3, rowHeight = 56.dp)
            EinkDensity.COMPACT -> EinkDimens(touchComfort = 48.dp, gridColumns = 4, rowHeight = 48.dp, gutter = 12.dp)
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
