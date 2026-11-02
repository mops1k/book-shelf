package io.github.bookshelf.feature.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bookshelf.core.applist.icon.AppIconKey
import io.github.bookshelf.core.applist.model.InstalledApp
import io.github.bookshelf.core.designsystem.component.EinkButton
import io.github.bookshelf.core.designsystem.component.EinkDivider
import io.github.bookshelf.core.designsystem.component.EinkEmptyState
import io.github.bookshelf.core.designsystem.component.EinkScaffold
import io.github.bookshelf.core.designsystem.theme.LocalEinkColors
import io.github.bookshelf.core.designsystem.theme.LocalEinkDimens
import io.github.bookshelf.feature.launcher.LauncherViewModel
import io.github.bookshelf.feature.launcher.R
import coil3.compose.AsyncImage

/**
 * Экран «Скрытые приложения» (ADR-015, решение 7).
 *
 * Список спрятанных вручную приложений: у каждого кнопка «Вернуть». Нужен потому, что «Скрыть
 * из списка» без пути назад — ловушка: приложение исчезает из сетки, а найти его больше негде
 * (нашёл Александр 02.11.2026).
 */
@Composable
fun HiddenAppsScreen(
    viewModel: LauncherViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dimens = LocalEinkDimens.current

    EinkScaffold(
        title = stringResource(R.string.launcher_hidden_title),
        onBack = onBack,
        onKeyAction = { action ->
            if (action == io.github.bookshelf.core.designsystem.input.EinkAction.BACK) onBack()
        },
    ) {
        if (state.hiddenApps.isEmpty()) {
            EinkEmptyState(
                title = stringResource(R.string.launcher_hidden_empty_title),
                message = stringResource(R.string.launcher_hidden_empty_message),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = dimens.gutter,
                    vertical = dimens.gutterSmall,
                ),
            ) {
                items(items = state.hiddenApps, key = { app -> app.packageName }) { app ->
                    HiddenAppRow(
                        app = app,
                        onRestore = { viewModel.onUnhide(app.packageName) },
                    )
                }
            }
        }
    }
}

/** Строка скрытого приложения: иконка, название и «Вернуть». */
@Composable
private fun HiddenAppRow(app: InstalledApp, onRestore: () -> Unit) {
    val dimens = LocalEinkDimens.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = dimens.gutterSmall),
            verticalArrangement = Arrangement.spacedBy(dimens.gutterSmall),
        ) {
            androidx.compose.foundation.layout.Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.gutterSmall),
            ) {
                AsyncImage(
                    model = AppIconKey(packageName = app.packageName, versionCode = app.versionCode),
                    contentDescription = null,
                    modifier = Modifier.size(dimens.appListIcon),
                )
                androidx.compose.material3.Text(
                    text = app.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = LocalEinkColors.current.foreground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            EinkButton(
                text = stringResource(R.string.launcher_hidden_restore),
                onClick = onRestore,
            )
        }
        EinkDivider()
    }
}
