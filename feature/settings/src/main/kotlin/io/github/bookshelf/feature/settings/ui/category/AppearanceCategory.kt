package io.github.bookshelf.feature.settings.ui.category

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.bookshelf.core.datastore.model.ThemeMode
import io.github.bookshelf.core.designsystem.theme.LocalEinkDimens
import io.github.bookshelf.feature.settings.SettingsUiState
import io.github.bookshelf.feature.settings.SettingsViewModel
import io.github.bookshelf.feature.settings.ui.ChoiceRow
import io.github.bookshelf.feature.settings.ui.ToggleRow

/**
 * Категория «Внешний вид»: тема, плотность, масштаб шрифта и инверсия обложек.
 *
 * У всех настроек раздела есть живой потребитель, поэтому переключатели здесь не обманывают:
 * тема, плотность и кегль применяются через `EinkTheme`, инверсию обложек читает `BookCover`
 * из `LocalInvertCovers` — всё без перезапуска приложения (docs/eink-ux.md §2).
 *
 * `textOnlyMode` намеренно не показывается: потребителя у настройки нет (проверено аудитом),
 * а выключенное поле без действия читается как сломанное.
 */
@Composable
internal fun AppearanceCategory(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    val dimens = LocalEinkDimens.current
    val settings = state.settings

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = dimens.gutter),
    ) {
        item(key = "appearance-theme") {
            // Варианты берутся из `ThemeMode`, как у выбора вида полки: список и подпись текущего
            // значения не могут разойтись, потому что строятся одной функцией.
            ChoiceRow(
                title = "Тема",
                options = ThemeMode.entries.map(::themeLabel),
                selected = themeLabel(settings.themeMode),
                onPick = { label ->
                    viewModel.onSetThemeMode(ThemeMode.entries.first { themeLabel(it) == label })
                },
                subtitle = "«Системная» следует за настройкой Android",
            )
        }
        item(key = "appearance-density") {
            ChoiceRow(
                title = "Плотность",
                options = DENSITY_OPTIONS,
                selected = settings.densityLabel,
                onPick = viewModel::onSetDensity,
                subtitle = "Сколько строк и книг помещается на экран",
            )
        }
        item(key = "appearance-font-scale") {
            ChoiceRow(
                title = "Масштаб шрифта",
                options = FONT_SCALE_OPTIONS,
                // Число отделяем от знака процента: подпись читается человеком, а в настройку
                // уходит то же значение, что принимает `onSetFontScale`.
                selected = "${settings.fontScalePercent} %",
                onPick = { label ->
                    label.substringBefore(' ').toIntOrNull()?.let(viewModel::onSetFontScale)
                },
            )
        }
        item(key = "appearance-invert-covers") {
            ToggleRow(
                title = "Инвертировать обложки",
                subtitle = "Для тёмных обложек на e-ink: цвета становятся негативом",
                enabled = settings.invertCovers,
                onToggle = viewModel::onSetInvertCovers,
            )
        }
    }
}

/** Название темы по-русски; живёт рядом с выбором, чтобы подпись и вариант не разъехались. */
private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.LIGHT -> "Светлая"
    ThemeMode.DARK -> "Тёмная"
    ThemeMode.SYSTEM -> "Системная"
}

/** Подписи плотности — ровно те слова, которые разбирает `SettingsViewModel.onSetDensity`. */
private val DENSITY_OPTIONS = listOf("Крупный", "Обычный", "Плотный")

/** Допустимые проценты из docs/eink-ux.md §2.2: другие значения маппинг не знает. */
private val FONT_SCALE_OPTIONS = listOf("100 %", "115 %", "130 %", "150 %")
