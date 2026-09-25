package io.github.bookshelf.feature.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bookshelf.core.designsystem.component.EinkListRow
import io.github.bookshelf.core.designsystem.component.EinkScaffold
import io.github.bookshelf.core.designsystem.component.EinkSectionHeader
import io.github.bookshelf.core.designsystem.component.TapZoneLayout
import io.github.bookshelf.core.designsystem.input.EinkAction
import io.github.bookshelf.core.designsystem.paging.rememberEinkPagerState
import io.github.bookshelf.core.designsystem.theme.LocalEinkColors
import io.github.bookshelf.core.designsystem.theme.LocalEinkDimens
import io.github.bookshelf.feature.settings.EinkDiagnosticsUi
import io.github.bookshelf.feature.settings.ReaderItemUi
import io.github.bookshelf.feature.settings.SettingsSectionKind
import io.github.bookshelf.feature.settings.SettingsViewModel

/** Сколько разделов настроек помещается на «страницу». */
private const val SETTINGS_PAGE_SIZE = 10

/**
 * Настройки: разделы, диагностика e-ink и состояние читалок (docs/eink-ux.md §6.8).
 *
 * Значения подписываются словами («Обновление экрана: Полное»), а не только переключателем.
 * Каждый раздел открывается отдельным экраном — переход отдаётся навигации в `:app`
 * через [onOpenSection] (Sprint 5).
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenSection: (SettingsSectionKind) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dimens = LocalEinkDimens.current
    val pager = rememberEinkPagerState(pageSize = SETTINGS_PAGE_SIZE, totalItems = state.sections.size)

    LaunchedEffect(state.sections.size) { pager.updateTotalItems(state.sections.size) }

    EinkScaffold(
        title = "Настройки",
        subtitle = "Значения подписаны словами, а не только переключателем",
        modifier = modifier,
        onBack = onBack,
        onKeyAction = { action ->
            when (action) {
                EinkAction.PREVIOUS_PAGE -> pager.previous()
                EinkAction.NEXT_PAGE -> pager.next()
                EinkAction.OPEN -> state.sections.getOrNull(pager.offset)?.let { section ->
                    onOpenSection(section.kind)
                }

                EinkAction.BACK -> onBack()
                else -> Unit
            }
        },
        footer = {
            Text(
                text = pager.statusLabel("разделов"),
                style = MaterialTheme.typography.labelMedium,
                color = LocalEinkColors.current.foreground,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.gutter, vertical = dimens.gutterSmall),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = dimens.gutter),
        ) {
            EinkListRow(
                title = "Устройство",
                subtitle = diagnosticsSummary(state.diagnostics),
                trailing = if (state.diagnostics.isEinkDevice) "e-ink" else "не e-ink",
            )
            EinkListRow(
                title = "Читалки",
                subtitle = "Найдено приложений: ${state.installedReaders.size}",
                trailing = readersSummary(state.installedReaders),
            )

            EinkSectionHeader(title = "Разделы", count = state.sections.size)

            TapZoneLayout(
                onPrevious = pager::previous,
                onNext = pager::next,
                modifier = Modifier.weight(1f),
            ) {
                val visible = state.sections.drop(pager.offset).take(pager.pageSize)
                LazyColumn(modifier = Modifier.fillMaxSize(), userScrollEnabled = false) {
                    items(items = visible, key = { section -> section.kind.name }) { section ->
                        EinkListRow(
                            title = section.title,
                            subtitle = section.summary,
                            onClick = { onOpenSection(section.kind) },
                        )
                    }
                }
            }

            StubNotice(sprint = 5)
        }
    }
}

/** Строка диагностики вендора: «e-ink: да · вендор: Onyx Boox · быстрый режим: поддерживается». */
private fun diagnosticsSummary(diagnostics: EinkDiagnosticsUi): String = buildString {
    append("e-ink: ")
    append(if (diagnostics.isEinkDevice) "да" else "нет")
    append(" · вендор: ")
    append(diagnostics.vendorLabel)
    append(" · быстрый режим: ")
    append(if (diagnostics.supportsFastMode) "поддерживается" else "нет")
}

/** Выбрана ли читалка по умолчанию хотя бы для одного формата. */
private fun readersSummary(readers: List<ReaderItemUi>): String =
    if (readers.any { reader -> reader.isDefault }) "выбрана" else "не выбрана"

/** Пометка этапа каркаса: видно, что экран ещё не реализован, и в каком спринте будет. */
@Composable
private fun StubNotice(sprint: Int) {
    val colors = LocalEinkColors.current
    Text(
        text = "Раздел в разработке — Спринт $sprint",
        style = MaterialTheme.typography.labelMedium,
        color = colors.foreground,
        modifier = Modifier.padding(vertical = LocalEinkDimens.current.gutterSmall),
    )
}
