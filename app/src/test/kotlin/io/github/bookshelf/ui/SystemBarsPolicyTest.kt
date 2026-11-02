package io.github.bookshelf.ui

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.datastore.model.SystemBarsMode
import org.junit.Test

/**
 * Политика системных панелей: четыре режима и текущий экран (ADR-015, «E-ink UX» §6.8).
 *
 * Проверяется таблица целиком, а не по одному удачному случаю: режимы отличаются ровно тем, на
 * каких маршрутах панели скрываются, и ошибка в одном из четырёх вариантов на устройстве выглядит
 * как «панели пропали на карточке книги» или «на полке не вернулись».
 */
class SystemBarsPolicyTest {

    /** Короткий вызов: проверяется таблица, а не сигнатура функции. */
    private fun hide(
        mode: SystemBarsMode,
        shelf: Boolean = false,
        apps: Boolean = false,
    ): Boolean = SystemBarsPolicy.shouldHideBars(mode, isShelf = shelf, isApps = apps)

    @Test
    fun hide_always_hides_on_every_screen() {
        assertThat(hide(SystemBarsMode.HIDE_ALWAYS, shelf = true)).isTrue()
        assertThat(hide(SystemBarsMode.HIDE_ALWAYS, apps = true)).isTrue()
        assertThat(hide(SystemBarsMode.HIDE_ALWAYS)).isTrue()
    }

    @Test
    fun hide_on_shelf_hides_only_on_the_shelf() {
        assertThat(hide(SystemBarsMode.HIDE_ON_SHELF, shelf = true)).isTrue()
        assertThat(hide(SystemBarsMode.HIDE_ON_SHELF, apps = true)).isFalse()
        assertThat(hide(SystemBarsMode.HIDE_ON_SHELF)).isFalse()
    }

    @Test
    fun hide_except_apps_shows_bars_only_on_app_list() {
        assertThat(hide(SystemBarsMode.HIDE_EXCEPT_APPS, apps = true)).isFalse()
        assertThat(hide(SystemBarsMode.HIDE_EXCEPT_APPS, shelf = true)).isTrue()
        assertThat(hide(SystemBarsMode.HIDE_EXCEPT_APPS)).isTrue()
    }

    @Test
    fun show_always_never_hides() {
        assertThat(hide(SystemBarsMode.SHOW_ALWAYS, shelf = true)).isFalse()
        assertThat(hide(SystemBarsMode.SHOW_ALWAYS, apps = true)).isFalse()
        assertThat(hide(SystemBarsMode.SHOW_ALWAYS)).isFalse()
    }
}
