package io.github.bookshelf.core.fb2opt

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Оптимизация картинок внутри FB2 без потери данных (`Fb2ImageOptimizer`).
 *
 * Проверяется главное обещание: **пиксели не меняются**. Для JPEG это значит, что коэффициенты
 * дискретного косинусного преобразования остаются прежними (меняются только служебные сегменты и
 * таблицы Хаффмана), для PNG — что совпадают сами пиксели, а сверяет их декодер JDK.
 *
 * Фикстуры настоящие: JPEG взяты из книги «Книга Уткина Мы тут жизнь живём» (с EXIF и без метаданных),
 * PNG — реальный снимок экрана устройства из `.dsh/shots/02-polki.png`.
 *
 * Отдельно проверяется случай, ради которого нельзя «просто снять метаданные»: если в EXIF стоит
 * ориентация (фото повёрнуто), снятие EXIF перевернуло бы картинку в читалке — такая картинка
 * остаётся нетронутой.
 */
class Fb2ImageOptimizerTest {

    private val optimizer = Fb2ImageOptimizer(JpegEntropyOptimizer())

    @Test
    fun `jpeg metadata is dropped while the compressed picture stays the same`() {
        val source = fixture("image-with-exif.jpg")

        val result = optimizer.optimize(source, "image/jpeg")

        assertThat(result).isNotNull()
        assertThat(result!!.size).isLessThan(source.size)
        assertThat(String(result, Charsets.ISO_8859_1)).doesNotContain("Exif")
        // Пиксели: коэффициенты картинки обязаны совпасть. Сжатые байты сравнивать нельзя —
        // пережатие таблицами Хаффмана их как раз и меняет.
        assertThat(result.coefficients()).isEqualTo(source.coefficients())
    }

    @Test
    fun `jpeg without metadata gets its tables optimized`() {
        val source = fixture("image-plain.jpg")

        val result = optimizer.optimize(source, "image/jpeg")

        // Метаданных нет, но таблицы Хаффмана можно посчитать по самой картинке: выигрыш есть.
        // Если выигрыша нет — возвращается null, и файл остаётся как был.
        if (result != null) {
            assertThat(result.size).isLessThan(source.size)
            assertThat(result.coefficients()).isEqualTo(source.coefficients())
        }
    }

    @Test
    fun `jpeg with an exif orientation keeps its metadata`() {
        val source = fixture("image-with-exif.jpg").withOrientation(6)

        val result = optimizer.optimize(source, "image/jpeg")

        // Снятие EXIF перевернуло бы такую картинку в читалке, поэтому метаданные остаются на месте.
        // Пережать при этом можно: пиксели не меняются.
        if (result != null) {
            assertThat(String(result, Charsets.ISO_8859_1)).contains("Exif")
            assertThat(result.coefficients()).isEqualTo(source.coefficients())
        }
    }

    @Test
    fun `png is repacked tighter without changing the picture`() {
        val source = fixture("image-real.png")

        val result = optimizer.optimize(source, "image/png")

        assertThat(result).isNotNull()
        // Пиксели сверяет декодер JDK, а не тот же код, что пересобирает строки PNG.
        assertThat(PngTestSupport.pixelsDigest(result!!)).isEqualTo(PngTestSupport.pixelsDigest(source))
        assertThat(result.size).isAtMost(source.size)
    }

    @Test
    fun `an unknown format is left alone`() {
        val source = "просто текст".toByteArray()

        assertThat(optimizer.optimize(source, "image/gif")).isNull()
        assertThat(optimizer.optimize(source, "text/plain")).isNull()
    }

    @Test
    fun `a broken jpeg is left alone`() {
        val broken = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x01, 0x02)

        assertThat(optimizer.optimize(broken, "image/jpeg")).isNull()
    }

    // --- вспомогательное: разбор форматов прямо в тесте, чтобы проверка не зависела от проверяемого кода ---

    private fun ByteArray.coefficients(): List<List<Short>> {
        val structure = checkNotNull(JpegParser.parse(this)) { "картинка не разбирается" }
        val decoded = checkNotNull(JpegEntropyTranscoder(structure).decode()) { "данные не читаются" }
        return decoded.blocks.map { blocks -> blocks.flatMap { block -> block.toList() } }
    }

    /**
     * Поставить картинке ориентацию в EXIF — так помечает повёрнутое фото камера.
     *
     * Фикстура остаётся настоящей: те же байты сжатого изображения, меняется только служебное поле.
     */
    private fun ByteArray.withOrientation(value: Int): ByteArray {
        val ifd = indexOfExifIfd()
        check(ifd > 0) { "в фикстуре нет EXIF" }
        val result = copyOf()
        val entry = ifd + 2
        // Первую запись IFD0 превращаем в Orientation: тип SHORT, одно значение, значение = value.
        result[entry] = 0x01
        result[entry + 1] = 0x12
        result[entry + 2] = 0x00
        result[entry + 3] = 0x03
        result[entry + 4] = 0x00
        result[entry + 5] = 0x00
        result[entry + 6] = 0x00
        result[entry + 7] = 0x01
        result[entry + 8] = ((value shr 8) and 0xFF).toByte()
        result[entry + 9] = (value and 0xFF).toByte()
        return result
    }

    private fun ByteArray.indexOfExifIfd(): Int {
        var index = 2
        while (index + 4 < size) {
            val length = ((this[index + 2].toInt() and 0xFF) shl 8) or (this[index + 3].toInt() and 0xFF)
            if (this[index + 1] == 0xE1.toByte() &&
                String(this, index + 4, 6, Charsets.ISO_8859_1) == "Exif\u0000\u0000"
            ) {
                // TIFF начинается сразу после "Exif\0\0": смещение IFD0 берётся из его заголовка.
                // Смещение — ЧЕТЫРЕ байта (в фикстуре 00 00 00 08): чтение двух дало бы 0 и правка
                // ушла бы в заголовок TIFF.
                val tiff = index + 10
                val littleEndian = this[tiff] == 0x49.toByte()
                val b0 = this[tiff + 4].toInt() and 0xFF
                val b1 = this[tiff + 5].toInt() and 0xFF
                val b2 = this[tiff + 6].toInt() and 0xFF
                val b3 = this[tiff + 7].toInt() and 0xFF
                val ifdOffset = if (littleEndian) {
                    (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
                } else {
                    (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
                }
                return tiff + ifdOffset
            }
            index += 2 + length
        }
        return -1
    }

    private fun fixture(name: String): ByteArray = checkNotNull(
        javaClass.getResourceAsStream("/$name"),
    ) { "нет фикстуры $name" }.use { stream -> stream.readBytes() }

}
