package io.github.bookshelf.feature.bookdetails.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bookshelf.core.designsystem.component.EinkButton
import io.github.bookshelf.core.designsystem.component.EinkCheckbox
import io.github.bookshelf.core.designsystem.component.EinkErrorBanner
import io.github.bookshelf.core.designsystem.component.EinkScaffold
import io.github.bookshelf.core.designsystem.component.EinkTextField
import io.github.bookshelf.core.designsystem.input.EinkAction
import io.github.bookshelf.core.designsystem.theme.LocalEinkColors
import io.github.bookshelf.core.designsystem.theme.LocalEinkDimens
import io.github.bookshelf.feature.bookdetails.BookMetadataEditViewModel

/**
 * Правка метаданных книги (docs/eink-ux.md §6.4).
 *
 * Пять полей, которые человек правит чаще всего: название, авторы, серия, номер в серии, описание.
 * Серия и номер — то, ради чего экран и появился: в EPUB серии часто нет, а на полке по сериям
 * книга без неё висит отдельно.
 *
 * Файл книги правится по флажку «Записать правку в файл книги» (по умолчанию включён): тогда серия
 * остаётся в самой книге и переживает перенос на другое устройство. Итог показывается на экране —
 * «сохранено» без оговорки было бы неправдой, если файл записать не удалось.
 */
@Composable
fun BookMetadataEditScreen(
    viewModel: BookMetadataEditViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = LocalEinkColors.current
    val dimens = LocalEinkDimens.current

    EinkScaffold(
        title = "Правка метаданных",
        subtitle = "Пустое поле оставляет значение из файла",
        modifier = modifier,
        onBack = onBack,
        onKeyAction = { action -> if (action == EinkAction.OPEN) viewModel.onSave() },
        footer = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.gutter, vertical = dimens.gutterSmall),
                horizontalArrangement = Arrangement.spacedBy(dimens.gutterSmall),
            ) {
                EinkButton(
                    text = if (state.isSaving) "Сохраняем…" else "Сохранить",
                    onClick = viewModel::onSave,
                    enabled = state.canSave,
                    primary = true,
                    modifier = Modifier.weight(1f),
                )
                EinkButton(text = "Отмена", onClick = onBack, modifier = Modifier.weight(1f))
            }
        },
    ) {
        state.error?.let { error ->
            EinkErrorBanner(message = error)
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimens.gutter, vertical = dimens.gutterSmall),
            verticalArrangement = Arrangement.spacedBy(dimens.gutter),
        ) {
            state.message?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.foreground,
                )
            }
            EinkTextField(
                value = state.title,
                onValueChange = viewModel::onTitleChange,
                label = "Название",
                placeholder = "Название книги",
                enabled = !state.isSaving,
            )
            EinkTextField(
                value = state.authors,
                onValueChange = viewModel::onAuthorsChange,
                label = "Авторы",
                placeholder = "Иванов, Петров",
                enabled = !state.isSaving,
            )
            EinkTextField(
                value = state.series,
                onValueChange = viewModel::onSeriesChange,
                label = "Серия",
                placeholder = "Серия книги",
                enabled = !state.isSaving,
            )
            EinkTextField(
                value = state.seriesIndex,
                onValueChange = viewModel::onSeriesIndexChange,
                label = "Номер в серии",
                placeholder = "3 или 3.5",
                enabled = !state.isSaving,
            )
            EinkTextField(
                value = state.description,
                onValueChange = viewModel::onDescriptionChange,
                label = "Описание",
                placeholder = "Аннотация",
                singleLine = false,
                enabled = !state.isSaving,
            )
            EinkCheckbox(
                checked = state.writeToFile,
                onCheckedChange = viewModel::onWriteToFileChange,
                label = "Записать правку в файл книги (FB2 и EPUB) — тогда она переживёт перенос",
                enabled = !state.isSaving,
            )
        }
    }
}
