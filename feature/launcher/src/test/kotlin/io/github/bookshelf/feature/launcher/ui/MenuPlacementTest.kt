package io.github.bookshelf.feature.launcher.ui

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Позиционирование меню долгого нажатия (ADR-015, «E-ink UX» §6.9).
 *
 * Меню — карточка у иконки, как в вендорском Launcher3. Где именно её поставить, считается
 * чистой функцией: под плиткой, если помещается, иначе над ней; по горизонтали карточка
 * прижимается к экрану полями. Тест проверяет края экрана — там ошибка видна сразу.
 */
class MenuPlacementTest {

    private val screen = IntSize(width = 1072, height = 1448)
    private val menu = IntSize(width = 400, height = 500)
    private val tile = IntSize(width = 200, height = 240)
    private val margin = 24

    @Test
    fun menu_is_placed_below_the_tile_when_it_fits() {
        val position = MenuPlacement.place(
            anchor = IntOffset(x = 400, y = 300),
            anchorSize = tile,
            menuSize = menu,
            screenSize = screen,
            margin = margin,
        )

        assertThat(position).isEqualTo(IntOffset(x = 400, y = 300 + tile.height + margin))
    }

    @Test
    fun menu_is_placed_above_the_tile_when_the_bottom_does_not_fit() {
        val position = MenuPlacement.place(
            anchor = IntOffset(x = 400, y = 1200),
            anchorSize = tile,
            menuSize = menu,
            screenSize = screen,
            margin = margin,
        )

        assertThat(position).isEqualTo(IntOffset(x = 400, y = 1200 - margin - menu.height))
    }

    @Test
    fun menu_is_pushed_from_the_left_edge() {
        val position = MenuPlacement.place(
            anchor = IntOffset(x = 0, y = 300),
            anchorSize = tile,
            menuSize = menu,
            screenSize = screen,
            margin = margin,
        )

        assertThat(position.x).isEqualTo(margin)
    }

    @Test
    fun menu_is_pushed_from_the_right_edge() {
        val position = MenuPlacement.place(
            anchor = IntOffset(x = 1000, y = 300),
            anchorSize = tile,
            menuSize = menu,
            screenSize = screen,
            margin = margin,
        )

        assertThat(position.x).isEqualTo(screen.width - menu.width - margin)
    }

    @Test
    fun menu_never_goes_off_the_top() {
        val position = MenuPlacement.place(
            anchor = IntOffset(x = 400, y = 100),
            anchorSize = IntSize(width = 200, height = 1000),
            menuSize = IntSize(width = 700, height = 900),
            screenSize = screen,
            margin = margin,
        )

        assertThat(position.y).isAtLeast(margin)
    }
}
