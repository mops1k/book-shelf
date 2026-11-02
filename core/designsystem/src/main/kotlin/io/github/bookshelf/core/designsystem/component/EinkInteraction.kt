package io.github.bookshelf.core.designsystem.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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

/**
 * Нажатие и долгое нажатие без «волны» (ADR-015: долгий тап в списке приложений).
 *
 * Второй вариант нужен там, где обычный тап запускает приложение, а долгий открывает меню:
 * `clickable` такого не умеет, а `combinedClickable` с ripple на e-ink оставляет серое пятно.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.combinedClickableNoIndication(
    enabled: Boolean = true,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
): Modifier = this.combinedClickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = null,
    enabled = enabled,
    onClick = onClick,
    onLongClick = onLongClick,
)
