package io.github.bookshelf.feature.settings.ui.category

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bookshelf.core.datastore.model.Grouping
import io.github.bookshelf.core.datastore.model.SortOrder
import io.github.bookshelf.core.designsystem.component.EinkConfirmRow
import io.github.bookshelf.core.designsystem.component.EinkErrorBanner
import io.github.bookshelf.core.designsystem.component.EinkListRow
import io.github.bookshelf.core.designsystem.component.EinkSectionHeader
import io.github.bookshelf.core.designsystem.theme.LocalEinkDimens
import io.github.bookshelf.feature.settings.COVER_CACHE_LIMIT_CHOICES_MB
import io.github.bookshelf.feature.settings.DuplicatesUiState
import io.github.bookshelf.feature.settings.DuplicatesViewModel
import io.github.bookshelf.feature.settings.SettingsUiState
import io.github.bookshelf.feature.settings.SettingsViewModel
import io.github.bookshelf.feature.settings.TEMP_COPY_LIMIT_CHOICES_MB
import io.github.bookshelf.feature.settings.ui.ToggleRow
import io.github.bookshelf.feature.settings.ui.ValueChoiceRow
import io.github.bookshelf.feature.settings.ui.ViewPresetChooser
import io.github.bookshelf.feature.settings.ui.displayNameOfTree
import io.github.bookshelf.feature.settings.ui.openAppInfoSettings
import io.github.bookshelf.feature.settings.ui.permissionsOf
import io.github.bookshelf.feature.settings.ui.sourcesSection

/**
 * Категория «Библиотека и скан»: вид и порядок полки, лимиты диска, папки-источники и разрешения.
 *
 * Здесь показано только то, у чего есть потребитель: вид полки и порядок по умолчанию читает
 * `LibraryViewModel`, лимит кэша обложек — `CoverCleanupWorker`, лимит временной копии —
 * `BookContentOpener`, папки и пересканирование — репозитории, разрешения выдаёт система.
 *
 * Сознательно **не** показаны (настройки существуют, но подключить их без вреда нельзя):
 * `gridColumns` — плотность полки уже задаётся размером плитки (`GridCells.Adaptive`);
 * `autoScanOnStart` и `scanSchedule` — синхронизация при возврате в приложение и раз в 15 минут
 * уже работает по решению пользователя; `showMissing` и `missingCleanupDays` — устарели: книги,
 * исчезнувшие с диска, удаляются после полного прохода; `rowsPerPage` — полка листает прокруткой;
 * `metadataParallelism` — `:core:common` не может читать настройки (см. `DefaultAppDispatchers`).
 */
@Composable
internal fun LibraryScanCategory(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    duplicatesViewModel: DuplicatesViewModel,
) {
    val context = LocalContext.current
    val dimens = LocalEinkDimens.current

    // Дубли живут в своей ViewModel: в `SettingsViewModel` уже 29 методов при пороге detekt 30.
    val duplicates by duplicatesViewModel.uiState.collectAsStateWithLifecycle()

    // Источник папок выбирает система, а не приложение: путь на устройстве нам неизвестен.
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = context.displayNameOfTree(uri)
        // Права сохраняет репозиторий (`SourceRepository.addSafSource`) — и сохраняет чтение
        // ВМЕСТЕ С ЗАПИСЬЮ. Прежний вызов `takePersistableUriPermission(uri, 0)` перезаписывал
        // сохранённые права пустым набором, после чего запись в папку (скачивание книг)
        // отклонялась провайдером.
        viewModel.onAddSource(uri = uri.toString(), displayName = name)
    }

    // Права выдаёт система: перезапрос после каждого изменения нужен, иначе строка продолжит
    // показывать «не выдано» при уже выданном.
    var permissions by remember { mutableStateOf(permissionsOf(context)) }
    val permissionRequest = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { permissions = permissionsOf(context) }

    Column(modifier = Modifier.fillMaxSize()) {
        val duplicatesError = duplicates.error
        if (duplicatesError != null) {
            EinkErrorBanner(message = duplicatesError)
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = dimens.gutter),
        ) {
        item(key = "view-preset") {
            ViewPresetChooser(preset = state.settings.viewPreset, onPick = viewModel::onSetViewPreset)
        }

        item(key = "default-sort") {
            ValueChoiceRow(
                title = "Сортировка по умолчанию",
                options = SortOrder.entries,
                selected = state.settings.sortOrder,
                label = ::sortOrderLabel,
                onPick = viewModel::onSetDefaultSortOrder,
                subtitle = "Чем открывается полка. На самой полке порядок можно сменить на время",
            )
        }

        item(key = "default-sort-descending") {
            ToggleRow(
                title = "Обратный порядок",
                subtitle = "От новых к старым, от больших файлов к маленьким — наоборот к выбранной сортировке",
                enabled = state.settings.sortDescending,
                onToggle = viewModel::onSetDefaultSortDescending,
            )
        }

        item(key = "default-grouping") {
            ValueChoiceRow(
                title = "Группировка по умолчанию",
                options = Grouping.entries,
                selected = state.settings.grouping,
                label = ::groupingLabel,
                onPick = viewModel::onSetDefaultGrouping,
                subtitle = "Книги одного автора, серии, папки или формата собираются рядом",
            )
        }

        item(key = "header-library-limits") {
            EinkSectionHeader(title = "Лимиты диска")
        }

        item(key = "cover-cache-limit") {
            ValueChoiceRow(
                title = "Лимит кэша обложек",
                options = COVER_CACHE_LIMIT_CHOICES_MB,
                selected = state.settings.coverCacheLimitMb,
                label = ::megabytesLabel,
                onPick = viewModel::onSetCoverCacheLimitMb,
                subtitle = "Больше лимит — дольше живут обложки, больше места на диске",
            )
        }

        item(key = "temp-copy-limit") {
            ValueChoiceRow(
                title = "Лимит временной копии",
                options = TEMP_COPY_LIMIT_CHOICES_MB,
                selected = state.settings.tempCopyLimitMb,
                label = ::megabytesLabel,
                onPick = viewModel::onSetTempCopyLimitMb,
                subtitle = "Копия нужна книге из облака: больше лимит — откроются крупные файлы, " +
                    "но и места уйдёт больше",
            )
        }

        duplicatesSection(
            state = duplicates,
            onCheck = duplicatesViewModel::onCheck,
            onConfirmDelete = duplicatesViewModel::onConfirmDelete,
            onCancelDelete = duplicatesViewModel::onCancelDelete,
            onConfirmBroken = duplicatesViewModel::onConfirmDeleteBroken,
            onCancelBroken = duplicatesViewModel::onCancelDeleteBroken,
        )

        sourcesSection(
            sources = state.sources,
            isRescanning = state.isRescanning,
            onAddFolder = { folderPicker.launch(null) },
            onRemoveSource = viewModel::onRemoveSource,
            onRescanAll = viewModel::onRescanAll,
        )

        item(key = "header-permissions") {
            EinkSectionHeader(title = "Разрешения", count = permissions.size)
        }
        items(items = permissions, key = { item -> item.permission }) { item ->
            EinkListRow(
                title = item.title,
                subtitle = item.subtitle,
                trailing = if (item.isGranted) "выдано" else "запросить",
                onClick = { permissionRequest.launch(item.permission) },
            )
        }
        item(key = "permissions-appinfo") {
            EinkListRow(
                title = "Разрешения приложения в системе",
                subtitle = "Открыть системную страницу: там же отзываются выданные права",
                onClick = { context.openAppInfoSettings() },
            )
        }
        }
    }
}

