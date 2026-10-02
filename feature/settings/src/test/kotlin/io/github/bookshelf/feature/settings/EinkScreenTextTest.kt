package io.github.bookshelf.feature.settings

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.designsystem.text.AppText
import io.github.bookshelf.core.eink.device.EinkScreen
import org.junit.Test

/**
 * Подпись типа экрана выбирает экран приложения, а не `:core:eink`.
 *
 * Дефект, ради которого появился этот маппинг: `EinkScreen.displayName` отдавал готовую русскую
 * строку из core-модуля, у которого нет ресурсов, — в английском интерфейсе «Экран: обычный экран»
 * оставался русским. Теперь ядро отдаёт вид, а строку выбирает тот, у кого есть `Context`.
 */
class EinkScreenTextTest {

    @Test
    fun `every screen kind has its own text`() {
        assertThat(EinkScreen.NORMAL.text()).isEqualTo(AppText(R.string.settings_screen_normal))
        assertThat(EinkScreen.EINK_COLOR.text()).isEqualTo(AppText(R.string.settings_screen_eink_color))
        assertThat(EinkScreen.EINK_MONOCHROME.text()).isEqualTo(AppText(R.string.settings_screen_eink_mono))
        assertThat(EinkScreen.EINK_UNKNOWN.text()).isEqualTo(AppText(R.string.settings_screen_eink_unknown))
    }
}
