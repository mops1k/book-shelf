package io.github.bookshelf.feature.launcher.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/**
 * Где на экране стоит плитка приложения, по которой нажали долго.
 *
 * Координаты — в корне окна (`positionInRoot`), размер — сама плитка с подписью. По ним меню
 * ставится рядом с иконкой ([MenuPlacement]): на e-ink это важно, иначе непонятно, к какому
 * приложению относятся действия.
 */
data class AppAnchor(
    val position: IntOffset = IntOffset.Zero,
    val size: IntSize = IntSize.Zero,
)

/**
 * Запомнить, где на экране оказалась плитка: по этим координатам ставится меню долгого нажатия.
 *
 * `positionInRoot` не зависит от прокрутки той части, где лежит плитка, поэтому привязка остаётся
 * верной и в сетке, и в списке.
 */
fun Modifier.anchoredTo(onAnchor: (AppAnchor) -> Unit): Modifier = onGloballyPositioned { coordinates ->
    val position = coordinates.positionInRoot()
    onAnchor(
        AppAnchor(
            position = IntOffset(position.x.roundToInt(), position.y.roundToInt()),
            size = coordinates.size,
        ),
    )
}