/**
 * Блок «Дубли книг»: проверка содержимого и удаление лишних копий.
 *
 * Отдельной функцией, а не блоком внутри экрана: тело экрана и так длинное (порог detekt
 * `LongMethod`), а блок цельный — он знает только своё состояние и пять команд.
 */
private fun LazyListScope.duplicatesSection(
    state: DuplicatesUiState,
    onCheck: () -> Unit,
    onConfirmDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    onConfirmBroken: () -> Unit,
    onCancelBroken: () -> Unit,
) {
    item(key = "duplicates-header") {
        EinkSectionHeader(title = "Дубли книг")
    }
    item(key = "duplicates-check") {
        val busy = state.isChecking || state.isDeleting
        EinkListRow(
            title = "Проверить на дубли",
            subtitle = when {
                state.isChecking -> "Сравниваем содержимое книг…"
                state.isDeleting -> "Удаляем…"
                else -> state.message
                    ?: "Сравнение по содержимому файлов: одинаковые книги можно удалить"
            },
            onClick = { if (!busy) onCheck() },
        )
    }
    if (state.isConfirming) {
        item(key = "duplicates-confirm") {
            EinkConfirmRow(
                question = "Удалить лишние копии (${state.toRemove.size} шт.)? " +
                    "Останется по одной — та, что разложена по папкам.",
                confirmLabel = "Удалить",
                onConfirm = onConfirmDelete,
                onCancel = onCancelDelete,
                enabled = !state.isDeleting,
            )
        }
    }
    if (state.isConfirmingBroken) {
        item(key = "broken-confirm") {
            EinkConfirmRow(
                question = "Удалить битые файлы (${state.broken.size} шт.)? " +
                    "Они не читаются и открыть их нельзя.",
                confirmLabel = "Удалить",
                onConfirm = onConfirmBroken,
                onCancel = onCancelBroken,
                enabled = !state.isDeleting,
            )
        }
    }
}

/** Мегабайты чипом: «200 МБ». */
private fun megabytesLabel(megabytes: Int): String = "$megabytes МБ"

/**
 * Подписи сортировки — те же слова, что в строке сортировки на полке.
 *
 * Продублированы намеренно: экран настроек не должен зависеть от `:feature:library` ради восьми
 * строк, а состав вариантов задаёт перечисление настроек — новый вариант обязан появиться здесь
 * сразу, иначе настройку нельзя будет выставить.
 */
private fun sortOrderLabel(order: SortOrder): String = when (order) {
    SortOrder.TITLE -> "Название"
    SortOrder.AUTHOR -> "Автор"
    SortOrder.SERIES -> "Серия"
    SortOrder.ADDED_AT -> "Дата добавления"
    SortOrder.LAST_OPENED -> "Дата открытия"
    SortOrder.SIZE -> "Размер"
    SortOrder.PROGRESS -> "Прогресс"
    SortOrder.YEAR -> "Год"
}

/** Подписи группировки для чипов: на полке те же варианты, но строчными буквами в подвале. */
private fun groupingLabel(grouping: Grouping): String = when (grouping) {
    Grouping.NONE -> "Без группировки"
    Grouping.SERIES -> "По сериям"
    Grouping.AUTHOR -> "По авторам"
    Grouping.FOLDER -> "По папкам"
    Grouping.FORMAT -> "По форматам"
}
