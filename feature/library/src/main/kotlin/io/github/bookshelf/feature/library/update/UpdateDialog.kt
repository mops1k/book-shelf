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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bookshelf.core.designsystem.component.EinkButton
import io.github.bookshelf.core.designsystem.theme.LocalEinkColors
import io.github.bookshelf.core.designsystem.theme.LocalEinkDimens
import io.github.bookshelf.core.update.download.UpdateDownloadState
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
    if (state.visible) {
        UpdateDialog(
            state = state,
            onDismiss = viewModel::onDismiss,
            onDownload = viewModel::onDownload,
            onInstall = viewModel::onInstall,
            onRequestPermission = viewModel::onRequestPermission,
        )
    }
}

/** Карточка диалога обновления. */
@Composable
internal fun UpdateDialog(
    state: UpdateUiState,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onRequestPermission: () -> Unit,
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
            UpdateDialogStatus(state = state)
            UpdateDialogActions(
                state = state,
                onDownload = onDownload,
                onInstall = onInstall,
                onRequestPermission = onRequestPermission,
                onDismiss = onDismiss,
            )
        }
    }
}

/** Строка состояния под «что нового»: прогресс, ошибка или просьба о разрешении. */
@Composable
private fun UpdateDialogStatus(state: UpdateUiState) {
    val colors = LocalEinkColors.current
    val text = when {
        state.permissionRequired -> stringResource(R.string.update_dialog_permission_hint)
        state.download is UpdateDownloadState.Failed ->
            stringResource(R.string.update_dialog_download_failed, (state.download as UpdateDownloadState.Failed).reason)
        state.download is UpdateDownloadState.Running -> {
            val percent = (state.download as UpdateDownloadState.Running).percent
            if (percent == null) {
                stringResource(R.string.update_dialog_downloading_unknown)
            } else {
                stringResource(R.string.update_dialog_downloading, percent)
            }
        }
        else -> null
    }
    if (text != null) {
        Text(
            text = text,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = colors.foreground,
        )
    }
}

/** Кнопки диалога: скачать, установить, разрешить или отложить. */
@Composable
private fun UpdateDialogActions(
    state: UpdateUiState,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onRequestPermission: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dimens = LocalEinkDimens.current
    val done = state.download is UpdateDownloadState.Done
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(dimens.gutterSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val primaryAction = when {
            state.permissionRequired -> onRequestPermission
            done -> onInstall
            else -> onDownload
        }
        val primaryLabel = when {
            state.permissionRequired -> stringResource(R.string.update_dialog_permission_action)
            done -> stringResource(R.string.update_dialog_install)
            state.download is UpdateDownloadState.Failed -> stringResource(R.string.update_dialog_download)
            else -> stringResource(R.string.update_dialog_download)
        }
        EinkButton(
            text = primaryLabel,
            onClick = primaryAction,
            modifier = Modifier.weight(1f),
            primary = true,
            enabled = state.download !is UpdateDownloadState.Running,
        )
        EinkButton(
            text = stringResource(R.string.update_dialog_later),
            onClick = onDismiss,
            modifier = Modifier.weight(1f),
        )
    }
}
