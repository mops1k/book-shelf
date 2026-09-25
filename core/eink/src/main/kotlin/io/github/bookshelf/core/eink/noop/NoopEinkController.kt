package io.github.bookshelf.core.eink.noop

import android.view.View
import io.github.bookshelf.core.eink.EinkController
import io.github.bookshelf.core.eink.RefreshMode
import io.github.bookshelf.core.eink.vendor.EinkVendor

/**
 * Безопасная реализация по умолчанию (docs/eink-ux.md §8.4).
 *
 * Это дефолт для любого устройства, которое не распознано как e-ink, и фолбэк для распознанного
 * устройства, на котором вендорский SDK недоступен. Ключевое свойство: **ничего не ломает** —
 * приложение обязано быть полностью работоспособным без вендорских API, теряя только «быстрый
 * режим» и управление подсветкой.
 *
 * [setRefreshMode] и [fullRefresh] сводятся к `view.invalidate()`: на обычном экране это обычная
 * перерисовка, на e-ink — минимально возможный запрос обновления. [setGcInterval] и [setFrontLight]
 * — честные no-op, [frontLight] возвращает `null` («устройство не сообщает уровень»).
 */
class NoopEinkController : EinkController {

    override val isEinkDevice: Boolean = false

    override val vendor: EinkVendor = EinkVendor.UNKNOWN

    override val supportsFastMode: Boolean = false

    override fun setRefreshMode(view: View, mode: RefreshMode) {
        // Никаких вендорских режимов: единственное, что можно сделать безопасно, — перерисовать кадр.
        view.invalidate()
    }

    override fun fullRefresh(view: View) {
        view.invalidate()
    }

    override fun setGcInterval(partialUpdates: Int) = Unit

    override fun setFrontLight(level: Int) = Unit

    override fun frontLight(): Int? = null
}
