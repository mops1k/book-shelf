package io.github.bookshelf.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Раскладка «три вертикальные зоны» (docs/eink-ux.md §3.1):
 * слева — назад/предыдущая страница, центр — открыть, справа — далее.
 *
 * Доли настраиваются: на 6" удобнее 25/50/25, на 10.3" — 15/70/15.
 * Зоны не перекрываются и не анимируются.
 *
 * TODO(Sprint 4): долгое нажатие в центре — контекстное меню книги (`combinedClickable`).
 */
@Composable
fun TapZoneLayout(
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    leftFraction: Float = DEFAULT_SIDE_FRACTION,
    rightFraction: Float = DEFAULT_SIDE_FRACTION,
    onCenter: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val centerFraction = (1f - leftFraction - rightFraction).coerceAtLeast(0.1f)

    Box(modifier = modifier.fillMaxSize()) {
        content()

        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(leftFraction.coerceAtLeast(0.05f))
                    .fillMaxHeight()
                    .clickableNoIndication(onClick = onPrevious),
            )
            Box(
                modifier = Modifier
                    .weight(centerFraction)
                    .fillMaxHeight()
                    .clickableNoIndication(enabled = onCenter != null, onClick = { onCenter?.invoke() }),
            )
            Box(
                modifier = Modifier
                    .weight(rightFraction.coerceAtLeast(0.05f))
                    .fillMaxHeight()
                    .clickableNoIndication(onClick = onNext),
            )
        }
    }
}

private const val DEFAULT_SIDE_FRACTION = 0.25f
