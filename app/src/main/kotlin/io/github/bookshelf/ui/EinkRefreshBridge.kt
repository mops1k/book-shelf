package io.github.bookshelf.ui

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import io.github.bookshelf.core.designsystem.refresh.LocalEinkRefresh
import io.github.bookshelf.core.designsystem.refresh.RefreshReason as UiRefreshReason
import io.github.bookshelf.core.eink.EinkController
import io.github.bookshelf.core.eink.refresh.EinkRefreshHost
import io.github.bookshelf.core.eink.refresh.RefreshReason as EinkRefreshReason

/**
 * Мост между UI-слоем и e-ink-слоем — единственное место, где они встречаются
 * («Архитектура» §6).
 *
 * Дизайн-система объявляет только «здесь нужен полный кадр» (`LocalEinkRefresh`), а что делать —
 * решает контроллер устройства: на Onyx это вендорский режим обновления, на Rockchip — рефлексия,
 * на обычном устройстве — безвредный `invalidate()`. Ни один экран про это не знает.
 */
@Composable
fun EinkRefreshBridge(
    controller: EinkController,
    settings: EinkRefreshSettings = EinkRefreshSettings(),
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val host = remember(controller, view, settings) {
        ViewEinkRefreshHost(controller = controller, rootView = view, settings = settings)
    }
    val requestRefresh: (UiRefreshReason) -> Unit = remember(host) { { host.onReason(it.toEinkReason()) } }

    CompositionLocalProvider(LocalEinkRefresh provides requestRefresh, content = content)
}

/** Какие события пользователь считает достойными полного перерисона (настройка, «E-ink UX» §4). */
data class EinkRefreshSettings(
    val onScreenChange: Boolean = true,
    val onDialog: Boolean = true,
    val onPageChange: Boolean = true,
    val onScanEvents: Boolean = true,
)

/**
 * Реализация [EinkRefreshHost] поверх корневого view экрана.
 *
 * Значения [EinkRefreshSettings] приходят из `SettingsRepository` через `ThemeState` (`MainActivity`):
 * `remember(controller, view, settings)` в [EinkRefreshBridge] пересоздаёт host при изменении
 * настроек, поэтому переключатель в разделе «Экран (e-ink)» действует без перезапуска.
 */
class ViewEinkRefreshHost(
    private val controller: EinkController,
    private val rootView: View,
    private val settings: EinkRefreshSettings = EinkRefreshSettings(),
) : EinkRefreshHost {

    override fun onReason(reason: EinkRefreshReason) {
        if (!isEnabled(reason)) return
        controller.fullRefresh(rootView)
    }

    private fun isEnabled(reason: EinkRefreshReason): Boolean = when (reason) {
        EinkRefreshReason.SCREEN_CHANGED -> settings.onScreenChange
        EinkRefreshReason.DIALOG_OPENED, EinkRefreshReason.DIALOG_CLOSED -> settings.onDialog
        EinkRefreshReason.PAGE_CHANGED, EinkRefreshReason.FILTER_APPLIED -> settings.onPageChange
        EinkRefreshReason.SCAN_STARTED, EinkRefreshReason.SCAN_FINISHED -> settings.onScanEvents
        EinkRefreshReason.MANUAL -> true
    }
}

/**
 * Перечисления совпадают по значениям, но живут в разных модулях осознанно: `:core:eink` не должен
 * зависеть от Compose и дизайн-системы. Перевод — здесь, и он исчерпывающий: новый повод для
 * перерисона сломает компиляцию, а не останется незамеченным.
 */
internal fun UiRefreshReason.toEinkReason(): EinkRefreshReason = when (this) {
    UiRefreshReason.SCREEN_CHANGED -> EinkRefreshReason.SCREEN_CHANGED
    UiRefreshReason.DIALOG_OPENED -> EinkRefreshReason.DIALOG_OPENED
    UiRefreshReason.DIALOG_CLOSED -> EinkRefreshReason.DIALOG_CLOSED
    UiRefreshReason.PAGE_CHANGED -> EinkRefreshReason.PAGE_CHANGED
    UiRefreshReason.SCAN_STARTED -> EinkRefreshReason.SCAN_STARTED
    UiRefreshReason.SCAN_FINISHED -> EinkRefreshReason.SCAN_FINISHED
    UiRefreshReason.FILTER_APPLIED -> EinkRefreshReason.FILTER_APPLIED
    UiRefreshReason.MANUAL -> EinkRefreshReason.MANUAL
}
