package io.github.bookshelf.core.fb2opt

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Оптимизация картинок внутри FB2 без потери данных (`Fb2ImageOptimizer`).
 *
 * Проверяется главное обещание: **пиксели не меняются**. Для JPEG это значит, что сжатые данные
 * после `SOS` остаются байт-в-байт (их не трогают — убираются только служебные сегменты), для PNG —
 * что распакованное содержимое `IDAT` совпадает с исходным.
 *
 * Фикстуры настоящие: JPEG взяты из книги «Книга Уткина Мы тут жизнь живём» (с EXIF и без метаданных),
 * PNG — реальный снимок экрана устройства из `.dsh/shots/02-polki.png`.
 *
 * Отдельно проверяется случай, ради которого нельзя «просто снять метаданные»: если в EXIF стоит
 * ориентация (фото повёрнуто), снятие EXIF перевернуло бы картинку в читалке — такая картинка
 * остаётся нетронутой.
 */
class Fb2ImageOptimizerTest {

    private val optimizer = Fb2ImageOptimizer()

    @Test
    fun `jpeg metadata is dropped while the compressed picture stays the same`() {
        val source = fixture("image-with-exif.jpg")

        val result = optimizer.optimize(source, "image/jpeg")

        assertThat(result).isNotNull()
        assertThat(result!!.size).isLessThan(source.size)
        assertThat(String(result, Charsets.ISO_8859_1)).doesNotContain("Exif")
        // Пиксели: всё, что идёт после SOS, обязано совпасть байт-в-байт.
        assertThat(result.scanData()).isEqualTo(source.scanData())
    }

    @Test
    fun `jpeg without metadata is left alone`() {
        val source = fixture("image-plain.jpg")

        val result = optimizer.optimize(source, "image/jpeg")

        // Ничего не выиграли — значит и трогать нечего: возвращаем null, а не те же байты.
        assertThat(result).isNull()
    }

    @Test
    fun `jpeg with an exif orientation is not touched`() {
        val source = fixture("image-with-exif.jpg").withOrientation(6)

        val result = optimizer.optimize(source, "image/jpeg")

        // Снятие EXIF перевернуло бы такую картинку в читалке: пусть остаётся как есть.
        assertThat(result).isNull()
    }

    @Test
    fun `png is repacked tighter without changing the picture`() {
        val source = fixture("image-real.png")

        val result = optimizer.optimize(source, "image/png")

        assertThat(result).isNotNull()
        // Распакованное содержимое — это и есть пиксели: оно обязано совпасть.
        assertThat(result!!.inflatedIdat()).isEqualTo(source.inflatedIdat())
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

    private fun ByteArray.scanData(): ByteArray {
        val start = indexOfMarker(SOS)
        return if (start < 0) this else copyOfRange(start, size)
    }

    private fun ByteArray.indexOfMarker(marker: Int): Int {
        var index = 2
        while (index + 1 < size) {
            if (this[index] != 0xFF.toByte()) return -1
            val kind = this[index + 1].toInt() and 0xFF
            if (kind == marker) return index
            val length = ((this[index + 2].toInt() and 0xFF) shl 8) or (this[index + 3].toInt() and 0xFF)
            index += 2 + length
        }
        return -1
    }

    /** Распакованное содержимое всех `IDAT`: сравнение доказывает, что картинка не изменилась. */
    private fun ByteArray.inflatedIdat(): ByteArray {
        var index = 8
        val deflated = ByteArrayOutputStream()
        while (index + 8 <= size) {
            val length = ((this[index].toInt() and 0xFF) shl 24) or
                ((this[index + 1].toInt() and 0xFF) shl 16) or
                ((this[index + 2].toInt() and 0xFF) shl 8) or
                (this[index + 3].toInt() and 0xFF)
            val kind = String(this, index + 4, 4, Charsets.ISO_8859_1)
            if (kind == "IDAT") deflated.write(this, index + 8, length)
            index += 12 + length
        }
        val inflater = Inflater()
        inflater.setInput(deflated.toByteArray())
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (!inflater.finished()) {
            val read = inflater.inflate(buffer)
            if (read <= 0) break
            out.write(buffer, 0, read)
        }
        inflater.end()
        return out.toByteArray()
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

    private companion object {
        const val SOS = 0xDA
    }
}
