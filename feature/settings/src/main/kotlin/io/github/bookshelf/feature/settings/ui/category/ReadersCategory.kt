package io.github.bookshelf.feature.settings.ui.category

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.bookshelf.core.designsystem.component.EinkEmptyState
import io.github.bookshelf.core.designsystem.component.EinkListRow
import io.github.bookshelf.core.designsystem.theme.LocalEinkDimens
import io.github.bookshelf.feature.settings.SettingsUiState
import io.github.bookshelf.feature.settings.SettingsViewModel

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
    }
}

