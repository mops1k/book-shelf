package io.github.bookshelf.feature.settings.ui.category

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.bookshelf.core.designsystem.component.EinkListRow
import io.github.bookshelf.core.designsystem.theme.LocalEinkDimens
import io.github.bookshelf.feature.settings.SettingsUiState
import io.github.bookshelf.feature.settings.SettingsViewModel
import io.github.bookshelf.feature.settings.ui.CategoryPending

/**
 * Категория «Хранилище и кэш».
 *
 * «Очистить кэш обложек» работает: строка показывает освобождённый объём, а не молчит (раньше
 * здесь была заглушка «пока не поддерживается», хотя `CoverCache.clear()` уже был реализован).
 * Экспорт библиотеки по-прежнему заглушка — о нём строка сообщает честно, чтобы кнопка не
 * выглядела рабочей. Лимит кэша обложек здесь не показан: его место — в разделе «Библиотека
 * и скан», рядом с лимитом временной копии.
 */
@Composable
internal fun StorageCategory(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
) {
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
        item(key = "storage-pending") {
            CategoryPending(
                "Бэкап базы пока не подключён. Лимит кэша обложек настраивается в разделе " +
                    "«Библиотека и скан».",
            )
        }
    }
}
