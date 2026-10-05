package io.github.bookshelf.feature.settings.ui.category

import androidx.annotation.StringRes
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
import io.github.bookshelf.feature.settings.installedReaderPackages
import io.github.bookshelf.feature.settings.progressSourcesSummary
import io.github.bookshelf.feature.settings.visibleProgressSources
import io.github.bookshelf.core.readerlink.progress.ProgressSourceId
import io.github.bookshelf.feature.settings.readersFoundText
import io.github.bookshelf.feature.settings.ui.ToggleRow
import io.github.bookshelf.feature.settings.ui.ValueChoiceRow
import io.github.bookshelf.feature.settings.ui.openAppInfoSettings
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
            ReadersLevelUi.Defaults -> defaultsLevel(state = state)
            ReadersLevelUi.Progress -> progressLevel(state = state, viewModel = viewModel)
        }
    }
}

/**
 * Первый уровень: короткое меню раздела.
 *
 * Требования Александра (05.10.2026): список читалок — своим экраном («выбор читалок по умолчанию
 * надо тоже сделать отдельным экраном»), прогресс чтения — своим, «временный экспорт и проверка связи
 * между прогрессом и форматами», а форматы остались разделом внизу. До этого первый уровень
 * показывал все читалки, категории форматов и весь блок прогресса сразу.
 */
