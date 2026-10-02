package io.github.bookshelf.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import io.github.bookshelf.core.designsystem.theme.LocalEinkColors
import io.github.bookshelf.core.designsystem.theme.LocalEinkDimens

/**
 * Поле ввода без анимаций Material: `BasicTextField` под своей рамкой.
 *
 * Нужно для поиска по библиотеке и для формы добавления каталога OPDS, где важны
 * крупный курсор и отсутствие подчёркиваний с анимацией.
 */
@Composable
fun EinkTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    imeAction: ImeAction = ImeAction.Done,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    suffix: String? = null,
    onImeAction: (() -> Unit)? = null,
    /**
     * Куда поставить фокус.
     *
     * Нужно поле поиска: открыв экран поиска, человек на e-ink не должен ещё раз попадать
     * по строке ввода — курсор ставится сразу («E-ink UX» §2). Компонент про экран
     * ничего не знает и фокус сам не запрашивает.
     */
    focusRequester: FocusRequester? = null,
) {
    val colors = LocalEinkColors.current
    val dimens = LocalEinkDimens.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(dimens.gutterSmall / 2),
    ) {
        if (label != null) {
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = colors.foreground)
        }
        EinkTextInputRow(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            placeholder = placeholder,
            suffix = suffix,
            imeAction = imeAction,
            keyboardType = keyboardType,
            visualTransformation = visualTransformation,
            onImeAction = onImeAction,
            focusRequester = focusRequester,
        )
    }
}

/**
 * Строка ввода: рамка, подсказка, поле и суффикс.
 *
 * Вынесена из [EinkTextField] отдельной функцией: сам компонент перешагнул порог `LongMethod`,
 * а строка ввода читается отдельно от подписи над ней.
 */
@Composable
private fun EinkTextInputRow(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    singleLine: Boolean,
    placeholder: String?,
    suffix: String?,
    imeAction: ImeAction,
    keyboardType: KeyboardType,
    visualTransformation: VisualTransformation,
    onImeAction: (() -> Unit)?,
    focusRequester: FocusRequester?,
) {
    val colors = LocalEinkColors.current
    val dimens = LocalEinkDimens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = dimens.touchMin)
            .background(colors.background)
            .border(dimens.border, colors.divider)
            .padding(horizontal = dimens.gutterSmall, vertical = dimens.gutterSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty() && placeholder != null) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.foreground,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = singleLine,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.foreground),
                cursorBrush = SolidColor(colors.foreground),
                visualTransformation = visualTransformation,
                keyboardOptions = KeyboardOptions(imeAction = imeAction, keyboardType = keyboardType),
                keyboardActions = KeyboardActions(
                    onDone = { onImeAction?.invoke() },
                    onSearch = { onImeAction?.invoke() },
                    onGo = { onImeAction?.invoke() },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (focusRequester != null) {
                            Modifier.focusRequester(focusRequester)
                        } else {
                            Modifier
                        },
                    ),
            )
        }
        if (suffix != null) {
            Text(
                text = suffix,
                style = MaterialTheme.typography.labelMedium,
                color = colors.foreground,
                modifier = Modifier.padding(start = dimens.gutterSmall),
            )
        }
    }
}

/**
 * Флажок/переключатель в виде инвертируемого квадрата: не зависит от анимаций Material
 * и одинаково читается на grayscale-панели.
 */
@Composable
fun EinkCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = LocalEinkColors.current
    val dimens = LocalEinkDimens.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = dimens.touchComfort)
            .clickableNoIndication(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = dimens.gutterSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.gutterSmall),
    ) {
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = dimens.touchMin / 2, minHeight = dimens.touchMin / 2)
                .background(if (checked) colors.invertedBackground else colors.background)
                .border(dimens.borderAccent, colors.divider)
                .padding(dimens.gutterSmall / 2),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Text(
                    text = "×",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.invertedForeground,
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.foreground,
            modifier = Modifier.weight(1f),
        )
    }
}
