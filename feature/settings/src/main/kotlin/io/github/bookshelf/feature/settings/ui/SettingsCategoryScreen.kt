package io.github.bookshelf.feature.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
 * @param modifier внешний модификатор экрана.
 * @param viewModel состояние настроек; общее на все категории, поэтому передаётся один раз.
 */
@Composable
fun SettingsCategoryScreen(
    kind: SettingsSectionKind,
    onBack: () -> Unit,
    onOpenCatalogs: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val title = defaultSettingsSections().first { section -> section.kind == kind }.title

    EinkScaffold(
        title = title,
        subtitle = "Применяется сразу",
        modifier = modifier,
        onBack = onBack,
        onKeyAction = { action -> if (action == EinkAction.BACK) onBack() },
    ) {
        val error = state.error
        if (error != null) {
            EinkErrorBanner(
                message = error,
                actionLabel = "Повторить",
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
            )
            SettingsSectionKind.ABOUT -> AboutCategory(state = state, viewModel = viewModel)
        }
    }
}
