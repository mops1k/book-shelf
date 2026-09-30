package io.github.bookshelf.core.fb2opt

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Проверка самой проверки: правильно ли тест снимает фильтры строк PNG.
 *
 * Все утверждения о «пиксели не изменились» держатся на [PngTestSupport.pixels], и если бы он
 * снимал фильтры неверно, тесты молча подтверждали бы испорченную картинку. Поэтому здесь файл
 * собирается вручную, а ожидаемые пиксели посчитаны по спецификации PNG:
 *
 * * картинка 3×2, три канала (RGB), 8 бит на канал;
 * * первая строка отфильтрована `Sub`, вторая — `Up`;
 * * значения байтов фильтрованных строк выписаны в тесте числами.
 */
class PngTestSupportTest {

    @Test
    fun `filters are undone according to the png spec`() {
        val png = PngTestSupport.build(width = WIDTH, height = HEIGHT, colorType = RGB, raw = rawRows())

        val pixels = PngTestSupport.pixels(png)

        assertThat(pixels).asList().containsExactly(
            argb(10, 20, 30), argb(40, 50, 60), argb(70, 80, 90),
            argb(1, 2, 3), argb(4, 5, 6), argb(7, 8, 9),
        ).inOrder()
    }

    /**
     * Строки, как они лежат в файле.
     *
     * Первая: фильтр `Sub` (1) — из каждого байта вычтен байт на расстоянии одного пикселя.
     * Вторая: фильтр `Up` (2) — вычтена та же позиция предыдущей строки.
     */
    private fun rawRows(): ByteArray = byteArrayOf(
        1, 10, 20, 30, 30, 30, 30, 30, 30, 30,
        2, -9, -18, -27, -36, -45, -54, -63, -72, -81,
    )

    private fun argb(red: Int, green: Int, blue: Int): Int =
        (OPAQUE shl 24) or (red shl 16) or (green shl 8) or blue

    private companion object {
        const val WIDTH = 3
        const val HEIGHT = 2
        const val RGB = 2
        const val OPAQUE = 255
    }
}
