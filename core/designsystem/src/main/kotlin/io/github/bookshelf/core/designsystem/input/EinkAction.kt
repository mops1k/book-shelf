package io.github.bookshelf.core.designsystem.input

import android.view.KeyEvent

/**
 * Действие интерфейса, не зависящее от конкретной кнопки.
 *
 * Отображение «кнопка → действие» — чистые данные, поэтому оно тестируется обычным JUnit
 * без устройства: на e-ink доступность аппаратных кнопок критична, и ошибка в маппинге
 * ломает основной сценарий («E-ink UX» §3.2).
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
    // Громкость — единственная клавиша, чей смысл зависит от настройки: на устройствах без
    // Page Up/Down листание вешается на неё, иначе она остаётся громкостью.
    KeyEvent.KEYCODE_VOLUME_UP -> if (volumeKeysAsPageTurn) EinkAction.PREVIOUS_PAGE else null
    KeyEvent.KEYCODE_VOLUME_DOWN -> if (volumeKeysAsPageTurn) EinkAction.NEXT_PAGE else null
    // Остальные клавиши — прямое соответствие: карта, а не `when` с десятком веток
    // (CyclomaticComplexMethod считает каждую ветку, и порог 15 упирался именно в неё).
    else -> KEY_TO_ACTION[keyCode]
}

private val KEY_TO_ACTION: Map<Int, EinkAction> = mapOf(
    KeyEvent.KEYCODE_PAGE_UP to EinkAction.PREVIOUS_PAGE,
    KeyEvent.KEYCODE_PAGE_DOWN to EinkAction.NEXT_PAGE,
    KeyEvent.KEYCODE_DPAD_UP to EinkAction.FOCUS_UP,
    KeyEvent.KEYCODE_DPAD_DOWN to EinkAction.FOCUS_DOWN,
    KeyEvent.KEYCODE_DPAD_LEFT to EinkAction.FOCUS_LEFT,
    KeyEvent.KEYCODE_DPAD_RIGHT to EinkAction.FOCUS_RIGHT,
    KeyEvent.KEYCODE_DPAD_CENTER to EinkAction.OPEN,
    KeyEvent.KEYCODE_ENTER to EinkAction.OPEN,
    KeyEvent.KEYCODE_NUMPAD_ENTER to EinkAction.OPEN,
    KeyEvent.KEYCODE_BACK to EinkAction.BACK,
    KeyEvent.KEYCODE_MENU to EinkAction.MENU,
    KeyEvent.KEYCODE_SEARCH to EinkAction.SEARCH,
)

/** Действие приходит один раз на нажатие, а не на каждое автоповторение удержания. */
fun KeyEvent.isInitialPress(): Boolean = repeatCount == 0 && action == KeyEvent.ACTION_DOWN
