package io.github.bookshelf.feature.settings.ui.category

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.bookshelf.core.designsystem.theme.LocalEinkDimens
import io.github.bookshelf.feature.settings.SettingsUiState
import io.github.bookshelf.feature.settings.SettingsViewModel
import io.github.bookshelf.feature.settings.ui.CategoryPending
import io.github.bookshelf.feature.settings.ui.ChoiceRow
import io.github.bookshelf.feature.settings.ui.ToggleRow

/**
 * Категория «Экран (e-ink)»: режим обновления, события полного кадра, подсветка и клавиши.
 *
 * Все настройки раздела подключены к потребителям: режим обновления, интервал GC и подсветку
 * применяет `MainActivity` через `EinkController`, события полного кадра читает
 * `ViewEinkRefreshHost`, клавиши громкости — `EinkScaffold` через `LocalVolumeKeysAsPageTurn`.
 * Ничего не перезапускается: подписка на настройки живёт в `MainViewModel` (docs/eink-ux.md §4).
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
                "Управление обновлением экрана недоступно: приложение не нашло вендорский контроллер " +
                    "e-ink на этом устройстве. Здесь появятся режим обновления, события полного кадра, " +
                    "интервал полного обновления и подсветка — как только устройство их поддержит.",
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
                title = "Режим обновления экрана",
                options = REFRESH_MODE_OPTIONS,
                selected = settings.refreshModeLabel,
                onPick = viewModel::onSetRefreshMode,
                subtitle = "«Быстрый» ускоряет перерисовку, но оставляет следы прежнего кадра",
            )
        }
        item(key = "eink-refresh-screen") {
            ToggleRow(
                title = "Полный кадр при смене экрана",
                subtitle = "Самый заметный источник «призраков»: остатки предыдущего экрана",
                enabled = settings.fullRefreshOnScreenChange,
                onToggle = viewModel::onSetRefreshOnScreenChange,
            )
        }
        item(key = "eink-refresh-dialog") {
            ToggleRow(
                title = "Полный кадр при открытии диалога",
                subtitle = "Диалог ложится поверх списка: без полного кадра его края «грязнятся»",
                enabled = settings.fullRefreshOnDialog,
                onToggle = viewModel::onSetRefreshOnDialog,
            )
        }
        item(key = "eink-refresh-page") {
            ToggleRow(
                title = "Полный кадр при листании",
                subtitle = "Текст без остатков предыдущей страницы, но листание заметно медленнее",
                enabled = settings.fullRefreshOnPage,
                onToggle = viewModel::onSetFullRefreshOnPage,
            )
        }
        item(key = "eink-volume-keys") {
            ToggleRow(
                title = "Клавиши громкости листают страницы",
                subtitle = "Для устройств без кнопок Page Up/Down: выключено — клавиши снова меняют громкость",
                enabled = settings.volumeKeysAsPageTurn,
                onToggle = viewModel::onSetVolumeKeysAsPageTurn,
            )
        }
        item(key = "eink-gc-interval") {
            ChoiceRow(
                title = "Интервал полного обновления",
                options = GC_INTERVAL_OPTIONS,
                selected = settings.gcInterval.toString(),
                onPick = { label ->
                    label.toIntOrNull()?.let(viewModel::onSetGcInterval)
                },
                subtitle = "Полный кадр каждые N частичных обновлений: реже — меньше мигания, чаще — чище экран",
            )
        }
        // Подсветку показываем только там, где ею можно управлять: `supportsFrontLight`
        // выставляется по `EinkController.frontLight()`, и на устройстве без подсветки строки нет.
        if (state.diagnostics.supportsFrontLight) {
            item(key = "eink-front-light") {
                ChoiceRow(
                    title = "Подсветка",
                    options = FRONT_LIGHT_OPTIONS,
                    selected = settings.frontLightLevel?.toString().orEmpty(),
                    onPick = { label ->
                        label.toIntOrNull()?.let(viewModel::onSetFrontLight)
                    },
                    subtitle = "Яркость подсветки экрана, если устройство ей управляет",
                )
            }
        }
    }
}

/** Подписи режима — ровно те слова, которые разбирает `SettingsViewModel.onSetRefreshMode`. */
private val REFRESH_MODE_OPTIONS = listOf("Авто", "Полный", "Быстрый")

/**
 * Значения интервала GC: шаг в пять обновлений покрывает и «почти каждый кадр», и «редко».
 *
 * Диапазон шире (любое число от 1) — это осознанно: список даёт быстрый выбор, а не ограничение.
 */
private val GC_INTERVAL_OPTIONS = listOf("5", "10", "20", "50")

/** Уровни подсветки: пять шагов от выключенной до максимальной. */
private val FRONT_LIGHT_OPTIONS = listOf("0", "25", "50", "75", "100")
