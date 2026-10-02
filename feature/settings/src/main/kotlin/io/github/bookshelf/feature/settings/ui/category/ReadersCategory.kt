package io.github.bookshelf.feature.settings.ui.category

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.core.graphics.drawable.toBitmap
import io.github.bookshelf.core.datastore.ProgressSyncInterval
import io.github.bookshelf.core.designsystem.component.EinkEmptyState
import io.github.bookshelf.core.designsystem.component.EinkListRow
import io.github.bookshelf.core.designsystem.component.EinkSectionHeader
import io.github.bookshelf.core.designsystem.text.resolve
import io.github.bookshelf.core.designsystem.theme.LocalEinkDimens
import io.github.bookshelf.core.readerlink.mime.ReaderFormatCategory
import io.github.bookshelf.feature.settings.ReaderItemUi
import io.github.bookshelf.feature.settings.ReaderProgressAction
import io.github.bookshelf.feature.settings.ReadersLevelUi
import io.github.bookshelf.feature.settings.SettingsUiState
import io.github.bookshelf.feature.settings.SettingsViewModel
import io.github.bookshelf.feature.settings.ui.ToggleRow
import io.github.bookshelf.feature.settings.ui.ValueChoiceRow
import io.github.bookshelf.feature.settings.R

/**
 * Категория «Читалки»: чем открываются книги.
 *
 * Экран идёт по шагам (Александр, 01.10.2026): категория → форматы категории → читалки формата.
 * Выбор пишется в таблицу `format_defaults`, а не в настройки: её читает открытие книги, поэтому
 * выбор действительно влияет на поведение, а настройки держат только зеркало для отметок.
 */
@Composable
internal fun ReadersCategory(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    val dimens = LocalEinkDimens.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = dimens.gutter),
    ) {
        when (val level = state.readersLevel) {
            ReadersLevelUi.Categories -> categoriesLevel(state = state, viewModel = viewModel)
            is ReadersLevelUi.Formats -> formatsLevel(
                state = state,
                viewModel = viewModel,
                category = level.category,
            )
            is ReadersLevelUi.ReadersOfFormat -> readersOfFormatLevel(
                state = state,
                viewModel = viewModel,
                extension = level.extension,
            )
        }

        progressItems(state = state, viewModel = viewModel)
    }
}

/** Первый уровень: список читалок и категории форматов. */
private fun LazyListScope.categoriesLevel(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    if (state.installedReaders.isEmpty()) {
        item(key = "readers-empty") {
            EinkEmptyState(
                title = stringResource(R.string.settings_readers_missing_title),
                message = stringResource(R.string.settings_readers_missing_message),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    } else {
        // Показываем только читалки: браузеры, «Фото», словари, редакторы и синтезаторы речи
        // отсеиваются в discovery (чёрный список не-читалок + проверка объявленных типов).
        items(items = state.installedReaders, key = { reader -> reader.id }) { reader ->
            readerRow(reader = reader)
        }
    }

    if (state.readerCategories.isNotEmpty()) {
        item(key = "readers-categories-header") {
            EinkSectionHeader(title = stringResource(R.string.settings_reader_formats_title))
        }
        items(
            items = state.readerCategories,
            key = { row -> "category-" + row.category.name },
        ) { row ->
            EinkListRow(
                title = row.category.extensionLabel,
                subtitle = stringResource(
                    R.string.settings_reader_category_summary,
                    row.bound,
                    row.formats,
                ),
                trailing = stringResource(R.string.settings_reader_category_open),
                onClick = { viewModel.onReadersLevel(ReadersLevelUi.Formats(row.category)) },
            )
        }
    }
}

/** Второй уровень: форматы выбранной категории и выбранная для каждого читалка. */
private fun LazyListScope.formatsLevel(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    category: ReaderFormatCategory,
) {
    item(key = "readers-formats-header") {
        EinkSectionHeader(title = category.extensionLabel)
    }
    val rows = state.readerFormats.filter { row -> row.category == category }
    items(items = rows, key = { row -> "format-" + row.extension }) { row ->
        EinkListRow(
            title = row.extension.uppercase(),
            subtitle = row.readerName ?: stringResource(R.string.settings_reader_format_none),
            trailing = stringResource(R.string.settings_reader_format_change),
            onClick = { viewModel.onReadersLevel(ReadersLevelUi.ReadersOfFormat(row.extension)) },
        )
    }
}

/** Третий уровень: читалки, которые умеют выбранный формат. */
private fun LazyListScope.readersOfFormatLevel(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    extension: String,
) {
    val current = state.readerFormats.firstOrNull { row -> row.extension == extension }
    item(key = "readers-format-header") {
        EinkSectionHeader(title = extension.uppercase())
    }
    item(key = "readers-none") {
        EinkListRow(
            // «Не выбрано» — тоже выбор: снять привязку и спросить при следующем открытии книги.
            title = stringResource(R.string.settings_reader_format_none),
            selected = current?.readerId == null,
            onClick = { viewModel.onSetReaderForFormat(extension, null) },
        )
    }
    val candidates = state.installedReaders.filter { reader -> extension in reader.supportedExtensions }
    items(items = candidates, key = { reader -> "reader-" + reader.id }) { reader ->
        EinkListRow(
            title = reader.name,
            subtitle = reader.packageName,
            selected = reader.id == current?.readerId,
            leadingContent = { ReaderIcon(packageName = reader.packageName) },
            onClick = { viewModel.onSetReaderForFormat(extension, reader.id) },
        )
    }
}

/** Строка списка читалок: иконка приложения, имя, пакет и отметка «по умолчанию». */
@Composable
private fun readerRow(reader: ReaderItemUi) {
    EinkListRow(
        title = reader.name,
        subtitle = reader.packageName,
        trailing = if (reader.isDefault) {
            stringResource(R.string.settings_reader_default)
        } else {
            null
        },
        // Иконка приложения: человек узнаёт свою читалку по картинке быстрее, чем по имени пакета.
        leadingContent = { ReaderIcon(packageName = reader.packageName) },
        // Действия у строки нет: читалка выбирается для формата, на втором и третьем уровнях.
        onClick = null,
    )
}

/**
 * Иконка приложения-читалки.
 *
 * Берём её у системы и приводим к одному размеру: чужие иконки приходят разного размера, а на
 * e-ink разнобой в строках заметнее, чем на цветном экране. Если иконку получить не удалось
 * (пакет удалили между кадром и отрисовкой), слот просто остаётся пустым.
 */
@Composable
private fun ReaderIcon(packageName: String) {
    val context = LocalContext.current
    val size = LocalEinkDimens.current.touchMin / 2
    val density = LocalDensity.current
    val drawable = remember(packageName) {
        runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()
    } ?: return
    val bitmap = remember(drawable, size) {
        val pixels = with(density) { size.roundToPx() }
        runCatching { drawable.toBitmap(width = pixels, height = pixels) }.getOrNull()
    } ?: return

    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = null,
        modifier = Modifier.size(size),
    )
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
                else -> state.readerApiStatus?.resolve()
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
