package io.github.bookshelf.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import io.github.bookshelf.core.designsystem.theme.LocalEinkColors
import io.github.bookshelf.core.designsystem.theme.LocalEinkDimens

/**
 * Кнопка без ripple и без скруглений: состояние показывается инверсией блока,
 * а не «волной» («E-ink UX» §5).
 *
 * [primary] = инверсия (чёрный фон, белый текст) для главного действия на экране.
 */
@Composable
fun EinkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
) {
    val colors = LocalEinkColors.current
    val dimens = LocalEinkDimens.current

    val fill = when {
        !enabled -> colors.background
        primary -> colors.invertedBackground
    else -> colors.background
    }
    val content = when {
        !enabled -> colors.foreground
        primary -> colors.invertedForeground
        else -> colors.foreground
    }

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = dimens.touchMin, minWidth = dimens.touchMin)
            .background(fill)
            .border(
                width = if (enabled) dimens.borderAccent else dimens.border,
                color = colors.divider,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = dimens.gutter, vertical = dimens.gutterSmall),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Пункт меню действий: та же кнопка, но во всю ширину.
 *
 * В меню команды идут столбцом («E-ink UX» §6.2): цель крупная, подпись не переносится,
 * а порядок читается сверху вниз — в отличие от ряда кнопок в шапке, где они «уезжают» вниз
 * и перекрывают содержимое.
 */
@Composable
fun EinkMenuButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    EinkButton(
        text = text,
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
    )
}

/**
 * Чип фильтра: выбранное состояние — инверсия, число рядом с названием помогает понять,
 * сколько книг останется после применения (фасетные фильтры, «Архитектура» §3.5).
 *
 * Подпись центрируется внутри чипа: когда чип шире текста (растянут по строке), прижатая влево
 * подпись выглядит как обрезанная.
 */
@Composable
fun EinkChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int? = null,
    enabled: Boolean = true,
) {
    val colors = LocalEinkColors.current
    val dimens = LocalEinkDimens.current

    Row(
        modifier = modifier
            .defaultMinSize(minHeight = dimens.touchMin)
            .background(if (selected) colors.invertedBackground else colors.background)
            .border(dimens.border, colors.divider)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = dimens.gutterSmall, vertical = dimens.gutterSmall),
        verticalAlignment = Alignment.CenterVertically,
        // Зазор между подписью и числом: без него «Космическая фантастика43» читалось как одно
        // слово (подтверждено на устройстве 28.09.2026).
        horizontalArrangement = Arrangement.spacedBy(
            space = dimens.gutterSmall / 2,
            alignment = Alignment.CenterHorizontally,
        ),
    ) {
        val textColor = if (selected) colors.invertedForeground else colors.foreground
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (count != null) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = textColor,
            )
        }
    }
}
