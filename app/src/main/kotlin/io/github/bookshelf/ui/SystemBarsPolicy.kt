package io.github.bookshelf.ui

import io.github.bookshelf.core.datastore.model.SystemBarsMode

/**
 * Политика системных панелей: режим плюс текущий экран (ADR-015).
 *
 * Вынесена из `MainActivity` в чистую функцию, потому что решение «скрывать или нет» — не про
 * окно Android, а про маршрут: его проверяют тесты на все четыре режима, а активити только
 * применяет ответ к `WindowInsetsController`.
 */
object SystemBarsPolicy {

    /**
     * Нужно ли скрывать системные панели.
     *
     * @param isShelf текущий экран — полка (главный экран лаунчера).
     * @param isApps текущий экран — список приложений.
     */
    fun shouldHideBars(mode: SystemBarsMode, isShelf: Boolean, isApps: Boolean): Boolean = when (mode) {
        SystemBarsMode.HIDE_ALWAYS -> true
        SystemBarsMode.HIDE_ON_SHELF -> isShelf
        // Единственный режим, где панели видны хоть где-то: на экране приложений они нужны,
        // чтобы видеть заряд и сеть при запуске чужих программ.
        SystemBarsMode.HIDE_EXCEPT_APPS -> !isApps
        SystemBarsMode.SHOW_ALWAYS -> false
    }
}
