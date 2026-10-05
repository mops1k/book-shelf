package io.github.bookshelf.feature.settings.ui.category

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import io.github.bookshelf.core.designsystem.component.EinkButton
import io.github.bookshelf.core.designsystem.component.EinkListRow
import io.github.bookshelf.core.designsystem.text.resolve
import io.github.bookshelf.core.designsystem.component.EinkSectionHeader
import io.github.bookshelf.core.designsystem.theme.LocalEinkDimens
import io.github.bookshelf.feature.settings.EinkDiagnosticsUi
import io.github.bookshelf.core.data.sync.ProgressSourcesReport
import io.github.bookshelf.feature.settings.SettingsUiState
import io.github.bookshelf.feature.settings.progressSourceNameRes
import io.github.bookshelf.feature.settings.progressSourcesLines
import io.github.bookshelf.feature.settings.SettingsViewModel
import io.github.bookshelf.feature.settings.ui.openAppInfoSettings
import io.github.bookshelf.feature.settings.R

/**
 * Категория «О приложении и диагностика».
 *
 * Здесь показывается только то, что действительно известно системе: версия из пакетного менеджера
 * и данные вендора из `SettingsUiState.diagnostics`. Диагностику можно перечитать — вендорские
 * контроллеры читают состояние при старте, а после перезагрузки устройства оно может измениться.
 * Пустое место вместо лицензий и вендорских настроек остаётся заглушкой: их нечем наполнить.
 */
@Composable
internal fun AboutCategory(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    val context = LocalContext.current
    val dimens = LocalEinkDimens.current
    val diagnostics = state.diagnostics

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = dimens.gutter),
    ) {
        item(key = "about-version") {
            EinkListRow(
                title = stringResource(R.string.settings_about_version),
                subtitle = stringResource(R.string.settings_about_version_hint),
                trailing = appVersion(context),
            )
        }
        item(key = "about-eink-header") {
            EinkSectionHeader(title = stringResource(R.string.settings_about_diagnostics))
        }
        einkDiagnosticsRows(diagnostics = diagnostics)

        einkCapabilitiesRows(diagnostics = diagnostics)

        progressSourceRows(state = state)

        item(key = "about-refresh") {
            EinkListRow(
                title = stringResource(R.string.settings_about_refresh_diagnostics),
                subtitle = stringResource(R.string.settings_about_refresh_diagnostics_hint),
                onClick = viewModel::onRefreshDiagnostics,
            )
        }
        item(key = "about-permissions") {
            EinkButton(
                text = stringResource(R.string.settings_permission_app_info),
                onClick = { context.openAppInfoSettings() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = dimens.gutterSmall),
            )
        }
    }
}

/**
 * Что показала диагностика источников прогресса.
 *
 * Отдельным блоком, а не строкой «всё хорошо»: вопрос «почему у книги нет процента» решается только
 * тем, что видно, какие читалки приложение реально может прочитать (доступ ко всей памяти) и чей
 * прогресс записан последним. До первой проверки блока нет — пустой список выглядел бы как поломка.
 */
private fun LazyListScope.progressSourceRows(state: SettingsUiState) {
    val report = state.progressSources ?: return
    // Заголовок и две строки списка вместо строки на источник: источников шесть, и однотипный список
    // растягивал экран (замечание Александра 05.10.2026). Читаемые и нечитаемые — разными строками,
    // и подпись переносится по ширине экрана, иначе список не увидеть целиком.
    item(key = "about-progress-sources-header") {
        EinkSectionHeader(title = stringResource(R.string.settings_about_progress_sources))
    }
    items(
        items = progressSourcesLines(report),
        key = { line -> "about-progress-line-" + line.titleRes },
    ) { line ->
        EinkListRow(
            title = stringResource(line.titleRes),
            subtitle = progressSourceNamesLine(line.names),
            subtitleMaxLines = SUBTITLE_UNLIMITED_LINES,
        )
    }
    item(key = "about-progress-last") {
        EinkListRow(
            title = stringResource(R.string.settings_about_progress_last),
            subtitle = lastProgressText(report),
        )
    }
}

/**
 * Имена источников одной строкой, через запятую.
 *
 * Отдельной функцией, а не `joinToString { stringResource(it) }`: ресурс переводится только в
 * Composable, и в лямбде `joinToString` этого сделать нельзя — компилятор не считает её местом
 * вызова. `forEach` — inline без nullable-лямбды, поэтому внутри него перевод допустим.
 */
