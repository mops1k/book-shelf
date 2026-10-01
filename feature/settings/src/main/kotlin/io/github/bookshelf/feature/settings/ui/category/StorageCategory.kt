package io.github.bookshelf.feature.settings.ui.category

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bookshelf.core.designsystem.component.EinkConfirmRow
import io.github.bookshelf.core.designsystem.component.EinkSectionHeader
import io.github.bookshelf.core.designsystem.component.EinkListRow
import io.github.bookshelf.core.designsystem.component.EinkProgress
import io.github.bookshelf.core.designsystem.theme.LocalEinkDimens
import io.github.bookshelf.feature.settings.BookOptimizationUiState
import io.github.bookshelf.feature.settings.BookCopiesUiState
import io.github.bookshelf.feature.settings.BookCopiesViewModel
import io.github.bookshelf.feature.settings.BookOptimizationViewModel
import io.github.bookshelf.feature.settings.DatabaseBackupViewModel
import io.github.bookshelf.feature.settings.LibraryExportViewModel
import io.github.bookshelf.feature.settings.SettingsUiState
import io.github.bookshelf.feature.settings.SettingsViewModel
import io.github.bookshelf.feature.settings.ui.ToggleRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import io.github.bookshelf.feature.settings.R
import io.github.bookshelf.core.designsystem.text.resolve

/**
 * Категория «Хранилище и кэш».
 *
 * «Очистить кэш обложек» работает: строка показывает освобождённый объём, а не молчит.
 * «Экспорт библиотеки» — тоже: диалог SAF спрашивает, куда положить файл, а строка после выгрузки
 * называет число книг и полок (до этого здесь стояла строка-обещание «пока не поддерживается»).
 * Лимит кэша обложек здесь не показан: его место — в разделе «Библиотека и скан», рядом с лимитом
 * временной копии.
 *
 * Бэкап базы и выгрузка библиотеки — отдельные ViewModel: у общей уже девять зависимостей при
 * пороге detekt десять, а восстановление с подтверждением, списком копий и диалогом сохранения
 * файла в неё бы не поместилось.
 *
 * У каждой копии одна строка: касание раскрывает под ней три действия — «Восстановить», «Удалить»
 * и «Отмена» (требование Александра, 29.09.2026: удаление не должно занимать отдельную строку).
 */
/**
 * Блок «Копии книг на устройстве».
 *
 * Отдельной функцией, потому что `StorageCategory` уже длинная, а блок самостоятельный: он либо
 * пуст (копий нет — и места на экране не занимает), либо показывает список, сумму и удаление с
 * подтверждением.
 */
private fun LazyListScope.storageCopiesSection(
    state: BookCopiesUiState,
    viewModel: BookCopiesViewModel,
) {
    item(key = "storage-copies-header") {
            EinkSectionHeader(
                title = stringResource(R.string.settings_copies_header),
                count = state.copies.size,
            )
        }
        if (!state.isEmpty) {
            item(key = "storage-copies-hint") {
                EinkListRow(
                    title = stringResource(
                        R.string.settings_copies_total,
                        state.totalLabel?.resolve().orEmpty(),
                    ),
                    subtitle = stringResource(R.string.settings_copies_hint),
                )
            }
            items(items = state.copies, key = { copy -> copy.copyDocumentUri }) { copy ->
                EinkListRow(
                    title = copy.title,
                    subtitle = copy.sizeLabel.resolve(),
                )
            }
            if (state.isConfirming) {
                item(key = "storage-copies-confirm") {
                    EinkConfirmRow(
                        question = stringResource(R.string.settings_copies_confirm),
                        confirmLabel = stringResource(R.string.settings_action_delete),
                        onConfirm = viewModel::onConfirmRemove,
                        onCancel = viewModel::onCancelRemove,
                        enabled = !state.isWorking,
                    )
                }
            } else {
                item(key = "storage-copies-remove") {
                    EinkListRow(
                        title = stringResource(
                            if (state.isWorking) {
                                R.string.settings_working
                            } else {
                                R.string.settings_copies_action
                            },
                        ),
                        subtitle = state.message?.resolve() ?: state.error?.resolve(),
                        onClick = viewModel::onAskRemove,
                    )
                }
            }
        }
}

