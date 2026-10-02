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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bookshelf.core.datastore.model.Grouping
import io.github.bookshelf.core.datastore.model.SortOrder
import io.github.bookshelf.core.designsystem.component.EinkConfirmRow
import io.github.bookshelf.core.designsystem.component.EinkErrorBanner
import io.github.bookshelf.core.designsystem.component.EinkListRow
import io.github.bookshelf.core.designsystem.component.EinkSectionHeader
import io.github.bookshelf.core.designsystem.text.resolve
import io.github.bookshelf.core.designsystem.text.resolveJoined
import io.github.bookshelf.core.designsystem.theme.LocalEinkDimens
import io.github.bookshelf.feature.settings.COVER_CACHE_LIMIT_CHOICES_MB
import io.github.bookshelf.feature.settings.DiskLimitAction
import io.github.bookshelf.feature.settings.DuplicatesUiState
import io.github.bookshelf.feature.settings.DuplicatesViewModel
import io.github.bookshelf.feature.settings.LibraryBehaviourAction
import io.github.bookshelf.feature.settings.PermissionItemUi
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
import io.github.bookshelf.feature.settings.R

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
    onOpenScan: () -> Unit,
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
            EinkErrorBanner(message = duplicatesError.resolve())
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = dimens.gutter),
        ) {
        libraryDefaultsSection(state = state, viewModel = viewModel)

        diskLimitsSection(state = state, onPick = viewModel::onSetDiskLimit)

        fileChangeSection(
            state = state,
            onKeepToggle = { enabled ->
                viewModel.onSetLibraryBehaviour(
                    LibraryBehaviourAction.SetKeepMetadataOnFileChange(enabled),
                )
            },
        )
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
            onOpenScan = onOpenScan,
        )

        permissionsSection(
            permissions = permissions,
            onRequest = permissionRequest::launch,
            onOpenAppInfo = context::openAppInfoSettings,
        )
        }
    }
}

/**
 * Блок «Дубли книг»: проверка содержимого и удаление лишних копий.
 *
 * Отдельной функцией, а не блоком внутри экрана: тело экрана и так длинное (порог detekt
 * `LongMethod`), а блок цельный — он знает только своё состояние и пять команд.
 */
/**
 * Блок «Лимиты диска»: сколько места отдано кэшу обложек и временным копиям.
 *
 * Отдельной функцией по той же причине, что и соседние блоки: тело экрана упирается в порог
 * detekt `LongMethod`.
 */
private fun LazyListScope.diskLimitsSection(
    state: SettingsUiState,
    onPick: (DiskLimitAction) -> Unit,
) {
    item(key = "header-library-limits") {
        EinkSectionHeader(title = stringResource(R.string.settings_disk_limits))
    }

    item(key = "cover-cache-limit") {
        ValueChoiceRow(
            title = stringResource(R.string.settings_cover_cache_limit),
            options = COVER_CACHE_LIMIT_CHOICES_MB,
            selected = state.settings.coverCacheLimitMb,
            labelRes = { R.string.settings_megabytes },
            onPick = { limitMb -> onPick(DiskLimitAction.SetCoverCacheMb(limitMb)) },
            subtitle = stringResource(R.string.settings_cover_cache_limit_hint),
        )
    }

    item(key = "temp-copy-limit") {
        ValueChoiceRow(
            title = stringResource(R.string.settings_temp_copy_limit),
            options = TEMP_COPY_LIMIT_CHOICES_MB,
            selected = state.settings.tempCopyLimitMb,
            labelRes = { R.string.settings_megabytes },
            onPick = { limitMb -> onPick(DiskLimitAction.SetTempCopyMb(limitMb)) },
            subtitle = stringResource(R.string.settings_temp_copy_limit_hint),
        )
    }
}

/**
 * Блок «Замена файлов книг»: что делать с данными книги, когда её файл изменили.
 *
 * Отдельной функцией, как и у дублей: тело экрана уже длинное (порог detekt `LongMethod`), а блок
 * цельный — он знает только своё переключение.
 */
private fun LazyListScope.fileChangeSection(
    state: SettingsUiState,
    onKeepToggle: (Boolean) -> Unit,
) {
    item(key = "file-change-header") {
        EinkSectionHeader(title = stringResource(R.string.settings_file_change_header))
    }
    item(key = "file-change-keep") {
        ToggleRow(
            title = stringResource(R.string.settings_file_change_keep),
            subtitle = stringResource(R.string.settings_file_change_keep_hint),
            enabled = state.settings.isKeepMetadataOnFileChange,
            onToggle = onKeepToggle,
        )
    }
}

private fun LazyListScope.duplicatesSection(
    state: DuplicatesUiState,
    onCheck: () -> Unit,
    onConfirmDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    onConfirmBroken: () -> Unit,
    onCancelBroken: () -> Unit,
) {
    item(key = "duplicates-header") {
        EinkSectionHeader(title = stringResource(R.string.settings_duplicates_header))
    }
    item(key = "duplicates-check") {
        val busy = state.isChecking || state.isDeleting
        EinkListRow(
            title = stringResource(R.string.settings_duplicates_check),
            subtitle = when {
                state.isChecking -> stringResource(R.string.settings_duplicates_comparing)
                state.isDeleting -> stringResource(R.string.settings_duplicates_removing)
                state.message.isNotEmpty() -> state.message.resolveJoined()
                else -> stringResource(R.string.settings_duplicates_hint)
            },
            onClick = { if (!busy) onCheck() },
        )
    }
    if (state.isConfirming) {
        item(key = "duplicates-confirm") {
            EinkConfirmRow(
                question = stringResource(R.string.settings_duplicates_remove_confirm, state.toRemove.size),
                confirmLabel = stringResource(R.string.settings_action_delete),
                onConfirm = onConfirmDelete,
                onCancel = onCancelDelete,
                enabled = !state.isDeleting,
            )
        }
    }
    if (state.isConfirmingBroken) {
        item(key = "broken-confirm") {
            EinkConfirmRow(
                question = stringResource(R.string.settings_broken_remove_confirm, state.broken.size),
                confirmLabel = stringResource(R.string.settings_action_delete),
                onConfirm = onConfirmBroken,
                onCancel = onCancelBroken,
                enabled = !state.isDeleting,
            )
        }
    }
}

