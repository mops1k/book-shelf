package io.github.bookshelf.feature.settings.ui.category

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.github.bookshelf.core.designsystem.component.EinkButton
import io.github.bookshelf.core.designsystem.component.EinkListRow
import io.github.bookshelf.core.designsystem.component.EinkSectionHeader
import io.github.bookshelf.core.designsystem.theme.LocalEinkDimens
import io.github.bookshelf.feature.settings.SettingsUiState
import io.github.bookshelf.feature.settings.SettingsViewModel
import io.github.bookshelf.feature.settings.ui.openAppInfoSettings

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
                title = "Версия",
                subtitle = "Версия приложения, как её видит система",
                trailing = appVersion(context),
            )
        }
        item(key = "about-eink-header") {
            EinkSectionHeader(title = "Диагностика e-ink")
        }
        item(key = "about-eink-vendor") {
            EinkListRow(
                title = "Вендор",
                subtitle = "От вендора зависит, какие команды обновления экрана доступны",
                trailing = diagnostics.vendorLabel,
            )
        }
        item(key = "about-eink-screen") {
            EinkListRow(
                title = "Экран",
                subtitle = "Что за экран у устройства: бумага бывает чёрно-белой и цветной",
                trailing = diagnostics.screenLabel,
            )
        }
        item(key = "about-eink-commands") {
            EinkListRow(
                title = "Команды вендора",
                subtitle = "Режим обновления, интервал полного кадра и подсветка: их даёт только " +
                    "вендорский контроллер",
                trailing = if (diagnostics.hasVendorCommands) "доступны" else "недоступны",
            )
        }
        item(key = "about-eink-fast") {
            EinkListRow(
                title = "Быстрый режим обновления",
                subtitle = "Мгновенная перерисовка без призраков прежнего кадра",
                trailing = if (diagnostics.supportsFastMode) "доступен" else "нет",
            )
        }
        item(key = "about-refresh") {
            EinkListRow(
                title = "Обновить диагностику",
                subtitle = "Перечитать данные вендора: они читаются один раз при запуске",
                onClick = viewModel::onRefreshDiagnostics,
            )
        }
        item(key = "about-permissions") {
            EinkButton(
                text = "Разрешения приложения в системе",
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
    return version?.takeIf { it.isNotBlank() } ?: "неизвестно"
}
