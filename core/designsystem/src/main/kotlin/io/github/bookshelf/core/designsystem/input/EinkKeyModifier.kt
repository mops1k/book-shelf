package io.github.bookshelf.core.designsystem.input

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onKeyEvent

/**
 * Обработка аппаратных кнопок на уровне Composable.
 *
 * Возвращает `true`, если событие поглощено: иначе кнопка громкости изменит громкость,
 * а Page Down уйдёт в системную прокрутку.
 */
fun Modifier.onEinkKeys(
    volumeKeysAsPageTurn: Boolean = true,
    onAction: (EinkAction) -> Unit,
): Modifier = this.onKeyEvent { event: KeyEvent ->
    val native = event.nativeKeyEvent
    if (native.action != AndroidKeyEvent.ACTION_DOWN || native.repeatCount != 0) {
        false
    } else {
        val action = keyCodeToEinkAction(native.keyCode, volumeKeysAsPageTurn)
        if (action == null) {
            false
        } else {
            onAction(action)
            true
        }
    }
}
