package io.github.bookshelf.feature.settings.ui.category

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.bookshelf.core.designsystem.component.EinkListRow
import io.github.bookshelf.core.designsystem.theme.LocalEinkDimens
import io.github.bookshelf.core.opds.download.DOWNLOAD_FILE_NAME_TEMPLATES
import io.github.bookshelf.feature.settings.DOWNLOAD_FILE_NAME_TEMPLATE_LABELS
import io.github.bookshelf.feature.settings.FEED_CACHE_CHOICES_MINUTES
import io.github.bookshelf.feature.settings.SettingsUiState
import io.github.bookshelf.feature.settings.SettingsViewModel
import io.github.bookshelf.feature.settings.ui.CategoryPending
import io.github.bookshelf.feature.settings.ui.ValueChoiceRow

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
 * папку, где лежит библиотека, а раскладку по подпапкам задаёт шаблон пути.
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
                title = "Открыть каталоги OPDS",
                subtitle = "Список каталогов, добавление по адресу и загрузки",
                onClick = onOpenCatalogs,
            )
        }

        item(key = "opds-file-name") {
            ValueChoiceRow(
                title = "Куда и под каким именем сохранять",
                options = DOWNLOAD_FILE_NAME_TEMPLATES,
                selected = settings.downloadFileNameTemplate,
                label = { template -> DOWNLOAD_FILE_NAME_TEMPLATE_LABELS[template] ?: template },
                onPick = viewModel::onSetDownloadFileNameTemplate,
                subtitle = "Папки задаются через «/»: книги раскладываются по авторам и сериям " +
                    "внутри папки библиотеки",
            )
        }

        item(key = "opds-feed-cache") {
            ValueChoiceRow(
                title = "Кэш каталогов",
                options = FEED_CACHE_CHOICES_MINUTES,
                selected = settings.feedCacheMaxAgeMinutes,
                label = ::feedCacheLabel,
                onPick = viewModel::onSetFeedCacheMaxAgeMinutes,
                subtitle = "Как долго открытый каталог берётся из памяти, не обращаясь к сети",
            )
        }

        item(key = "opds-note") {
            CategoryPending(
                "Папка загрузок отдельно не настраивается: книги скачиваются в папку библиотеки, " +
                    "а подпапки задаёт шаблон пути выше.",
            )
        }
    }
}

/** Возраст кэша по-человечески: «5 мин», но «1 ч» вместо «60 мин». */
private fun feedCacheLabel(minutes: Int): String =
    if (minutes >= MINUTES_PER_HOUR) "${minutes / MINUTES_PER_HOUR} ч" else "$minutes мин"

private const val MINUTES_PER_HOUR = 60
