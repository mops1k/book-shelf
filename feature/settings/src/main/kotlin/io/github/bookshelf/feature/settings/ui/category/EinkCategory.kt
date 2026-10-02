package io.github.bookshelf.feature.settings.ui.category

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.bookshelf.core.designsystem.theme.LocalEinkDimens
import io.github.bookshelf.core.datastore.model.RefreshModeLike
import io.github.bookshelf.feature.settings.AppSettingsUi
import io.github.bookshelf.feature.settings.SettingsUiState
import io.github.bookshelf.feature.settings.SettingsViewModel
import io.github.bookshelf.feature.settings.ui.CategoryPending
import io.github.bookshelf.feature.settings.ui.ChoiceOption
import io.github.bookshelf.feature.settings.ui.ChoiceRow
import io.github.bookshelf.feature.settings.ui.ValueChoiceRow
import io.github.bookshelf.feature.settings.ui.ToggleRow
import io.github.bookshelf.feature.settings.R

/**
 * Категория «Экран (e-ink)»: режим обновления, события полного кадра, подсветка и клавиши.
 *
 * Все настройки раздела подключены к потребителям: режим обновления, интервал GC и подсветку
 * применяет `MainActivity` через `EinkController`, события полного кадра читает
 * `ViewEinkRefreshHost`, клавиши громкости — `EinkScaffold` через `LocalVolumeKeysAsPageTurn`.
 * Ничего не перезапускается: подписка на настройки живёт в `MainViewModel` («E-ink UX» §4).
 *
 * Тап-зоны не показываются: `TapZoneLayout` в проекте нигде не используется, и настройка не на что
 * не влияет, а переключатель без действия — обман.
 */
@Composable
internal fun EinkCategory(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    val dimens = LocalEinkDimens.current
    val settings = state.settings

    // Настройки показываются только там, где ими есть что менять. На устройстве без вендорского
    // контроллера (например Bigme: `NoopEinkController`) режим обновления, интервал GC и подсветка
    // не делают ничего — переключатели без действия мы не показываем (решение Александра,
    // 28.09.2026). Признак здесь именно про контроллер, а не про тип экрана: бумага без команд —
    // обычное дело.
    if (!state.diagnostics.hasVendorCommands) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = dimens.gutter),
        ) {
            CategoryPending(
                stringResource(R.string.settings_eink_unavailable),
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = dimens.gutter),
    ) {
        item(key = "eink-refresh-mode") {
            ChoiceRow(
                title = stringResource(R.string.settings_eink_refresh_mode),
                options = RefreshModeLike.entries.map { mode ->
                    ChoiceOption(id = mode.name, labelRes = refreshModeLabelRes(mode))
                },
                selected = settings.refreshMode.name,
                onPick = viewModel::onSetRefreshMode,
                subtitle = stringResource(R.string.settings_eink_refresh_mode_hint),
            )
        }
        einkRefreshToggles(settings = settings, viewModel = viewModel)
        item(key = "eink-gc-interval") {
            ValueChoiceRow(
                title = stringResource(R.string.settings_eink_gc_interval),
                options = GC_INTERVAL_CHOICES,
                selected = settings.gcInterval,
                labelRes = { R.string.settings_number_plain },
                onPick = viewModel::onSetGcInterval,
                subtitle = stringResource(R.string.settings_eink_gc_interval_hint),
            )
        }
        // Подсветку показываем только там, где ею можно управлять: `supportsFrontLight`
        // выставляется по `EinkController.frontLight()`, и на устройстве без подсветки строки нет.
        if (state.diagnostics.supportsFrontLight) {
            item(key = "eink-front-light") {
                ValueChoiceRow(
                    title = stringResource(R.string.settings_eink_backlight),
                    options = FRONT_LIGHT_CHOICES,
                    selected = settings.frontLightLevel ?: FRONT_LIGHT_CHOICES.first(),
                    labelRes = { R.string.settings_number_plain },
                    onPick = viewModel::onSetFrontLight,
                    subtitle = stringResource(R.string.settings_eink_backlight_hint),
                )
            }
        }
    }
}

/** Подписи режима — ровно те слова, которые разбирает `SettingsViewModel.onSetRefreshMode`. */
/** Подпись режима обновления. Ресурс: значение настройки — имя перечисления. */
private fun refreshModeLabelRes(mode: RefreshModeLike): Int = when (mode) {
    RefreshModeLike.AUTO -> R.string.settings_eink_mode_auto
    RefreshModeLike.FULL -> R.string.settings_eink_mode_full
    RefreshModeLike.FAST -> R.string.settings_eink_mode_fast
}

/**
 * Значения интервала GC: шаг в пять обновлений покрывает и «почти каждый кадр», и «редко».
 *
 * Диапазон шире (любое число от 1) — это осознанно: список даёт быстрый выбор, а не ограничение.
 */
private val GC_INTERVAL_CHOICES = listOf(5, 10, 20, 50)

/** Уровни подсветки: пять шагов от выключенной до максимальной. */
private val FRONT_LIGHT_CHOICES = listOf(0, 25, 50, 75, 100)


/** Тумблеры полного обновления и клавиш громкости: вынесены ради предела длины `EinkCategory`. */
private fun LazyListScope.einkRefreshToggles(
    settings: AppSettingsUi,
    viewModel: SettingsViewModel,
) {
    item(key = "eink-refresh-screen") {
        ToggleRow(
            title = stringResource(R.string.settings_eink_full_on_screen),
            subtitle = stringResource(R.string.settings_eink_full_on_screen_hint),
            enabled = settings.fullRefreshOnScreenChange,
            onToggle = viewModel::onSetRefreshOnScreenChange,
        )
    }
    item(key = "eink-refresh-dialog") {
        ToggleRow(
            title = stringResource(R.string.settings_eink_full_on_dialog),
            subtitle = stringResource(R.string.settings_eink_full_on_dialog_hint),
            enabled = settings.fullRefreshOnDialog,
            onToggle = viewModel::onSetRefreshOnDialog,
        )
    }
    item(key = "eink-refresh-page") {
        ToggleRow(
            title = stringResource(R.string.settings_eink_full_on_page),
            subtitle = stringResource(R.string.settings_eink_full_on_page_hint),
            enabled = settings.fullRefreshOnPage,
            onToggle = viewModel::onSetFullRefreshOnPage,
        )
    }
    item(key = "eink-volume-keys") {
        ToggleRow(
            title = stringResource(R.string.settings_eink_volume_keys),
            subtitle = stringResource(R.string.settings_eink_volume_keys_hint),
            enabled = settings.volumeKeysAsPageTurn,
            onToggle = viewModel::onSetVolumeKeysAsPageTurn,
        )
    }
}
