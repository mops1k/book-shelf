package io.github.bookshelf.core.designsystem.component

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Матрица инверсии обложек — единственная часть [BookCover] с чистой логикой, поэтому она
 * проверяется обычным JUnit без устройства (docs/tdd.md §7).
 *
 * Смысл проверок: настройка «инвертировать обложки» не должна ни «съедать» прозрачность
 * PNG-обложек, ни уходить в диапазон -255..0 (тогда Coil нарисовал бы чёрный прямоугольник).
 */
class BookCoverColorMatrixTest {

    @Test
    fun inverts_each_colour_channel_and_keeps_alpha() {
        val matrix = invertedColorMatrix()

        assertThat(matrix).hasLength(20)
        // Диагональ цветовых каналов — «минус единица»: 0 становится 255, 255 становится 0.
        assertThat(matrix[0]).isEqualTo(-1f)
        assertThat(matrix[6]).isEqualTo(-1f)
        assertThat(matrix[12]).isEqualTo(-1f)
        // Свободный член возвращает результат в диапазон 0..255 после умножения на -1.
        assertThat(matrix[4]).isEqualTo(255f)
        assertThat(matrix[9]).isEqualTo(255f)
        assertThat(matrix[14]).isEqualTo(255f)
        // Альфа не инвертируется: иначе полупрозрачная обложка стала бы полностью прозрачной.
        assertThat(matrix[18]).isEqualTo(1f)
        assertThat(matrix[19]).isEqualTo(0f)
    }

    @Test
    fun applying_inversion_twice_returns_the_original_colour() {
        val matrix = invertedColorMatrix()
        val colour = floatArrayOf(0f, 128f, 255f, 128f)

        val once = apply(matrix = matrix, colour = colour)
        val twice = apply(matrix = matrix, colour = once)

        assertThat(once.toList()).isEqualTo(listOf(255f, 127f, 0f, 128f))
        assertThat(twice.toList()).isEqualTo(colour.toList())
    }

    /**
     * Применяет матрицу 4×5 к цвету так же, как это делает графический конвейер: `R' = m00·R +
     * m01·G + m02·B + m03·A + m04`.
     *
     * Свой мини-рендерер в тесте нужен потому, что `ColorFilter` из Compose в JVM-тесте недоступен:
     * он завязан на `android.graphics`, а проверять здесь надо именно матрицу.
     */
    private fun apply(matrix: FloatArray, colour: FloatArray): FloatArray = FloatArray(4) { row ->
        (0 until 4).fold(matrix[row * 5 + 4]) { value, channel ->
            value + matrix[row * 5 + channel] * colour[channel]
        }
    }
}
