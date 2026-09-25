package io.github.bookshelf.core.designsystem.input

import android.view.KeyEvent
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Маппинг аппаратных кнопок — единственная часть дизайн-системы с чистой логикой,
 * поэтому она покрыта тестами с первого дня (docs/tdd.md §7).
 */
class EinkActionTest {

    @Test
    fun page_up_maps_to_previous_page() {
        assertThat(keyCodeToEinkAction(KeyEvent.KEYCODE_PAGE_UP)).isEqualTo(EinkAction.PREVIOUS_PAGE)
    }

    @Test
    fun page_down_maps_to_next_page() {
        assertThat(keyCodeToEinkAction(KeyEvent.KEYCODE_PAGE_DOWN)).isEqualTo(EinkAction.NEXT_PAGE)
    }

    @Test
    fun volume_keys_turn_pages_when_setting_enabled() {
        val up = keyCodeToEinkAction(KeyEvent.KEYCODE_VOLUME_UP, volumeKeysAsPageTurn = true)
        val down = keyCodeToEinkAction(KeyEvent.KEYCODE_VOLUME_DOWN, volumeKeysAsPageTurn = true)

        assertThat(up).isEqualTo(EinkAction.PREVIOUS_PAGE)
        assertThat(down).isEqualTo(EinkAction.NEXT_PAGE)
    }

    @Test
    fun volume_keys_keep_system_role_when_setting_disabled() {
        val up = keyCodeToEinkAction(KeyEvent.KEYCODE_VOLUME_UP, volumeKeysAsPageTurn = false)
        val down = keyCodeToEinkAction(KeyEvent.KEYCODE_VOLUME_DOWN, volumeKeysAsPageTurn = false)

        assertThat(up).isNull()
        assertThat(down).isNull()
    }

    @Test
    fun dpad_center_and_enter_open_item() {
        assertThat(keyCodeToEinkAction(KeyEvent.KEYCODE_DPAD_CENTER)).isEqualTo(EinkAction.OPEN)
        assertThat(keyCodeToEinkAction(KeyEvent.KEYCODE_ENTER)).isEqualTo(EinkAction.OPEN)
    }

    @Test
    fun unknown_key_is_not_consumed() {
        assertThat(keyCodeToEinkAction(KeyEvent.KEYCODE_A)).isNull()
    }
}
