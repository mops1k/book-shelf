package io.github.bookshelf.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import io.github.bookshelf.core.designsystem.theme.LocalEinkColors
import io.github.bookshelf.core.designsystem.theme.LocalEinkDimens

/** Разделитель: единственный способ показать иерархию без теней и elevation. */
@Composable
fun EinkDivider(modifier: Modifier = Modifier, accent: Boolean = false) {
    val colors = LocalEinkColors.current
    val dimens = LocalEinkDimens.current
    HorizontalDivider(
        modifier = modifier,
        thickness = if (accent) dimens.borderAccent else dimens.border,
        color = colors.divider,
    )
}

/** Заголовок группы («Серии», «Авторы») с опциональным счётчиком. */
@Composable
fun EinkSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
) {
    val colors = LocalEinkColors.current
    val dimens = LocalEinkDimens.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.gutter, vertical = dimens.gutterSmall),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = colors.foreground,
        )
        if (count != null) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = colors.foreground,
            )
        }
    }
}

/**
 * Строка списка: высота не меньше `touchComfort`, поэтому по ней реально попасть пальцем
 * на устройстве с медленным тачем.
 */
@Composable
fun EinkListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: String? = null,
    /**
     * Кнопка или значок справа от подписи.
     *
     * Нужен там, где у строки есть собственное действие (меню каталога «⋯»): подпись `trailing`
     * остаётся текстом, а действие — отдельным элементом, по которому можно попасть пальцем.
     */
    trailingContent: (@Composable () -> Unit)? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    hasDivider: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val colors = LocalEinkColors.current
    val dimens = LocalEinkDimens.current
    val textColor = if (selected) colors.invertedForeground else colors.foreground

    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (selected) colors.invertedBackground else colors.background)
                .then(
                    if (onClick != null && enabled) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = dimens.gutter, vertical = dimens.gutterSmall),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor,
                        maxLines = 1,
                        // Многоточие обязательно: подпись умной полки («Умная полка · жанр: …,
                        // статус: …») длиннее строки, и без него текст обрывался на середине слова —
                        // выглядело как потерянные данные, а не как сокращение.
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (trailing != null) {
                Text(
                    text = trailing,
                    style = MaterialTheme.typography.labelMedium,
                    color = textColor,
                    modifier = Modifier.padding(start = dimens.gutterSmall),
                )
            }
            trailingContent?.invoke()
        }
        if (!enabled) {
            // Отключённое состояние — пунктиром нельзя (нет API), поэтому рамкой и без реакции.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(dimens.border, colors.disabledBorder),
            )
        }
        if (hasDivider) {
            EinkDivider(modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}
