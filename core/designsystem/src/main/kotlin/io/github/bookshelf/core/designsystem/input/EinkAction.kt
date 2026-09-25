package io.github.bookshelf.core.designsystem.input

import android.view.KeyEvent

/**
 * Действие интерфейса, не зависящее от конкретной кнопки.
 *
 * Отображение «кнопка → действие» — чистые данные, поэтому оно тестируется обычным JUnit
 * без устройства: на e-ink доступность аппаратных кнопок критична, и ошибка в маппинге
 * ломает основной сценарий (docs/eink-ux.md §3.2).
 */
enum class EinkAction {
    PREVIOUS_PAGE,
    NEXT_PAGE,
    OPEN,
    BACK,
    MENU,
    FOCUS_UP,
    FOCUS_DOWN,
    FOCUS_LEFT,
    FOCUS_RIGHT,
    SEARCH,
}

/**
 * Маппинг физической кнопки в действие.
 *
 * @param volumeKeysAsPageTurn — настройка: на устройствах без Page Up/Down листание
 *   вешается на громкость (иначе кнопки громкости остаются громкостью).
 */
fun keyCodeToEinkAction(
    keyCode: Int,
    volumeKeysAsPageTurn: Boolean = true,
): EinkAction? = when (keyCode) {
    KeyEvent.KEYCODE_PAGE_UP -> EinkAction.PREVIOUS_PAGE
    KeyEvent.KEYCODE_PAGE_DOWN -> EinkAction.NEXT_PAGE
    KeyEvent.KEYCODE_VOLUME_UP -> if (volumeKeysAsPageTurn) EinkAction.PREVIOUS_PAGE else null
    KeyEvent.KEYCODE_VOLUME_DOWN -> if (volumeKeysAsPageTurn) EinkAction.NEXT_PAGE else null
    KeyEvent.KEYCODE_DPAD_UP -> EinkAction.FOCUS_UP
    KeyEvent.KEYCODE_DPAD_DOWN -> EinkAction.FOCUS_DOWN
    KeyEvent.KEYCODE_DPAD_LEFT -> EinkAction.FOCUS_LEFT
    KeyEvent.KEYCODE_DPAD_RIGHT -> EinkAction.FOCUS_RIGHT
    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> EinkAction.OPEN
    KeyEvent.KEYCODE_BACK -> EinkAction.BACK
    KeyEvent.KEYCODE_MENU -> EinkAction.MENU
    KeyEvent.KEYCODE_SEARCH -> EinkAction.SEARCH
    else -> null
}

/** Действие приходит один раз на нажатие, а не на каждое автоповторение удержания. */
fun KeyEvent.isInitialPress(): Boolean = repeatCount == 0 && action == KeyEvent.ACTION_DOWN
