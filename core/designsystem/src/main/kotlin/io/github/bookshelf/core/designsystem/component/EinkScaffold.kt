package io.github.bookshelf.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import io.github.bookshelf.core.designsystem.input.EinkAction
import io.github.bookshelf.core.designsystem.input.onEinkKeys
import io.github.bookshelf.core.designsystem.refresh.RefreshReason
import io.github.bookshelf.core.designsystem.refresh.RequestRefreshOnEnter
import io.github.bookshelf.core.designsystem.theme.LocalEinkColors
import io.github.bookshelf.core.designsystem.theme.LocalEinkDimens

/**
 * Каркас экрана: заголовок, содержимое, нижняя строка (номер страницы), обработка
 * аппаратных кнопок и полный перерисон при входе на экран.
 *
 * Экран не анимируется при появлении: на e-ink переход — это мгновенная смена кадра,
 * а полный refresh делает «чистый» кадр без остатков предыдущего экрана.
 */
@Composable
fun EinkScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    footer: @Composable (() -> Unit)? = null,
    volumeKeysAsPageTurn: Boolean = true,
    onKeyAction: (EinkAction) -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalEinkColors.current
    val dimens = LocalEinkDimens.current

    RequestRefreshOnEnter(RefreshReason.SCREEN_CHANGED)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .onEinkKeys(volumeKeysAsPageTurn = volumeKeysAsPageTurn, onAction = onKeyAction),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = dimens.touchComfort)
                .padding(horizontal = dimens.gutter, vertical = dimens.gutterSmall),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (onBack != null) {
                EinkButton(text = "← Назад", onClick = onBack)
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = dimens.gutterSmall)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.foreground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.foreground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.gutterSmall),
                content = actions,
            )
        }

        EinkDivider()

        Column(modifier = Modifier.weight(1f).fillMaxWidth(), content = content)

        if (footer != null) {
            EinkDivider()
            footer()
        }
    }
}
