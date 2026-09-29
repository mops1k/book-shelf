package io.github.bookshelf.feature.settings.ui.category

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
                    title = "Читалка не найдена",
                    message = "Установите FBReader — приложение открывает книги в ней. " +
                        "В другую программу книга отправлена не будет.",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            items(items = readers, key = { reader -> reader.id }) { reader ->
                EinkListRow(
                    title = reader.name,
                    subtitle = reader.packageName,
                    trailing = if (reader.isDefault) "по умолчанию" else "выбрать",
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
        EinkSectionHeader(title = "Прогресс чтения")
    }
    item(key = "progress-switch") {
        ToggleRow(
            title = "Синхронизировать прогресс с читалкой",
            subtitle = "Читает позицию из FBReader и показывает процент на полке. Работает, пока " +
                "читалка открыта или свёрнута, а не выгружена системой",
            enabled = state.settings.isFbReaderProgressEnabled,
            onToggle = { enabled ->
                viewModel.onReaderProgress(ReaderProgressAction.SetEnabled(enabled))
            },
        )
    }
    item(key = "progress-check") {
        EinkListRow(
            title = "Проверить связь с читалкой",
            subtitle = when {
                state.isCheckingReaderApi -> "Проверяем…"
                else -> state.readerApiStatus
                    ?: "Узнать, отвечает ли API читалки и какая книга в ней открыта"
            },
            onClick = { viewModel.onReaderProgress(ReaderProgressAction.CheckApi) },
        )
    }
    if (state.settings.isFbReaderProgressEnabled) {
        item(key = "progress-interval") {
            ValueChoiceRow(
                title = "Как часто проверять",
                options = ProgressSyncInterval.CHOICES_MINUTES,
                selected = state.settings.progressSyncIntervalMinutes,
                label = { minutes -> "$minutes мин" },
                onPick = { minutes ->
                    viewModel.onReaderProgress(ReaderProgressAction.SetInterval(minutes))
                },
                subtitle = "Реже — меньше расход батареи, чаще — свежее процент на полке",
            )
        }
    }
}
