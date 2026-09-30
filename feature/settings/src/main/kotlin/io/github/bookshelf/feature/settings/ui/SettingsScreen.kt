package io.github.bookshelf.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bookshelf.core.designsystem.component.EinkButton
import io.github.bookshelf.core.designsystem.component.EinkErrorBanner
import io.github.bookshelf.core.designsystem.component.EinkListRow
import io.github.bookshelf.core.designsystem.component.EinkScaffold
import io.github.bookshelf.core.designsystem.input.EinkAction
import io.github.bookshelf.core.designsystem.theme.LocalEinkColors
import io.github.bookshelf.core.designsystem.theme.LocalEinkDimens
import io.github.bookshelf.feature.settings.SettingsSectionKind
import io.github.bookshelf.feature.settings.SettingsUiState
import io.github.bookshelf.feature.settings.SettingsViewModel
import io.github.bookshelf.feature.settings.R

/**
 * Настройки (docs/eink-ux.md §6.8): список категорий и сброс к умолчаниям.
 *
 * Экран — только оглавление: каждая категория открывается своим экраном, потому что настроек
 * много, а на e-ink листать чужое долго. Так список настроек читается одной страницей, а внутри
 * категории видно ровно то, что к ней относится.
 *
 * @param onBack возврат на предыдущий экран.
 * @param onOpenCategory открытие экрана категории.
 * @param modifier внешний модификатор экрана.
 * @param viewModel состояние настроек.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenCategory: (SettingsSectionKind) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    EinkScaffold(
        title = stringResource(R.string.settings_title),
        subtitle = stringResource(R.string.settings_subtitle_applied),
        modifier = modifier,
        onBack = onBack,
        onKeyAction = { action -> if (action == EinkAction.BACK) onBack() },
        footer = { ResetFooter(state = state, viewModel = viewModel) },
    ) {
        val error = state.error
        if (error != null) {
            EinkErrorBanner(
                message = error,
                actionLabel = stringResource(R.string.settings_action_retry),
                onAction = viewModel::onRefreshDiagnostics,
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = LocalEinkDimens.current.gutter),
        ) {
            items(items = state.sections, key = { section -> section.kind.name }) { section ->
                EinkListRow(
                    title = stringResource(section.titleRes),
                    subtitle = stringResource(section.summaryRes),
                    onClick = { onOpenCategory(section.kind) },
                )
            }
        }
    }
}

/**
 * Подвал экрана настроек: сброс к умолчаниям.
 *
 * Сброс стоит отдельно от списка, а не строкой среди категорий: он возвращает к умолчаниям
 * **все** настройки сразу, и прикасаться к нему между делом пользователь не должен. Поэтому же
 * сначала спрашивается подтверждение, а во время самого сброса кнопка гаснет — повторное нажатие
 * не запустит вторую запись.
 *
 * @param state нужен признак подтверждения и признак идущего сброса.
 * @param viewModel команды «спросить», «отменить» и «сбросить».
 */
@Composable
private fun ResetFooter(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    val dimens = LocalEinkDimens.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.gutter, vertical = dimens.gutterSmall),
        verticalArrangement = Arrangement.spacedBy(dimens.gutterSmall),
    ) {
        if (state.isConfirmingReset) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dimens.gutterSmall),
            ) {
                EinkButton(
                    text = stringResource(R.string.settings_action_reset),
                    onClick = viewModel::onResetToDefaults,
                    modifier = Modifier.weight(1f),
                    primary = true,
                )
                EinkButton(
                    text = stringResource(R.string.settings_action_cancel),
                    onClick = { viewModel.onSetResetConfirmation(false) },
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            EinkButton(
                text = if (state.isResetting) {
                    stringResource(R.string.settings_resetting)
                } else {
                    stringResource(R.string.settings_action_reset_defaults)
                },
                onClick = { viewModel.onSetResetConfirmation(true) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isResetting,
            )
        }

        Text(
            text = stringResource(R.string.settings_reset_note),
            style = MaterialTheme.typography.labelMedium,
            color = LocalEinkColors.current.foreground,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
