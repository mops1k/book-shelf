package io.github.bookshelf.feature.settings.ui.category

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.bookshelf.core.datastore.model.EinkDensityLike
import io.github.bookshelf.core.datastore.model.ThemeMode
import io.github.bookshelf.core.designsystem.theme.LocalEinkDimens
import io.github.bookshelf.feature.settings.SettingsUiState
import io.github.bookshelf.feature.settings.SettingsViewModel
import io.github.bookshelf.feature.settings.ui.ChoiceOption
import io.github.bookshelf.feature.settings.ui.ChoiceRow
import io.github.bookshelf.feature.settings.ui.ValueChoiceRow
import io.github.bookshelf.feature.settings.ui.ToggleRow
import io.github.bookshelf.feature.settings.R

/**
 * Категория «Внешний вид»: тема, плотность, масштаб шрифта, инверсия обложек и клавиши громкости.
 *
 * У всех настроек раздела есть живой потребитель, поэтому переключатели здесь не обманывают:
 * тема, плотность и кегль применяются через `EinkTheme`, инверсию обложек читает `BookCover`
 * из `LocalInvertCovers`, клавиши громкости — `EinkScaffold` из `LocalVolumeKeysAsPageTurn` —
 * всё без перезапуска приложения («E-ink UX» §2).
 *
 * Клавиши громкости стоят здесь, а не в разделе «Экран (e-ink)»: они работают на любом
 * устройстве, вендорский контроллер им не нужен, а раздел e-ink без вендорских команд скрыт
 * целиком. Иначе самая нужная на e-ink настройка пропадала бы вместе с ним.
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
                title = stringResource(R.string.settings_appearance_theme),
                options = ThemeMode.entries.map { mode ->
                    ChoiceOption(id = mode.name, labelRes = themeLabelRes(mode))
                },
                selected = settings.themeMode.name,
                onPick = { id -> viewModel.onSetThemeMode(ThemeMode.valueOf(id)) },
                subtitle = stringResource(R.string.settings_appearance_theme_hint),
            )
        }
        item(key = "appearance-density") {
            ChoiceRow(
                title = stringResource(R.string.settings_appearance_density),
                options = EinkDensityLike.entries.map { density ->
                    ChoiceOption(id = density.name, labelRes = densityLabelRes(density))
                },
                selected = settings.density.name,
                onPick = viewModel::onSetDensity,
                subtitle = stringResource(R.string.settings_appearance_density_hint),
            )
        }
        item(key = "appearance-font-scale") {
            // Значение — само число процентов: подпись «130 %» собирается из ресурса, а в настройку
            // уходит то же число, что принимает `onSetFontScale`.
            ValueChoiceRow(
                title = stringResource(R.string.settings_appearance_font_scale),
                options = FONT_SCALE_CHOICES,
                selected = settings.fontScalePercent,
                labelRes = { R.string.settings_font_scale_percent },
                onPick = viewModel::onSetFontScale,
            )
        }
        item(key = "appearance-invert-covers") {
            ToggleRow(
                title = stringResource(R.string.settings_appearance_invert_covers),
                subtitle = stringResource(R.string.settings_appearance_invert_covers_hint),
                enabled = settings.invertCovers,
                onToggle = viewModel::onSetInvertCovers,
            )
        }
        item(key = "appearance-volume-keys") {
            ToggleRow(
                title = stringResource(R.string.settings_appearance_volume_keys),
                subtitle = stringResource(R.string.settings_appearance_volume_keys_hint),
                enabled = settings.volumeKeysAsPageTurn,
                onToggle = viewModel::onSetVolumeKeysAsPageTurn,
            )
        }
    }
}

/** Название темы; живёт рядом с выбором, чтобы подпись и вариант не разъехались. Ресурс. */
private fun themeLabelRes(mode: ThemeMode): Int = when (mode) {
    ThemeMode.LIGHT -> R.string.settings_theme_light
    ThemeMode.DARK -> R.string.settings_theme_dark
    ThemeMode.SYSTEM -> R.string.settings_theme_system
}

/** Подпись плотности. Ресурс: значение настройки — имя перечисления, а не слово. */
private fun densityLabelRes(density: EinkDensityLike): Int = when (density) {
    EinkDensityLike.LARGE -> R.string.settings_density_large
    EinkDensityLike.NORMAL -> R.string.settings_density_normal
    EinkDensityLike.COMPACT -> R.string.settings_density_compact
}

/** Допустимые проценты из «E-ink UX» §2.2: другие значения маппинг не знает. */
private val FONT_SCALE_CHOICES = listOf(100, 115, 130, 150)
