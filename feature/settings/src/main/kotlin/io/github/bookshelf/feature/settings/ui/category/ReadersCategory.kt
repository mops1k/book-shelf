package io.github.bookshelf.feature.settings.ui.category

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.bookshelf.core.designsystem.component.EinkEmptyState
import io.github.bookshelf.core.designsystem.component.EinkListRow
import io.github.bookshelf.core.designsystem.component.EinkSectionHeader
import io.github.bookshelf.core.datastore.ProgressSyncInterval
import io.github.bookshelf.core.designsystem.theme.LocalEinkDimens
import io.github.bookshelf.feature.settings.ReaderProgressAction
import io.github.bookshelf.feature.settings.SettingsUiState
import io.github.bookshelf.feature.settings.SettingsViewModel
import io.github.bookshelf.feature.settings.ui.ToggleRow
import io.github.bookshelf.feature.settings.ui.ValueChoiceRow
import io.github.bookshelf.feature.settings.R

/**
 * Категория «Читалки»: чем открываются книги.
 *
 * Выбор по умолчанию пишется в таблицу `format_defaults`, а не в настройки: её читает
 * `BookContentOpener` при открытии книги, поэтому выбор действительно влияет на поведение.
 */
@Composable
internal fun ReadersCategory(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    val dimens = LocalEinkDimens.current
    val readers = state.installedReaders

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = dimens.gutter),
    ) {
        if (readers.isEmpty()) {
            item(key = "readers-empty") {
                EinkEmptyState(
                    title = stringResource(R.string.settings_readers_missing_title),
                    message = stringResource(R.string.settings_readers_missing_message),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            items(items = readers, key = { reader -> reader.id }) { reader ->
                EinkListRow(
                    title = reader.name,
                    subtitle = reader.packageName,
                    trailing = if (reader.isDefault) {
                        stringResource(R.string.settings_reader_default)
                    } else {
                        stringResource(R.string.settings_reader_choose)
                    },
                    onClick = { viewModel.onSetDefaultReader(reader.id) },
                )
            }
        }

        progressItems(state = state, viewModel = viewModel)
    }
}

/**
 * Блок «Прогресс чтения»: синхронизация с читалкой и её интервал.
 *
 * Переключатель показывается только вместе с механизмом, который за ним стоит: прогресс читается из
 * API-сервиса FBReader, а расписание ставит цепочка фоновых работ (`ReadingProgressSyncWorker`).
 * Варианты интервала берутся из `ProgressSyncInterval` — то есть ровно те, что допускает
 * планировщик; интервал показывается только при включённой синхронизации, иначе он ни на что не
 * влияет.
 */
private fun LazyListScope.progressItems(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    item(key = "progress-header") {
        EinkSectionHeader(title = stringResource(R.string.settings_readers_progress_header))
    }
    item(key = "progress-switch") {
        ToggleRow(
            title = stringResource(R.string.settings_readers_sync),
            subtitle = stringResource(R.string.settings_readers_sync_hint),
            enabled = state.settings.isFbReaderProgressEnabled,
            onToggle = { enabled ->
                viewModel.onReaderProgress(ReaderProgressAction.SetEnabled(enabled))
            },
        )
    }
    item(key = "progress-temp-export") {
        ToggleRow(
            title = stringResource(R.string.settings_readers_temp_export),
            subtitle = stringResource(R.string.settings_readers_temp_export_hint),
            enabled = state.settings.isTempExportEnabled,
            onToggle = { enabled ->
                viewModel.onReaderProgress(ReaderProgressAction.SetTempExport(enabled))
            },
        )
    }
    item(key = "progress-check") {
        EinkListRow(
            title = stringResource(R.string.settings_readers_check),
            subtitle = when {
                state.isCheckingReaderApi -> stringResource(R.string.settings_readers_checking)
                else -> state.readerApiStatus
                    ?: stringResource(R.string.settings_readers_check_hint)
            },
            onClick = { viewModel.onReaderProgress(ReaderProgressAction.CheckApi) },
        )
    }
    if (state.settings.isFbReaderProgressEnabled) {
        item(key = "progress-interval") {
            ValueChoiceRow(
                title = stringResource(R.string.settings_readers_interval),
                options = ProgressSyncInterval.CHOICES_MINUTES,
                selected = state.settings.progressSyncIntervalMinutes,
                labelRes = { R.string.settings_reader_interval_minutes },
                onPick = { minutes ->
                    viewModel.onReaderProgress(ReaderProgressAction.SetInterval(minutes))
                },
                subtitle = stringResource(R.string.settings_readers_interval_hint),
            )
        }
    }
}
