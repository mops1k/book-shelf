package io.github.bookshelf.feature.settings.ui.category

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.bookshelf.core.designsystem.component.EinkListRow
import io.github.bookshelf.core.designsystem.theme.LocalEinkDimens
import io.github.bookshelf.core.opds.download.DOWNLOAD_FILE_NAME_TEMPLATES
import io.github.bookshelf.feature.settings.DOWNLOAD_FILE_NAME_TEMPLATE_LABELS
import io.github.bookshelf.feature.settings.FEED_CACHE_CHOICES_MINUTES
import io.github.bookshelf.feature.settings.SettingsUiState
import io.github.bookshelf.feature.settings.SettingsViewModel
import io.github.bookshelf.feature.settings.ui.ValueChoiceRow
import io.github.bookshelf.feature.settings.R

/**
 * Категория «Каталоги OPDS».
 *
 * Единственное рабочее действие помимо настроек — переход в браузер каталогов: он живёт в
 * `feature:catalogs` и уже умеет список, добавление по адресу и загрузки. Возвращать его копию
 * внутрь настроек значило бы завести два места, которые расходятся.
 *
 * Показаны только настройки с потребителем: шаблон пути читает `OpdsDownloadWorker`, возраст
 * кэша — `OpdsRepositoryImpl`. «Разрешить HTTP» и «Включить OPDS» на экран не вынесены по решению
 * Александра (29.09.2026): каталог Флибусты работает по `http://`, и запрет сломал бы его, а OPDS
 * всегда должен быть доступен. Отдельной папки загрузок тоже нет: книги складываются в ту же
 * папку, где лежит библиотека, а раскладку по подпапкам задаёт шаблон пути — папка выбирается на
 * карточке книги, когда источников несколько, поэтому пояснять здесь нечего.
 */
@Composable
internal fun OpdsCategory(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    onOpenCatalogs: () -> Unit,
) {
    val dimens = LocalEinkDimens.current
    val settings = state.settings

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = dimens.gutter),
    ) {
        item(key = "opds-catalogs") {
            EinkListRow(
                title = stringResource(R.string.settings_opds_open),
                subtitle = stringResource(R.string.settings_opds_open_hint),
                onClick = onOpenCatalogs,
            )
        }

        item(key = "opds-file-name") {
            ValueChoiceRow(
                title = stringResource(R.string.settings_opds_template),
                options = DOWNLOAD_FILE_NAME_TEMPLATES,
                selected = settings.downloadFileNameTemplate,
                labelRes = { template ->
                    DOWNLOAD_FILE_NAME_TEMPLATE_LABELS[template] ?: R.string.settings_opds_template_raw
                },
                onPick = viewModel::onSetDownloadFileNameTemplate,
                subtitle = stringResource(R.string.settings_opds_template_hint),
            )
        }

        item(key = "opds-feed-cache") {
            ValueChoiceRow(
                title = stringResource(R.string.settings_opds_cache),
                options = FEED_CACHE_CHOICES_MINUTES,
                selected = settings.feedCacheMaxAgeMinutes,
                labelRes = { R.string.settings_opds_cache_minutes },
                onPick = viewModel::onSetFeedCacheMaxAgeMinutes,
                subtitle = stringResource(R.string.settings_opds_cache_hint),
            )
        }
    }
}

/** Возраст кэша по-человечески: «5 мин», но «1 ч» вместо «60 мин». */
