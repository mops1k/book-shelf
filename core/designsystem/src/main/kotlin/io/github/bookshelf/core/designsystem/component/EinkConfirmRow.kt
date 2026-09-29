package io.github.bookshelf.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.bookshelf.core.designsystem.theme.LocalEinkColors
import io.github.bookshelf.core.designsystem.theme.LocalEinkDimens

/**
 * Подтверждение опасного действия: вопрос и две кнопки.
 *
 * Общий блок дизайн-системы, а не частный приём экрана настроек: так подтверждают удаление и
 * настройки (кнопка в карточке книги), причём карточка не зависит от `feature:settings` — модули
 * одного уровня не должны ссылаться друг на друга.
 *
 * Показывается на месте строки, а не диалогом поверх экрана: на e-ink диалог заставляет
 * перерисовывать кадр целиком, а вопрос рядом с тем, что нажали, читается сразу.
 *
 * Кнопка подтверждения идёт первой и выделена как основная; отмена всегда справа, чтобы «опасная»
 * и «безопасная» кнопки не менялись местами от экрана к экрану.
 */
@Composable
fun EinkConfirmRow(
    question: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val dimens = LocalEinkDimens.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = dimens.gutterSmall),
        verticalArrangement = Arrangement.spacedBy(dimens.gutterSmall),
    ) {
        Text(
            text = question,
            style = MaterialTheme.typography.bodyMedium,
            color = LocalEinkColors.current.foreground,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(dimens.gutterSmall),
        ) {
            EinkButton(
                text = confirmLabel,
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                primary = true,
                enabled = enabled,
            )
            EinkButton(
                text = "Отмена",
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                enabled = enabled,
            )
        }
    }
}
