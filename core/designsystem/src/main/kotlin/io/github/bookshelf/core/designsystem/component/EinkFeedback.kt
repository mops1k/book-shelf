package io.github.bookshelf.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import io.github.bookshelf.core.designsystem.theme.LocalEinkColors
import io.github.bookshelf.core.designsystem.theme.LocalEinkDimens

/** Пустое состояние: объясняет, что делать, и не содержит иллюстраций (они «шумят» на e-ink). */
@Composable
fun EinkEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = LocalEinkColors.current
    val dimens = LocalEinkDimens.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(dimens.gutterLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimens.gutter, Alignment.CenterVertically),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = colors.foreground,
            textAlign = TextAlign.Center,
        )
        if (message != null) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.foreground,
                textAlign = TextAlign.Center,
            )
        }
        if (actionLabel != null && onAction != null) {
            EinkButton(text = actionLabel, onClick = onAction, primary = true)
        }
    }
}

/**
 * Прогресс сканирования/загрузки. Обновлять не чаще раза в секунду: частые перерисоновки
 * на электронной бумаге вредны (docs/eink-ux.md §5).
 */
@Composable
fun EinkProgress(
    caption: String,
    progress: Float,
    modifier: Modifier = Modifier,
    detail: String? = null,
) {
    val colors = LocalEinkColors.current
    val dimens = LocalEinkDimens.current
    val fraction = progress.coerceIn(0f, 1f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(dimens.gutter),
        verticalArrangement = Arrangement.spacedBy(dimens.gutterSmall),
    ) {
        Text(text = caption, style = MaterialTheme.typography.bodyLarge, color = colors.foreground)
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth(),
            color = colors.foreground,
            trackColor = colors.background,
            strokeCap = StrokeCap.Butt,
        )
        if (detail != null) {
            Text(text = detail, style = MaterialTheme.typography.bodySmall, color = colors.foreground)
        }
    }
}

/** Ошибка: инвертированная плашка с текстом, без иконок и цветовой кодировки. */
@Composable
fun EinkErrorBanner(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = LocalEinkColors.current
    val dimens = LocalEinkDimens.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.errorBackground)
            .border(dimens.borderAccent, colors.errorBackground)
            .padding(dimens.gutter),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.errorForeground,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null && onAction != null) {
            Box(modifier = Modifier.padding(start = dimens.gutterSmall)) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.errorForeground,
                    modifier = Modifier
                        .border(dimens.border, colors.errorForeground)
                        .padding(dimens.gutterSmall)
                        .clickableNoIndication(onClick = onAction),
                )
            }
        }
    }
}
