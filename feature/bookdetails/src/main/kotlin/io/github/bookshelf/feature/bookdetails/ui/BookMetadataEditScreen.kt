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
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bookshelf.core.designsystem.component.EinkButton
import io.github.bookshelf.core.designsystem.component.EinkCheckbox
import io.github.bookshelf.core.designsystem.component.EinkErrorBanner
import io.github.bookshelf.core.designsystem.text.resolve
import io.github.bookshelf.core.designsystem.component.EinkScaffold
import io.github.bookshelf.core.designsystem.component.EinkTextField
import io.github.bookshelf.core.designsystem.input.EinkAction
import io.github.bookshelf.core.designsystem.text.AppText
import io.github.bookshelf.core.designsystem.theme.EinkColors
import io.github.bookshelf.core.designsystem.theme.LocalEinkColors
import io.github.bookshelf.core.designsystem.theme.LocalEinkDimens
import io.github.bookshelf.feature.bookdetails.BookMetadataEditUiState
import io.github.bookshelf.feature.bookdetails.BookMetadataEditViewModel
import io.github.bookshelf.feature.bookdetails.R

/**
 * Правка метаданных книги (docs/eink-ux.md §6.4).
 *
 * Пять полей, которые человек правит чаще всего: название, авторы, серия, номер в серии, описание.
 * Серия и номер — то, ради чего экран и появился: в EPUB серии часто нет, а на полке по сериям
 * книга без неё висит отдельно.
 *
 * Пустое поле убирает значение: поля заполнены текущими данными книги, поэтому стереть серию
 * или описание можно, просто очистив поле.
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
        title = stringResource(R.string.edit_title),
        subtitle = stringResource(R.string.edit_subtitle),
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
                    text = if (state.isSaving) {
                        stringResource(R.string.edit_action_saving)
                    } else {
                        stringResource(R.string.edit_action_save)
                    },
                    onClick = viewModel::onSave,
                    enabled = state.canSave,
                    primary = true,
                    modifier = Modifier.weight(1f),
                )
                EinkButton(
                    text = stringResource(R.string.book_action_cancel),
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                )
            }
        },
    ) {
        state.error?.let { error ->
            EinkErrorBanner(message = error.resolve())
        }
        MetadataFields(state = state, viewModel = viewModel)
    }
}


/**
 * Поля правки метаданных.
 *
 * Вынесены из `BookMetadataEditScreen` отдельной функцией: экран перевалил за предел длины detekt,
 * а поля — цельный кусок, который зависит только от состояния и ViewModel.
 */
@Composable
private fun MetadataFields(state: BookMetadataEditUiState, viewModel: BookMetadataEditViewModel) {
    val colors = LocalEinkColors.current
    val dimens = LocalEinkDimens.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = dimens.gutter, vertical = dimens.gutterSmall),
        verticalArrangement = Arrangement.spacedBy(dimens.gutter),
    ) {
        state.message?.let { message -> MessageLine(message = message, colors = colors) }
        EinkTextField(
            value = state.title,
            onValueChange = viewModel::onTitleChange,
            label = stringResource(R.string.edit_field_title),
            placeholder = stringResource(R.string.edit_field_title_hint),
            enabled = !state.isSaving,
        )
        EinkTextField(
            value = state.authors,
            onValueChange = viewModel::onAuthorsChange,
            label = stringResource(R.string.edit_field_authors),
            placeholder = stringResource(R.string.edit_field_authors_hint),
            enabled = !state.isSaving,
        )
        EinkTextField(
            value = state.series,
            onValueChange = viewModel::onSeriesChange,
            label = stringResource(R.string.edit_field_series),
            placeholder = stringResource(R.string.edit_field_series_hint),
            enabled = !state.isSaving,
        )
        EinkTextField(
            value = state.seriesIndex,
            onValueChange = viewModel::onSeriesIndexChange,
            label = stringResource(R.string.edit_field_series_index),
            placeholder = stringResource(R.string.edit_field_series_index_hint),
            enabled = !state.isSaving,
        )
        EinkTextField(
            value = state.description,
            onValueChange = viewModel::onDescriptionChange,
            label = stringResource(R.string.edit_field_description),
            placeholder = stringResource(R.string.edit_field_description_hint),
            singleLine = false,
            enabled = !state.isSaving,
        )
        EinkCheckbox(
            checked = state.writeToFile,
            onCheckedChange = viewModel::onWriteToFileChange,
            label = stringResource(R.string.edit_write_to_file),
            enabled = !state.isSaving,
        )
    }
}


/** Строка сообщения формы правки: перевод текста и цвет берутся здесь. */
@Composable
private fun MessageLine(message: AppText, colors: EinkColors) {
    Text(
        text = message.resolve(),
        style = MaterialTheme.typography.bodyMedium,
        color = colors.foreground,
    )
}
