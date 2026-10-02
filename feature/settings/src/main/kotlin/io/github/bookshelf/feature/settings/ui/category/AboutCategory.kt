package io.github.bookshelf.feature.settings.ui.category

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import io.github.bookshelf.core.designsystem.component.EinkButton
import io.github.bookshelf.core.designsystem.component.EinkListRow
import io.github.bookshelf.core.designsystem.text.resolve
import io.github.bookshelf.core.designsystem.component.EinkSectionHeader
import io.github.bookshelf.core.designsystem.theme.LocalEinkDimens
import io.github.bookshelf.feature.settings.EinkDiagnosticsUi
import io.github.bookshelf.feature.settings.SettingsUiState
import io.github.bookshelf.feature.settings.SettingsViewModel
import io.github.bookshelf.feature.settings.ui.openAppInfoSettings
import io.github.bookshelf.feature.settings.R

/**
 * Категория «О приложении и диагностика».
 *
 * Здесь показывается только то, что действительно известно системе: версия из пакетного менеджера
 * и данные вендора из `SettingsUiState.diagnostics`. Диагностику можно перечитать — вендорские
 * контроллеры читают состояние при старте, а после перезагрузки устройства оно может измениться.
 * Пустое место вместо лицензий и вендорских настроек остаётся заглушкой: их нечем наполнить.
 */
@Composable
internal fun AboutCategory(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    val context = LocalContext.current
    val dimens = LocalEinkDimens.current
    val diagnostics = state.diagnostics

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = dimens.gutter),
    ) {
        item(key = "about-version") {
            EinkListRow(
                title = stringResource(R.string.settings_about_version),
                subtitle = stringResource(R.string.settings_about_version_hint),
                trailing = appVersion(context),
            )
        }
        item(key = "about-eink-header") {
            EinkSectionHeader(title = stringResource(R.string.settings_about_diagnostics))
        }
        einkDiagnosticsRows(diagnostics = diagnostics)

        einkCapabilitiesRows(diagnostics = diagnostics)

        item(key = "about-refresh") {
            EinkListRow(
                title = stringResource(R.string.settings_about_refresh_diagnostics),
                subtitle = stringResource(R.string.settings_about_refresh_diagnostics_hint),
                onClick = viewModel::onRefreshDiagnostics,
            )
        }
        item(key = "about-permissions") {
            EinkButton(
                text = stringResource(R.string.settings_permission_app_info),
                onClick = { context.openAppInfoSettings() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = dimens.gutterSmall),
            )
        }
    }
}

/**
 * Версия приложения для строки «Версия».
 *
 * Читаем через `runCatching`: версия — приятная мелочь, из-за которой экран «О приложении»
 * развалиться не должен. Если система почему-то не отдала пакет, честнее показать «неизвестно»,
 * чем пустое место, которое выглядит как незаполненные данные.
 */
private fun appVersion(context: Context): String {
    val version = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull()
    return version?.takeIf { it.isNotBlank() } ?: context.getString(R.string.settings_about_unknown)
}


/** Строки диагностики e-ink: устройство, экран и доступность вендорских команд. */
private fun LazyListScope.einkDiagnosticsRows(diagnostics: EinkDiagnosticsUi) {
    item(key = "about-eink-vendor") {
        EinkListRow(
            title = stringResource(R.string.settings_about_vendor),
            subtitle = stringResource(R.string.settings_about_vendor_hint),
            trailing = diagnostics.vendorLabel
                ?: stringResource(R.string.settings_diagnostics_unknown_device),
        )
    }
    item(key = "about-eink-screen") {
        EinkListRow(
            title = stringResource(R.string.settings_about_screen),
            subtitle = stringResource(R.string.settings_about_screen_hint),
            trailing = diagnostics.screenLabel.resolve(),
        )
    }
}


/** Возможности вендорского контроллера: команды обновления и быстрый режим. */
private fun LazyListScope.einkCapabilitiesRows(diagnostics: EinkDiagnosticsUi) {
    item(key = "about-eink-commands") {
        EinkListRow(
            title = stringResource(R.string.settings_about_vendor_commands),
            subtitle = stringResource(R.string.settings_about_vendor_commands_hint),
            trailing = if (diagnostics.hasVendorCommands) {
                stringResource(R.string.settings_about_available)
            } else {
                stringResource(R.string.settings_about_unavailable)
            },
        )
    }
    item(key = "about-eink-fast") {
        EinkListRow(
            title = stringResource(R.string.settings_about_fast_mode),
            subtitle = stringResource(R.string.settings_about_fast_mode_hint),
            trailing = if (diagnostics.supportsFastMode) {
                stringResource(R.string.settings_about_available_short)
            } else {
                stringResource(R.string.settings_about_no)
            },
        )
    }
}
