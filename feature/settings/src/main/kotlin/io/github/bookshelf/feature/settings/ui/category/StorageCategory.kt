package io.github.bookshelf.feature.settings.ui.category

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bookshelf.core.designsystem.component.EinkConfirmRow
import io.github.bookshelf.core.designsystem.component.EinkListRow
import io.github.bookshelf.core.designsystem.component.EinkProgress
import io.github.bookshelf.core.designsystem.theme.LocalEinkDimens
import io.github.bookshelf.feature.settings.BookOptimizationUiState
import io.github.bookshelf.feature.settings.BookOptimizationViewModel
import io.github.bookshelf.feature.settings.DatabaseBackupViewModel
import io.github.bookshelf.feature.settings.LibraryExportViewModel
import io.github.bookshelf.feature.settings.SettingsUiState
import io.github.bookshelf.feature.settings.SettingsViewModel
import io.github.bookshelf.feature.settings.ui.ToggleRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
@Composable
internal fun StorageCategory(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    backupViewModel: DatabaseBackupViewModel,
    exportViewModel: LibraryExportViewModel,
    optimizationViewModel: BookOptimizationViewModel,
) {
    val backupState by backupViewModel.uiState.collectAsStateWithLifecycle()
    val exportState by exportViewModel.uiState.collectAsStateWithLifecycle()
    val optimizationState by optimizationViewModel.uiState.collectAsStateWithLifecycle()
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
                title = "Очистить кэш обложек",
                subtitle = when {
                    state.isClearingCoverCache -> "Очищаем…"
                    else -> state.coverCacheMessage
                        ?: "Пересобрать кэш заново: обложки скачаются при следующем скане"
                },
                onClick = viewModel::onClearCoverCache,
            )
        }
        item(key = "storage-export") {
            EinkListRow(
                title = "Экспорт библиотеки",
                subtitle = when {
                    exportState.isExporting -> "Выгружаем…"
                    exportState.error != null -> exportState.error.orEmpty()
                    else -> exportState.message
                        ?: "JSON со списком книг, полками и прогрессом чтения"
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
                title = "Ежедневный бэкап базы",
                subtitle = "Копия в папке библиотеки: переживает удаление приложения. " +
                    "Сканер книг копии не видит",
                enabled = backupState.isAutoEnabled,
                onToggle = backupViewModel::onSetAutoEnabled,
            )
        }
        item(key = "storage-backup-now") {
            EinkListRow(
                title = "Сделать бэкап сейчас",
                subtitle = when {
                    backupState.isWorking -> "Работаем…"
                    backupState.selected == null -> backupState.message
                        ?: "Копия базы в папке библиотеки"
                    else -> "Сначала ответьте на вопрос у выбранной копии"
                },
                onClick = backupViewModel::onCreateBackup,
            )
        }
        item(key = "storage-backup-restore") {
            EinkListRow(
                title = "Восстановить из бэкапа",
                subtitle = when {
                    backupState.backups.isEmpty() ->
                        "Копий пока нет — их делает ежедневный бэкап или кнопка выше"
                    else -> "Доступно копий: ${backupState.backups.size}"
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
                subtitle = entry.fileName + (entry.sizeBytes?.let { size -> " · $size байт" } ?: ""),
                hasDivider = !selected,
                onClick = { backupViewModel.onSelect(entry) },
            )
            // Действия раскрываются под самой копией, которую выбрали: у каждой копии одна строка,
            // а не две, и видно, к чему относится каждое нажатие.
            if (selected) {
                EinkConfirmRow(
                    question = "Копия от ${formatBackupMoment(entry.createdAt)}: восстановить " +
                        "библиотеку или удалить копию?",
                    confirmLabel = if (backupState.isWorking) "Работаем…" else "Восстановить",
                    onConfirm = backupViewModel::onConfirmRestore,
                    destructiveLabel = "Удалить",
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
                state.isStopping -> "Останавливаем…"
                state.isRunning -> "Остановить оптимизацию"
                else -> "Оптимизировать все книги FB2"
            },
            subtitle = when {
                state.isRunning -> "Обработано ${state.done} из ${state.total}"
                state.error != null -> state.error.orEmpty()
                else -> state.message ?: OPTIMIZATION_HINT
            },
            trailing = if (state.isRunning) "идёт…" else null,
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
                caption = "Оптимизируем книги без потери данных",
                progress = if (state.total == 0) 0f else state.done.toFloat() / state.total,
                detail = "Обработано ${state.done} из ${state.total}",
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
private const val OPTIMIZATION_HINT =
    "Без потери данных: разметка, картинки и архив. Файл заменяется только если стал меньше"
