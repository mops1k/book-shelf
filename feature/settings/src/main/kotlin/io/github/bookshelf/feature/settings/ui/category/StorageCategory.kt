package io.github.bookshelf.feature.settings.ui.category

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bookshelf.core.designsystem.component.EinkConfirmRow
import io.github.bookshelf.core.designsystem.component.EinkListRow
import io.github.bookshelf.core.designsystem.theme.LocalEinkDimens
import io.github.bookshelf.feature.settings.DatabaseBackupViewModel
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
 * Экспорт библиотеки по-прежнему заглушка — о нём строка сообщает честно, чтобы кнопка не
 * выглядела рабочей. Лимит кэша обложек здесь не показан: его место — в разделе «Библиотека
 * и скан», рядом с лимитом временной копии.
 *
 * Бэкап базы — отдельная ViewModel: у общей уже 29 методов при пороге detekt 30, а восстановление
 * с подтверждением и списком копий в неё бы не поместилось.
 */
@Composable
internal fun StorageCategory(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    backupViewModel: DatabaseBackupViewModel,
) {
    val backupState by backupViewModel.uiState.collectAsStateWithLifecycle()
    val dimens = LocalEinkDimens.current
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
                subtitle = "JSON со списком книг и метаданными",
                onClick = viewModel::onExportLibrary,
            )
        }
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
                    backupState.pendingRestore == null -> backupState.message
                        ?: "Копия базы в папке библиотеки"
                    else -> "Сначала ответьте на вопрос ниже"
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
        val pending = backupState.pendingRestore
        if (pending != null) {
            item(key = "storage-backup-confirm") {
                EinkConfirmRow(
                    question = "Заменить текущую библиотеку копией от " +
                        "${formatBackupMoment(pending.createdAt)}? Текущая база будет сохранена " +
                        "отдельной копией",
                    confirmLabel = "Восстановить",
                    onConfirm = backupViewModel::onConfirmRestore,
                    onCancel = backupViewModel::onCancelRestore,
                    enabled = !backupState.isWorking,
                )
            }
        }
        items(
            items = backupState.backups,
            key = { entry -> entry.documentUri },
        ) { entry ->
            EinkListRow(
                title = formatBackupMoment(entry.createdAt),
                subtitle = entry.fileName + (entry.sizeBytes?.let { size -> " · $size байт" } ?: ""),
                onClick = { backupViewModel.onAskRestore(entry) },
            )
        }
    }
}

/** Дата копии для человека: «29 сентября 2026, 16:54». */
private fun formatBackupMoment(createdAt: Long): String =
    SimpleDateFormat("d MMMM yyyy, HH:mm", Locale("ru")).format(Date(createdAt))
