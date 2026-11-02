package io.github.bookshelf.feature.launcher.ui

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * Где поставить меню долгого нажатия относительно плитки приложения.
 *
 * Меню — карточка у иконки, как в вендорском Launcher3 (скриншот 02.11.2026), а не отдельный
 * экран: так видно, к какому приложению относятся действия. Под плиткой, если помещается; иначе
 * над ней; по горизонтали карточка не выезжает за поля экрана. Чистая функция — края экрана
 * проверяются тестами без Compose.
 */
object MenuPlacement {

    /**
     * @param anchor левый верхний угол плитки в координатах окна.
     * @param anchorSize размер плитки (с подписью).
     * @param margin поля от краёв экрана.
     */
    fun place(
        anchor: IntOffset,
        anchorSize: IntSize,
        menuSize: IntSize,
        screenSize: IntSize,
        margin: Int,
    ): IntOffset {
        val maxX = (screenSize.width - menuSize.width - margin).coerceAtLeast(margin)
        val x = anchor.x.coerceIn(margin, maxX)

        val below = anchor.y + anchorSize.height + margin
        val above = anchor.y - margin - menuSize.height
        val fitsBelow = below + menuSize.height <= screenSize.height - margin
        val maxY = (screenSize.height - menuSize.height - margin).coerceAtLeast(margin)
        val y = if (fitsBelow) below else above.coerceIn(margin, maxY)

        return IntOffset(x = x, y = y)
    }
}
