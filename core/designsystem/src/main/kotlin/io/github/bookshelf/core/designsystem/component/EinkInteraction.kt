package io.github.bookshelf.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/**
 * Нажатие без «волны»: e-ink не умеет показывать полупрозрачную анимацию — она выглядит
 * как серое пятно и стоит лишнего перерисона («E-ink UX» §5).
 *
 * Единая точка отключения ripple: если ищем, почему где-то остался эффект,
 * смотреть надо только здесь и в компонентах дизайн-системы.
 */
@Composable
fun Modifier.clickableNoIndication(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = this.clickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = null,
    enabled = enabled,
    onClick = onClick,
)