/** Мегабайты чипом: «200 МБ». */

/**
 * Подписи сортировки — те же слова, что в строке сортировки на полке.
 *
 * Продублированы намеренно: экран настроек не должен зависеть от `:feature:library` ради восьми
 * строк, а состав вариантов задаёт перечисление настроек — новый вариант обязан появиться здесь
 * сразу, иначе настройку нельзя будет выставить.
 */
private fun sortOrderLabelRes(order: SortOrder): Int = when (order) {
    SortOrder.TITLE -> R.string.settings_sort_title
    SortOrder.AUTHOR -> R.string.settings_sort_author
    SortOrder.SERIES -> R.string.settings_sort_series
    SortOrder.ADDED_AT -> R.string.settings_sort_added
    SortOrder.LAST_OPENED -> R.string.settings_sort_last_opened
    SortOrder.SIZE -> R.string.settings_sort_size
    SortOrder.PROGRESS -> R.string.settings_sort_progress
    SortOrder.YEAR -> R.string.settings_sort_year
}

/** Подписи группировки для чипов: на полке те же варианты, но строчными буквами в подвале. */
private fun groupingLabelRes(grouping: Grouping): Int = when (grouping) {
    Grouping.NONE -> R.string.settings_grouping_none
    Grouping.SERIES -> R.string.settings_grouping_series
    Grouping.AUTHOR -> R.string.settings_grouping_author
    Grouping.FOLDER -> R.string.settings_grouping_folder
    Grouping.FORMAT -> R.string.settings_grouping_format
}


/**
 * Раздел умолчаний полки: вид, показ карты памяти, порядок, направление и группировка.
 *
 * Вынесено из `LibraryScanCategory` отдельной функцией: экран перевалил за предел длины detekt,
 * а это цельный раздел — он зависит только от состояния настроек и ViewModel.
 */
private fun LazyListScope.libraryDefaultsSection(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    item(key = "view-preset") {
        ViewPresetChooser(preset = state.settings.viewPreset, onPick = viewModel::onSetViewPreset)
    }

    item(key = "library-removable-shelf") {
        ToggleRow(
            title = stringResource(R.string.settings_library_removable_shelf),
            subtitle = stringResource(R.string.settings_library_removable_shelf_hint),
            enabled = state.settings.showRemovableShelf,
            onToggle = { enabled ->
                viewModel.onSetLibraryBehaviour(LibraryBehaviourAction.SetShowRemovableShelf(enabled))
            },
        )
    }

    item(key = "default-sort") {
        ValueChoiceRow(
            title = stringResource(R.string.settings_library_sort),
            options = SortOrder.entries,
            selected = state.settings.sortOrder,
            labelRes = ::sortOrderLabelRes,
            onPick = viewModel::onSetDefaultSortOrder,
            subtitle = stringResource(R.string.settings_library_sort_hint),
        )
    }

    item(key = "default-sort-descending") {
        ToggleRow(
            title = stringResource(R.string.settings_library_descending),
            subtitle = stringResource(R.string.settings_library_descending_hint),
            enabled = state.settings.sortDescending,
            onToggle = viewModel::onSetDefaultSortDescending,
        )
    }

    item(key = "default-grouping") {
        ValueChoiceRow(
            title = stringResource(R.string.settings_library_grouping),
            options = Grouping.entries,
            selected = state.settings.grouping,
            labelRes = ::groupingLabelRes,
            onPick = viewModel::onSetDefaultGrouping,
            subtitle = stringResource(R.string.settings_library_grouping_hint),
        )
    }
}


/** Раздел «Разрешения»: выданные права и кнопка открытия настроек приложения. */
private fun LazyListScope.permissionsSection(
    permissions: List<PermissionItemUi>,
    onRequest: (String) -> Unit,
    onOpenAppInfo: () -> Unit,
) {
    item(key = "header-permissions") {
        EinkSectionHeader(title = stringResource(R.string.settings_permissions_header), count = permissions.size)
    }
    items(items = permissions, key = { item -> item.permission }) { item ->
        EinkListRow(
            title = stringResource(item.titleRes),
            subtitle = stringResource(item.subtitleRes),
            trailing = if (item.isGranted) {
                stringResource(R.string.settings_permission_granted)
            } else {
                stringResource(R.string.settings_permission_request)
            },
            onClick = { onRequest(item.permission) },
        )
    }
    item(key = "permissions-appinfo") {
        EinkListRow(
            title = stringResource(R.string.settings_permission_app_info),
            subtitle = stringResource(R.string.settings_permission_app_info_hint),
            onClick = onOpenAppInfo,
        )
    }
}