private fun LazyListScope.categoriesLevel(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    item(key = "readers-defaults-entry") {
        EinkListRow(
            title = stringResource(R.string.settings_readers_defaults_header),
            subtitle = readersFoundText(state.installedReaders.size).resolve(),
            trailing = stringResource(R.string.settings_reader_category_open),
            onClick = { viewModel.onReadersLevel(ReadersLevelUi.Defaults) },
        )
    }
    // Прогресс чтения — отдельным подэкраном: переключателей стало шесть (FBReader, KOReader,
    // CoolReader, PocketBook, Librera, XReader), и на первом уровне от блока остаётся строка со
    // сводкой. Текст сводки лежит рядом с настройками, а не здесь.
    item(key = "readers-progress-entry") {
        EinkListRow(
            title = stringResource(R.string.settings_readers_progress_header),
            subtitle = progressSourcesSummary(state.settings, installedReaderPackages(state)).resolve(),
            trailing = stringResource(R.string.settings_reader_category_open),
            onClick = { viewModel.onReadersLevel(ReadersLevelUi.Progress) },
        )
    }
    item(key = "readers-temp-export") {
        ToggleRow(
            title = stringResource(R.string.settings_readers_temp_export),
            subtitle = stringResource(R.string.settings_readers_temp_export_hint),
            enabled = state.settings.isTempExportEnabled,
            onToggle = { enabled ->
                viewModel.onReaderProgress(ReaderProgressAction.SetTempExport(enabled))
            },
        )
    }
    item(key = "readers-check-api") {
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

/**
 * Экран «Читалки по умолчанию»: установленные читалки и отметка, за кем закреплён формат.
 *
 * Отдельным экраном, а не списком на первом уровне: читалок на устройстве бывает больше десятка, и
 * они выдавливали с экрана всё остальное (замечание Александра 05.10.2026).
 */
private fun LazyListScope.defaultsLevel(state: SettingsUiState) {
    if (state.installedReaders.isEmpty()) {
        item(key = "readers-empty") {
            EinkEmptyState(
                title = stringResource(R.string.settings_readers_missing_title),
                message = stringResource(R.string.settings_readers_missing_message),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        return
    }
    // Показываем только читалки: браузеры, «Фото», словари, редакторы и синтезаторы речи
    // отсеиваются в discovery (чёрный список не-читалок + проверка объявленных типов).
    items(items = state.installedReaders, key = { reader -> reader.id }) { reader ->
        readerRow(reader = reader)
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
 * Подэкран «Прогресс чтения»: по переключателю на читалку и общий интервал.
 *
 * Каждая интеграция включается отдельно, потому что условия у них разные: FBReader отдаёт позицию
 * через свой сервис, пока читалка жива, KOReader — файлом рядом с книгой (доступ ко всей памяти не
 * нужен), а читалкам на общей памяти (CoolReader, PocketBook, Librera, XReader) он понадобится.
 * Временный экспорт книги и проверка связи с читалкой живут на первом уровне, между этой строкой и
 * форматами (требование Александра 05.10.2026).
 * Расписание одно на всех: цепочку фоновых работ ставит `ReadingProgressSyncWorker`, и варианты
 * интервала берутся из `ProgressSyncInterval` — ровно те, что допускает планировщик. Интервал
 * показывается, когда включён хотя бы один источник: иначе он ни на что не влияет.
 */
private fun LazyListScope.progressLevel(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    item(key = "progress-header") {
        EinkSectionHeader(title = stringResource(R.string.settings_readers_progress_header))
    }
    // Доступ ко всей памяти: без него половина переключателей ничего не даст, и человек должен
    // узнать это здесь, а не гадать, почему процента нет.
    if (!state.settings.isAllFilesAccessGranted) {
        item(key = "progress-access") {
            val context = LocalContext.current
            EinkListRow(
                title = stringResource(R.string.settings_readers_progress_no_access),
                subtitle = stringResource(R.string.settings_readers_progress_no_access_hint),
                trailing = stringResource(R.string.settings_readers_progress_grant),
                // Ведём в системный экран приложения: только там выдаётся «Доступ ко всем файлам».
                onClick = { context.openAppInfoSettings() },
            )
        }
    }
    readerProgressSwitches(state = state, viewModel = viewModel)
    readerProgressInterval(state = state, viewModel = viewModel)
}

/**
 * Переключатели источников прогресса — по одному на читалку.
 *
 * Списком данных, а не копиями `ToggleRow`: читалок с прогрессом становится больше, и добавление
 * следующей не должно удлинять функцию списка (у неё порог detekt по длине уже выбран) и не должно
 * требовать четырёх правок в одном месте.
 */
private fun LazyListScope.readerProgressSwitches(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    // Удалённой читалки в переключателях нет: показываем источники только установленных читалок.
    progressSwitches(state)
        .filter { switch -> switch.source in visibleProgressSources(state) }
        .forEach { switch ->
        item(key = "progress-${switch.key}") {
            ToggleRow(
                title = stringResource(switch.title),
                subtitle = stringResource(switch.hint),
                enabled = switch.enabled,
                onToggle = { enabled -> viewModel.onReaderProgress(switch.action(enabled)) },
            )
        }
    }
}

/** Что показать в блоке «Прогресс чтения»: ключ строки, тексты, состояние и что записать. */
private data class ProgressSwitch(
    val key: String,
    /** Источник прогресса: по нему видно, установлена ли читалка источника. */
    val source: ProgressSourceId,
    @StringRes val title: Int,
    @StringRes val hint: Int,
    val enabled: Boolean,
    val action: (Boolean) -> ReaderProgressAction,
)

private fun progressSwitches(state: SettingsUiState) = listOf(
    ProgressSwitch(
        key = "fbreader",
        source = ProgressSourceId.FBREADER_AIDL,
        title = R.string.settings_readers_sync,
        hint = R.string.settings_readers_sync_hint,
        enabled = state.settings.isFbReaderProgressEnabled,
        action = { ReaderProgressAction.SetEnabled(it) },
    ),
    ProgressSwitch(
        key = "koreader",
        source = ProgressSourceId.KOREADER_SIDECAR,
        title = R.string.settings_readers_koreader,
        hint = R.string.settings_readers_koreader_hint,
        enabled = state.settings.isKoReaderProgressEnabled,
        action = { ReaderProgressAction.SetKoReaderEnabled(it) },
    ),
    ProgressSwitch(
        key = "coolreader",
        source = ProgressSourceId.COOLREADER_DB,
        title = R.string.settings_readers_coolreader,
        hint = R.string.settings_readers_coolreader_hint,
        enabled = state.settings.isCoolReaderProgressEnabled,
        action = { ReaderProgressAction.SetCoolReaderEnabled(it) },
    ),
    ProgressSwitch(
        key = "pocketbook",
        source = ProgressSourceId.POCKETBOOK_DB,
        title = R.string.settings_readers_pocketbook,
        hint = R.string.settings_readers_pocketbook_hint,
        enabled = state.settings.isPocketBookProgressEnabled,
        action = { ReaderProgressAction.SetPocketBookEnabled(it) },
    ),
    ProgressSwitch(
        key = "librera",
        source = ProgressSourceId.LIBRERA_JSON,
        title = R.string.settings_readers_librera,
        hint = R.string.settings_readers_librera_hint,
        enabled = state.settings.isLibreraProgressEnabled,
        action = { ReaderProgressAction.SetLibreraEnabled(it) },
    ),
    ProgressSwitch(
        key = "xreader",
        source = ProgressSourceId.XREADER_CACHE,
        title = R.string.settings_readers_xreader,
        hint = R.string.settings_readers_xreader_hint,
        enabled = state.settings.isXReaderProgressEnabled,
        action = { ReaderProgressAction.SetXReaderEnabled(it) },
    ),
)

/**
 * Интервал опроса — отдельным блоком.
 *
 * Вынесено из [progressItems] не ради красоты: функция списка уже на пределе длины, а интервал —
 * самостоятельная часть (он показывается только при включённом источнике и меняет расписание).
 */
private fun LazyListScope.readerProgressInterval(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    // Интервал один на все читалки: цепочка фоновых работ общая, поэтому показываем её, когда
    // включён хотя бы один источник.
    if (!state.settings.isProgressSyncEnabled) return
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