@Composable
internal fun StorageCategory(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    backupViewModel: DatabaseBackupViewModel,
    exportViewModel: LibraryExportViewModel,
    optimizationViewModel: BookOptimizationViewModel,
    copiesViewModel: BookCopiesViewModel,
) {
    val backupState by backupViewModel.uiState.collectAsStateWithLifecycle()
    val exportState by exportViewModel.uiState.collectAsStateWithLifecycle()
    val optimizationState by optimizationViewModel.uiState.collectAsStateWithLifecycle()
    val copiesState by copiesViewModel.uiState.collectAsStateWithLifecycle()
    val dimens = LocalEinkDimens.current
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(EXPORT_MIME_TYPE),
    ) { destination -> destination?.let(exportViewModel::onExport) }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = dimens.gutter),
    ) {
        item(key = "storage-cover-cache") {
            EinkListRow(
                title = stringResource(R.string.settings_storage_clear_covers),
                subtitle = when {
                    state.isClearingCoverCache -> stringResource(R.string.settings_storage_clearing)
                    else -> state.coverCacheMessage
                        ?: stringResource(R.string.settings_storage_clear_covers_hint)
                },
                onClick = viewModel::onClearCoverCache,
            )
        }
        storageCopiesSection(state = copiesState, viewModel = copiesViewModel)
        item(key = "storage-export") {
            EinkListRow(
                title = stringResource(R.string.settings_storage_export),
                subtitle = when {
                    exportState.isExporting -> stringResource(R.string.settings_storage_exporting)
                    exportState.error != null -> exportState.error.orEmpty()
                    else -> exportState.message
                        ?: stringResource(R.string.settings_storage_export_hint)
                },
                onClick = {
                    // Имя файла предлагает приложение, а место выбирает человек: диалог SAF отдаёт
                    // готовый документ, и записывать в него можно без дополнительных разрешений.
                    exportLauncher.launch(exportViewModel.suggestedFileName())
                },
            )
        }
        fb2OptimizationRows(state = optimizationState, viewModel = optimizationViewModel)
        item(key = "storage-backup-auto") {
            ToggleRow(
                title = stringResource(R.string.settings_storage_backup),
                subtitle = stringResource(R.string.settings_storage_backup_hint),
                enabled = backupState.isAutoEnabled,
                onToggle = backupViewModel::onSetAutoEnabled,
            )
        }
        item(key = "storage-backup-now") {
            EinkListRow(
                title = stringResource(R.string.settings_storage_backup_now),
                subtitle = when {
                    backupState.isWorking -> stringResource(R.string.settings_working)
                    backupState.selected == null -> backupState.message
                        ?: stringResource(R.string.settings_storage_backup_now_hint)
                    else -> stringResource(R.string.settings_storage_answer_first)
                },
                onClick = backupViewModel::onCreateBackup,
            )
        }
        item(key = "storage-backup-restore") {
            EinkListRow(
                title = stringResource(R.string.settings_storage_restore),
                subtitle = when {
                    backupState.backups.isEmpty() ->
                        stringResource(R.string.settings_storage_no_backups)
                    else -> stringResource(
                        R.string.settings_backups_available,
                        backupState.backups.size,
                    )
                },
                onClick = backupViewModel::onLoadBackups,
            )
        }
        items(
            items = backupState.backups,
            key = { entry -> entry.documentUri },
        ) { entry ->
            val selected = backupState.selected?.documentUri == entry.documentUri
            EinkListRow(
                title = formatBackupMoment(entry.createdAt),
                subtitle = entry.fileName + (
                    entry.sizeBytes
                        ?.let { size -> stringResource(R.string.settings_backup_size, size) }
                        ?: ""
                    ),
                hasDivider = !selected,
                onClick = { backupViewModel.onSelect(entry) },
            )
            // Действия раскрываются под самой копией, которую выбрали: у каждой копии одна строка,
            // а не две, и видно, к чему относится каждое нажатие.
            if (selected) {
                EinkConfirmRow(
                    question = stringResource(
                        R.string.settings_backup_restore_confirm,
                        formatBackupMoment(entry.createdAt),
                    ),
                    confirmLabel = if (backupState.isWorking) {
                        stringResource(R.string.settings_working)
                    } else {
                        stringResource(R.string.settings_storage_restore_action)
                    },
                    onConfirm = backupViewModel::onConfirmRestore,
                    destructiveLabel = stringResource(R.string.settings_action_delete),
                    onDestructive = backupViewModel::onDeleteSelected,
                    onCancel = backupViewModel::onCancelSelection,
                    enabled = !backupState.isWorking,
                )
            }
        }
    }
}

/**
 * Строки оптимизации FB2: запуск, остановка и полоса прогресса.
 *
 * Вынесены из [StorageCategory] отдельной функцией: у той уже девять зависимостей и длинное тело,
 * а здесь своё маленькое состояние — прогресс, отмена и итог.
 */
private fun LazyListScope.fb2OptimizationRows(
    state: BookOptimizationUiState,
    viewModel: BookOptimizationViewModel,
) {
    item(key = "storage-optimize-fb2") {
        EinkListRow(
            title = when {
                state.isStopping -> stringResource(R.string.settings_storage_optimize_stopping)
                state.isRunning -> stringResource(R.string.settings_storage_optimize_stop)
                else -> stringResource(R.string.settings_storage_optimize_all)
            },
            subtitle = when {
                state.isRunning -> stringResource(
                    R.string.settings_optimize_progress,
                    state.done,
                    state.total,
                )
                state.error != null -> state.error.orEmpty()
                else -> state.message ?: stringResource(OPTIMIZATION_HINT_RES)
            },
            trailing = if (state.isRunning) stringResource(R.string.settings_in_progress) else null,
            onClick = {
                // Одна строка на запуск и остановку: на e-ink лишняя кнопка дороже слова,
                // которое прямо говорит, что произойдёт по касанию.
                if (state.isRunning) viewModel.onStop() else viewModel.onStart()
            },
        )
    }
    if (state.isRunning) {
        item(key = "storage-optimize-progress") {
            EinkProgress(
                caption = stringResource(R.string.settings_storage_optimize_hint),
                progress = if (state.total == 0) 0f else state.done.toFloat() / state.total,
                detail = stringResource(R.string.settings_optimize_progress, state.done, state.total),
            )
        }
    }
}

/** Дата копии для человека: «29 сентября 2026, 16:54». */
private fun formatBackupMoment(createdAt: Long): String =
    SimpleDateFormat("d MMMM yyyy, HH:mm", Locale("ru")).format(Date(createdAt))

/**
 * Тип документа для диалога «создать документ».
 *
 * Именно `application/json`, а не `text/plain`: по типу провайдер подбирает расширение и владельца
 * файла, и выгрузку можно открыть как JSON, а не как текст без разметки.
 */
private const val EXPORT_MIME_TYPE = "application/json"

/**
 * Что произойдёт по касанию строки оптимизации.
 *
 * Словами названы все три обещания механизма: без потери данных, замена только при выигрыше и то,
 * что файл книги при этом меняется на устройстве. Молчаливая оптимизация, которая переписывает
 * файлы, была бы неприятным сюрпризом.
 */
private val OPTIMIZATION_HINT_RES = R.string.settings_storage_optimize_note
