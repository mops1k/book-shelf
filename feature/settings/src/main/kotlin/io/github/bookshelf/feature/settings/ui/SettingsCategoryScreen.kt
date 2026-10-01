package io.github.bookshelf.feature.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bookshelf.core.designsystem.component.EinkErrorBanner
import io.github.bookshelf.core.designsystem.component.EinkScaffold
import io.github.bookshelf.core.designsystem.input.EinkAction
import io.github.bookshelf.feature.settings.SettingsSectionKind
import io.github.bookshelf.feature.settings.SettingsViewModel
import io.github.bookshelf.feature.settings.defaultSettingsSections
import io.github.bookshelf.feature.settings.ui.category.AboutCategory
import io.github.bookshelf.feature.settings.ui.category.AppearanceCategory
import io.github.bookshelf.feature.settings.ui.category.EinkCategory
import io.github.bookshelf.feature.settings.ui.category.LibraryScanCategory
import io.github.bookshelf.feature.settings.ui.category.OpdsCategory
import io.github.bookshelf.feature.settings.ui.category.ReadersCategory
import io.github.bookshelf.feature.settings.ui.category.StorageCategory
import io.github.bookshelf.feature.settings.R

/**
 * Экран одной категории настроек (docs/eink-ux.md §6.8).
 *
 * Раздел открывается отдельным экраном, а не раскрывается в общем списке: так в каждой категории
 * помещается то, что действительно относится к делу, и не приходится листать чужие настройки.
 * Заголовок берётся из [defaultSettingsSections] — описание категории живёт рядом с её данными,
 * а не дублируется строкой в UI.
 *
 * @param kind какую категорию показывать.
 * @param onBack возврат к списку настроек.
 * @param onOpenCatalogs переход в браузер каталогов OPDS (живёт в `feature:catalogs`).
 * @param onOpenScan переход к экрану сканирования (живёт в `feature:scan`).
 * @param modifier внешний модификатор экрана.
 * @param viewModel состояние настроек; общее на все категории, поэтому передаётся один раз.
 */
@Composable
fun SettingsCategoryScreen(
    kind: SettingsSectionKind,
    onBack: () -> Unit,
    onOpenCatalogs: () -> Unit,
    onOpenScan: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val title = stringResource(defaultSettingsSections().first { section -> section.kind == kind }.titleRes)

    EinkScaffold(
        title = title,
        subtitle = stringResource(R.string.settings_subtitle_applied),
        modifier = modifier,
        onBack = onBack,
        onKeyAction = { action -> if (action == EinkAction.BACK) onBack() },
    ) {
        val error = state.error
        if (error != null) {
            EinkErrorBanner(
                message = error,
                actionLabel = stringResource(R.string.settings_action_retry),
                onAction = viewModel::onRefreshDiagnostics,
            )
        }

        when (kind) {
            SettingsSectionKind.APPEARANCE -> AppearanceCategory(state = state, viewModel = viewModel)
            SettingsSectionKind.EINK_SCREEN -> EinkCategory(state = state, viewModel = viewModel)
            SettingsSectionKind.LIBRARY_SCAN -> LibraryScanCategory(
                state = state,
                viewModel = viewModel,
                // Дубли — своя ViewModel: у общей уже 29 методов при пороге detekt 30.
                duplicatesViewModel = hiltViewModel(),
                onOpenScan = onOpenScan,
            )
            SettingsSectionKind.READERS -> ReadersCategory(state = state, viewModel = viewModel)
            SettingsSectionKind.OPDS -> OpdsCategory(
                state = state,
                viewModel = viewModel,
                onOpenCatalogs = onOpenCatalogs,
            )
            SettingsSectionKind.STORAGE -> StorageCategory(
                state = state,
                viewModel = viewModel,
                backupViewModel = hiltViewModel(),
                // Выгрузка библиотеки — своя ViewModel: у общей уже девять зависимостей при
                // пороге detekt десять.
                exportViewModel = hiltViewModel(),
                // Оптимизация всех книг — своя ViewModel по той же причине (у общей 29 методов
                // при пороге detekt 30), и потому что у неё собственный долгий прогон с отменой.
                optimizationViewModel = hiltViewModel(),
                copiesViewModel = hiltViewModel(),
            )
            SettingsSectionKind.ABOUT -> AboutCategory(state = state, viewModel = viewModel)
        }
    }
}
