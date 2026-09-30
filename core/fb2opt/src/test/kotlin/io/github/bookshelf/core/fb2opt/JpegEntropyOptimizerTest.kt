package io.github.bookshelf.core.fb2opt

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Пережатие сжатых данных JPEG оптимизированными таблицами Хаффмана (`JpegEntropyOptimizer`).
 *
 * Смысл шага: таблицы Хаффмана в книгах почти всегда «стандартные», а не посчитанные по этой
 * картинке. Замена их на посчитанные по фактическим данным уменьшает файл **без изменения пикселей** —
 * коэффициенты остаются теми же, меняется только способ их записи.
 *
 * Тест проверяет то, что можно проверить своими силами: размер стал меньше, а коэффициенты после
 * пережатия совпадают до последнего. Независимая проверка пикселей — сторонним декодером (`djpeg`)
 * на настоящих книгах, она описана в `docs/TESTING.md`: своему кодеку в этом вопросе верить нельзя,
 * он же и проверяется.
 *
 * Фикстуры настоящие: JPEG из книги «Книга Уткина Мы тут жизнь живём» (13,7 МБ, 101 картинка).
 */
class JpegEntropyOptimizerTest {

    private val optimizer = JpegEntropyOptimizer()

    @Test
    fun `a baseline jpeg becomes smaller and keeps its coefficients`() {
        val source = fixture("image-plain.jpg")

        val result = optimizer.optimize(source)

        assertThat(result).isNotNull()
        assertThat(result!!.size).isLessThan(source.size)
        assertThat(coefficientsOf(result)).isEqualTo(coefficientsOf(source))
    }

    @Test
    fun `a jpeg with metadata is recompressed the same way`() {
        val source = fixture("image-with-exif.jpg")

        val result = optimizer.optimize(source)

        assertThat(result).isNotNull()
        assertThat(result!!.size).isLessThan(source.size)
        assertThat(coefficientsOf(result)).isEqualTo(coefficientsOf(source))
    }

    @Test
    fun `a progressive jpeg is left alone`() {
        // Прогрессивный JPEG (маркер кадра C2) устроен иначе: несколько проходов по одним и тем же
        // данным. Пережимать его мы не умеем, и портить не будем.
        val progressive = fixture("image-plain.jpg").withFrameMarker(0xC2)

        assertThat(optimizer.optimize(progressive)).isNull()
    }

    @Test
    fun `a broken jpeg is left alone`() {
        assertThat(optimizer.optimize(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3))).isNull()
    }

    @Test
    fun `something that is not a jpeg is left alone`() {
        assertThat(optimizer.optimize("просто текст".toByteArray())).isNull()
    }

    /** Коэффициенты картинки: то, что определяет пиксели. */
    private fun coefficientsOf(jpeg: ByteArray): List<List<Short>> {
        val structure = checkNotNull(JpegParser.parse(jpeg)) { "картинка не разбирается" }
        val coefficients = checkNotNull(JpegEntropyTranscoder(structure).decode()) { "не читаются данные" }
        return coefficients.blocks.map { blocks -> blocks.flatMap { block -> block.toList() } }
    }

    /** Подменить маркер кадра: `C0` — обычный JPEG, `C2` — прогрессивный. */
    private fun ByteArray.withFrameMarker(marker: Int): ByteArray {
        val result = copyOf()
        var index = 2
        while (index + 4 < result.size) {
            val kind = result[index + 1].toInt() and 0xFF
            if (kind == 0xC0 || kind == 0xC1 || kind == 0xC2) {
                result[index + 1] = marker.toByte()
                return result
            }
            val length = ((result[index + 2].toInt() and 0xFF) shl 8) or (result[index + 3].toInt() and 0xFF)
            index += 2 + length
        }
        return result
    }

    private fun fixture(name: String): ByteArray = checkNotNull(
        javaClass.getResourceAsStream("/$name"),
    ) { "нет фикстуры $name" }.use { stream -> stream.readBytes() }
}