@Composable
private fun progressSourceNamesLine(names: List<Int>): String {
    val resolved = mutableListOf<String>()
    names.forEach { nameRes -> resolved += stringResource(nameRes) }
    return resolved.joinToString(", ")
}

/** Предел строк подписи: список источников должен быть виден целиком, а не обрезан. */
private const val SUBTITLE_UNLIMITED_LINES = 10

/** Подпись последней записи прогресса: источник, время и книга. */
@Composable
private fun lastProgressText(report: ProgressSourcesReport): String {
    val sourceKey = report.lastSourceKey
    val updatedAt = report.lastUpdatedAt
    if (sourceKey == null || updatedAt == null) {
        return stringResource(R.string.settings_about_progress_last_none)
    }
    val name = progressSourceNameRes(sourceKey)?.let { stringResource(it) }
        ?: stringResource(R.string.settings_about_progress_source_unknown)
    val value = stringResource(
        R.string.settings_about_progress_last_value,
        name,
        remember(updatedAt) { relativeOrAbsoluteTime(updatedAt) },
    )
    val title = report.lastBookTitle ?: return value
    return value + ", " + stringResource(R.string.settings_about_progress_last_book, title)
}

/**
 * Время записи словами: свежее — «минуту назад», старое — датой.
 *
 * Абсолютная дата для вчерашней записи нужна, а для только что сделанной она бесполезна: человек
 * смотрит диагностику сразу после чтения и хочет увидеть, что запись появилась.
 */
private fun relativeOrAbsoluteTime(updatedAt: Long): String {
    val minutes = (System.currentTimeMillis() - updatedAt) / MILLIS_PER_MINUTE
    val format = java.text.SimpleDateFormat("d MMMM, HH:mm", java.util.Locale.getDefault())
    return when {
        minutes < MINUTES_JUST_NOW -> "только что"
        minutes < MINUTES_PER_HOUR -> "$minutes мин назад"
        else -> format.format(java.util.Date(updatedAt))
    }
}

private const val MILLIS_PER_MINUTE = 60_000L
private const val MINUTES_JUST_NOW = 1L
private const val MINUTES_PER_HOUR = 60L

/**
 * Версия приложения для строки «Версия».
 *
 * Читаем через `runCatching`: версия — приятная мелочь, из-за которой экран «О приложении»
 * развалиться не должен. Если система почему-то не отдала пакет, честнее показать «неизвестно»,
 * чем пустое место, которое выглядит как незаполненные данные.
 */
private fun appVersion(context: Context): String {
    val version = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull()
    return version?.takeIf { it.isNotBlank() } ?: context.getString(R.string.settings_about_unknown)
}


/** Строки диагностики e-ink: устройство, экран и доступность вендорских команд. */
private fun LazyListScope.einkDiagnosticsRows(diagnostics: EinkDiagnosticsUi) {
    item(key = "about-eink-vendor") {
        EinkListRow(
            title = stringResource(R.string.settings_about_vendor),
            subtitle = stringResource(R.string.settings_about_vendor_hint),
            trailing = diagnostics.vendorLabel
                ?: stringResource(R.string.settings_diagnostics_unknown_device),
        )
    }
    item(key = "about-eink-screen") {
        EinkListRow(
            title = stringResource(R.string.settings_about_screen),
            subtitle = stringResource(R.string.settings_about_screen_hint),
            trailing = diagnostics.screenLabel.resolve(),
        )
    }
}


/** Возможности вендорского контроллера: команды обновления и быстрый режим. */
private fun LazyListScope.einkCapabilitiesRows(diagnostics: EinkDiagnosticsUi) {
    item(key = "about-eink-commands") {
        EinkListRow(
            title = stringResource(R.string.settings_about_vendor_commands),
            subtitle = stringResource(R.string.settings_about_vendor_commands_hint),
            trailing = if (diagnostics.hasVendorCommands) {
                stringResource(R.string.settings_about_available)
            } else {
                stringResource(R.string.settings_about_unavailable)
            },
        )
    }
    item(key = "about-eink-fast") {
        EinkListRow(
            title = stringResource(R.string.settings_about_fast_mode),
            subtitle = stringResource(R.string.settings_about_fast_mode_hint),
            trailing = if (diagnostics.supportsFastMode) {
                stringResource(R.string.settings_about_available_short)
            } else {
                stringResource(R.string.settings_about_no)
            },
        )
    }
}
