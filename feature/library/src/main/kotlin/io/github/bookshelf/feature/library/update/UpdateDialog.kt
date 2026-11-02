package io.github.bookshelf.feature.library.update

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bookshelf.core.designsystem.component.EinkButton
import io.github.bookshelf.core.designsystem.theme.LocalEinkColors
import io.github.bookshelf.core.designsystem.theme.LocalEinkDimens
import io.github.bookshelf.feature.library.R

/**
 * Диалог «доступна новая версия» на полке (ADR-018, «E-ink UX» §6.10).
 *
 * Единственный модальный диалог приложения. На e-ink он не анимируется и не отбрасывает тень:
 * карточка с рамкой по центру. «Позже» закрывает до следующей автоматической проверки.
 */
@Composable
fun UpdateDialogHost(viewModel: UpdateViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    if (state.visible) {
        UpdateDialog(
            state = state,
            onDismiss = viewModel::onDismiss,
            // Пока скачивание в приложении не сделано (фаза 3 ADR-018), «Скачать и установить»
            // ведёт на страницу релиза: кнопка не должна быть пустой.
            onDownload = { if (state.pageUrl.isNotBlank()) uriHandler.openUri(state.pageUrl) },
        )
    }
}

/** Карточка диалога обновления. */
@Composable
internal fun UpdateDialog(
    state: UpdateUiState,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
) {
    val colors = LocalEinkColors.current
    val dimens = LocalEinkDimens.current
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .border(dimens.borderAccent, colors.foreground)
                .padding(dimens.gutter),
            verticalArrangement = Arrangement.spacedBy(dimens.gutterSmall),
        ) {
            Text(
                text = stringResource(R.string.update_dialog_title, state.versionName),
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                color = colors.foreground,
            )
            Text(
                text = stringResource(R.string.update_dialog_current_version),
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = colors.foreground,
            )
            if (state.notes.isNotBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = state.notes,
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                        color = colors.foreground,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dimens.gutterSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EinkButton(
                    text = stringResource(R.string.update_dialog_download),
                    onClick = onDownload,
                    modifier = Modifier.weight(1f),
                    primary = true,
                )
                EinkButton(
                    text = stringResource(R.string.update_dialog_later),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
