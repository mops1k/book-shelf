package io.github.bookshelf.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import io.github.bookshelf.core.designsystem.theme.LocalEinkColors
import io.github.bookshelf.core.designsystem.theme.LocalEinkDimens

/**
 * Нижняя панель вкладок (ADR-015).
 *
 * Две текстовые кнопки равной ширины, активная — инверсией (чёрная плашка, белый текст): на e-ink
 * цвет и подчёркивание плохо различимы, а инверсия читается без напряжения. Высота — компактная
 * (`EinkDimens.tabBarHeight`, 48 dp): панель стоит на двух главных экранах и не должна отнимать
 * у сетки больше строки; анимации перехода не используются.
 *
 * Панель живёт только на двух экранах — полке и списке приложений («E-ink UX» §6.9).
 */
@Composable
fun EinkTabBar(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalEinkColors.current
    val dimens = LocalEinkDimens.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.gutter, vertical = dimens.gutterSmall),
        horizontalArrangement = Arrangement.spacedBy(dimens.gutterSmall),
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = dimens.tabBarHeight)
                    .background(if (selected) colors.invertedBackground else colors.background)
                    .border(width = dimens.borderAccent, color = colors.divider)
                    .clickableNoIndication(onClick = { onSelect(index) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (selected) colors.invertedForeground else colors.foreground,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
