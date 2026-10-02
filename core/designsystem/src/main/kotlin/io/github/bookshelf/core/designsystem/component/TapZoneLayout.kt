package io.github.bookshelf.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Раскладка «три вертикальные зоны» («E-ink UX» §3.1):
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
    val centerFraction = (1f - leftFraction - rightFraction).coerceAtLeast(MIN_CENTER_FRACTION)

    Box(modifier = modifier.fillMaxSize()) {
        content()

        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(leftFraction.coerceAtLeast(MIN_SIDE_FRACTION))
                    .fillMaxHeight()
                    .clickableNoIndication(onClick = onPrevious),
            )
            // Без обработчика зону не рисуем вовсе: отключённый Box всё равно перехватывает
            // касание и блокирует кнопки под собой.
            if (onCenter != null) {
                Box(
                    modifier = Modifier
                        .weight(centerFraction)
                        .fillMaxHeight()
                        .clickableNoIndication(onClick = { onCenter?.invoke() }),
                )
            } else {
                // Пустое место вместо зоны: без него вес уходил боковым зонам, и они
                // закрывали собой кнопки в середине экрана.
                Spacer(modifier = Modifier.weight(centerFraction))
            }
            Box(
                modifier = Modifier
                    .weight(rightFraction.coerceAtLeast(MIN_SIDE_FRACTION))
                    .fillMaxHeight()
                    .clickableNoIndication(onClick = onNext),
            )
        }
    }
}

private const val DEFAULT_SIDE_FRACTION = 0.25f

/** Ниже этого центр перестаёт быть центром: зона должна оставаться нажимаемой. */
private const val MIN_CENTER_FRACTION = 0.1f

/** Слишком узкая боковая зона не попадает под палец. */
private const val MIN_SIDE_FRACTION = 0.05f
