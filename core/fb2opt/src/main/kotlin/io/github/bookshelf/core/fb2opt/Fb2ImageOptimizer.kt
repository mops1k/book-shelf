package io.github.bookshelf.core.fb2opt

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Оптимизация картинок внутри FB2 **без потери данных**.
 *
 * Что делается и почему это не портит картинку:
 * * **JPEG** — убираются только служебные сегменты (`COM` и `APP1`/Exif). Сжатые данные после `SOS`
 *   копируются байт-в-байт, то есть пиксели те же. Метаданные (дата съёмки, GPS) при этом теряются —
 *   это и есть смысл шага;
 * * **PNG** — содержимое `IDAT` распаковывается и сжимается плотнее (уровень 9). Распакованные
 *   байты — это и есть пиксели, они совпадают до последнего байта.
 *
 * Чего НЕ делаем осознанно:
 * * не снимаем EXIF, если в нём есть **ориентация**: убрав её, мы перевернули бы картинку в читалке;
 * * не трогаем `APP2`/ICC и `APP14`/Adobe: первый задаёт цветовой профиль, второй — преобразование
 *   цвета, и их потеря изменила бы то, что человек видит, хотя пиксели формально те же;
 * * не меняем формат (PNG ↔ JPEG) и не пережимаем с потерями.
 *
 * Возвращается `null`, если выигрыша нет: «не стало меньше» — это не повод переписывать файл.
 */
@Singleton
class Fb2ImageOptimizer @Inject constructor(
    private val entropy: JpegEntropyOptimizer,
) {

    /**
     * Оптимизировать картинку [bytes] с типом [contentType]; `null` — оставить как есть.
     *
     * Для JPEG делаются два независимых шага, и берётся лучший результат: снятие служебных
     * сегментов и пережатие сжатых данных оптимизированными таблицами Хаффмана. Оба не меняют
     * пиксели, и вместе они дают больше, чем каждый по отдельности.
     */
    fun optimize(bytes: ByteArray, contentType: String): ByteArray? = when (contentType.lowercase()) {
        JPEG_TYPE -> bestOf(
            source = bytes,
            candidates = listOfNotNull(
                optimizeJpeg(bytes),
                entropy.optimize(bytes),
                // Оба шага вместе: снятие метаданных уменьшает файл, после чего таблицы Хаффмана
                // считаются по тем же данным и дают ещё немного.
                optimizeJpeg(bytes)?.let { stripped -> entropy.optimize(stripped) },
            ),
        )

        PNG_TYPE -> optimizePng(bytes)
        else -> null
    }

    /** Самый маленький из вариантов, если он строго меньше исходного; иначе — не трогать. */
    private fun bestOf(source: ByteArray, candidates: List<ByteArray>): ByteArray? =
        candidates.filter { it.size < source.size }.minByOrNull { it.size }

    private fun optimizeJpeg(bytes: ByteArray): ByteArray? {
        if (bytes.size < SOI.size + 2 || !bytes.startsWith(SOI)) return null
        val out = ByteArrayOutputStream(bytes.size)
        out.write(bytes, 0, SOI.size)
        var index = SOI.size
        var dropped = false
        while (index + 1 < bytes.size) {
            if (bytes[index] != MARKER_PREFIX) return null
            val marker = bytes[index + 1].toInt() and BYTE_MASK
            when {
                // Начало сжатых данных: дальше только они и маркер конца — копируем без разбора.
                marker == SOS || marker == EOI -> {
                    out.write(bytes, index, bytes.size - index)
                    return if (dropped && out.size() < bytes.size) out.toByteArray() else null
                }

                marker == COM || marker == APP1 -> {
                    val end = segmentEnd(bytes, index) ?: return null
                    if (marker == APP1 && bytes.hasOrientation(index, end)) {
                        // Ориентация в EXIF: снятие перевернуло бы картинку — не трогаем вовсе.
                        return null
                    }
                    dropped = true
                    index = end
                }

                else -> {
                    val end = segmentEnd(bytes, index) ?: return null
                    out.write(bytes, index, end - index)
                    index = end
                }
            }
        }
        return null
    }

    private fun optimizePng(bytes: ByteArray): ByteArray? {
        if (bytes.size < PNG_SIGNATURE.size || !bytes.startsWith(PNG_SIGNATURE)) return null
        val chunks = mutableListOf<PngChunk>()
        val deflated = ByteArrayOutputStream()
        var index = PNG_SIGNATURE.size
        while (index + CHUNK_HEADER_SIZE <= bytes.size) {
            val length = readInt(bytes, index)
            if (length < 0 || index + CHUNK_HEADER_SIZE + length > bytes.size) return null
            val type = String(bytes, index + PNG_TYPE_OFFSET, INT_SIZE, Charsets.ISO_8859_1)
            val data = bytes.copyOfRange(index + PNG_DATA_OFFSET, index + PNG_DATA_OFFSET + length)
            if (type == IDAT) deflated.write(data) else chunks += PngChunk(type, data)
            index += CHUNK_HEADER_SIZE + length
        }
        if (deflated.size() == 0) return null
        val raw = inflate(deflated.toByteArray()) ?: return null
        val repacked = deflate(raw)
        val out = ByteArrayOutputStream(bytes.size)
        out.write(PNG_SIGNATURE)
        var idatWritten = false
        for (chunk in chunks) {
            if (chunk.type == IEND && !idatWritten) {
                writeChunks(out, repacked)
                idatWritten = true
            }
            out.writeChunk(chunk)
        }
        if (!idatWritten) writeChunks(out, repacked)
        val result = out.toByteArray()
        // Проверка перед выдачей: распакованное содержимое обязано совпасть — это и есть пиксели.
        return if (result.size < bytes.size && result.inflatedIdatEquals(raw)) result else null
    }

    private fun ByteArray.inflatedIdatEquals(expected: ByteArray): Boolean {
        val deflated = ByteArrayOutputStream()
        var index = PNG_SIGNATURE.size
        while (index + CHUNK_HEADER_SIZE <= size) {
            val length = readInt(this, index)
            if (length < 0 || index + CHUNK_HEADER_SIZE + length > size) return false
            if (String(this, index + PNG_TYPE_OFFSET, INT_SIZE, Charsets.ISO_8859_1) == IDAT) {
                deflated.write(this, index + PNG_DATA_OFFSET, length)
            }
            index += CHUNK_HEADER_SIZE + length
        }
        val actual = inflate(deflated.toByteArray()) ?: return false
        return actual.contentEquals(expected)
    }

    /** Конец сегмента JPEG: заголовок с длиной плюс сама длина. */
    private fun segmentEnd(bytes: ByteArray, start: Int): Int? {
        if (start + SEGMENT_LENGTH_FIELD + 1 >= bytes.size) return null
        val high = bytes[start + SEGMENT_LENGTH_FIELD].toInt() and BYTE_MASK
        val low = bytes[start + SEGMENT_LENGTH_FIELD + 1].toInt() and BYTE_MASK
        val length = (high shl SECOND_BYTE_SHIFT) or low
        val end = start + SEGMENT_LENGTH_FIELD + length
        return if (length < SEGMENT_LENGTH_FIELD || end > bytes.size) null else end
    }

    /**
     * Есть ли в сегменте `APP1` ориентация, отличная от обычной.
     *
     * Ориентация `1` и её отсутствие означают «картинку не поворачивать», и тогда EXIF можно снять.
     */
    private fun ByteArray.hasOrientation(start: Int, end: Int): Boolean {
        // После маркера и поля длины сразу идёт строка "Exif\0\0", а за ней — блок TIFF.
        val exifHeader = start + MARKER_LENGTH + SEGMENT_LENGTH_FIELD
        val tiff = exifHeader + EXIF_HEADER.length
        if (end - tiff < TIFF_HEADER_SIZE) return false
        if (String(this, exifHeader, EXIF_HEADER.length, Charsets.ISO_8859_1) != EXIF_HEADER) {
            return false
        }
        val orientation = ExifOrientation.read(copyOfRange(tiff, end)) ?: return false
        return orientation != ORIENTATION_NORMAL
    }

    private fun readInt(bytes: ByteArray, at: Int): Int =
        ((bytes[at].toInt() and BYTE_MASK) shl FOURTH_BYTE_SHIFT) or
            ((bytes[at + SECOND_BYTE].toInt() and BYTE_MASK) shl THIRD_BYTE_SHIFT) or
            ((bytes[at + THIRD_BYTE].toInt() and BYTE_MASK) shl SECOND_BYTE_SHIFT) or
            (bytes[at + FOURTH_BYTE].toInt() and BYTE_MASK)

    private fun inflate(data: ByteArray): ByteArray? = runCatching {
        val inflater = Inflater()
        inflater.setInput(data)
        val out = ByteArrayOutputStream(data.size * INFLATED_SIZE_FACTOR)
        val buffer = ByteArray(INFLATE_BUFFER)
        while (!inflater.finished()) {
            val read = inflater.inflate(buffer)
            if (read <= 0) break
            out.write(buffer, 0, read)
        }
        inflater.end()
        out.toByteArray()
    }.getOrNull()?.takeIf { it.isNotEmpty() }

    private fun deflate(raw: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        deflater.setInput(raw)
        deflater.finish()
        val out = ByteArrayOutputStream(raw.size / 2)
        val buffer = ByteArray(INFLATE_BUFFER)
        while (!deflater.finished()) {
            val written = deflater.deflate(buffer)
            out.write(buffer, 0, written)
        }
        deflater.end()
        return out.toByteArray()
    }

    private fun writeChunks(out: ByteArrayOutputStream, data: ByteArray) {
        var offset = 0
        while (offset < data.size) {
            val size = minOf(IDAT_CHUNK_SIZE, data.size - offset)
            out.writeChunk(PngChunk(IDAT, data.copyOfRange(offset, offset + size)))
            offset += size
        }
    }

    private fun ByteArrayOutputStream.writeChunk(chunk: PngChunk) {
        writeInt(chunk.data.size)
        write(chunk.type.toByteArray(Charsets.ISO_8859_1))
        write(chunk.data)
        // CRC пересчитывается по содержимому: чужие значения после переупаковки уже неверны.
        write(crc32(chunk.type.toByteArray(Charsets.ISO_8859_1), chunk.data))
    }

    private fun crc32(type: ByteArray, data: ByteArray): ByteArray {
        val crc = java.util.zip.CRC32()
        crc.update(type)
        crc.update(data)
        val out = ByteArrayOutputStream(INT_SIZE)
        out.writeInt(crc.value.toInt())
        return out.toByteArray()
    }

    /** Записать число четырьмя байтами от старшего: так устроены длина чанка и CRC в PNG. */
    private fun ByteArrayOutputStream.writeInt(value: Int) {
        write((value ushr FOURTH_BYTE_SHIFT) and BYTE_MASK)
        write((value ushr THIRD_BYTE_SHIFT) and BYTE_MASK)
        write((value ushr SECOND_BYTE_SHIFT) and BYTE_MASK)
        write(value and BYTE_MASK)
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (size < prefix.size) return false
        for (index in prefix.indices) if (this[index] != prefix[index]) return false
        return true
    }

    private data class PngChunk(val type: String, val data: ByteArray)

    private companion object {
        const val BYTE_MASK = 0xFF
        const val SECOND_BYTE = 1
        const val THIRD_BYTE = 2
        const val FOURTH_BYTE = 3
        const val SECOND_BYTE_SHIFT = 8
        const val THIRD_BYTE_SHIFT = 16
        const val FOURTH_BYTE_SHIFT = 24

        /** В сегменте JPEG длина записана двумя байтами сразу после маркера. */
        const val SEGMENT_LENGTH_FIELD = 2

        /** В чанке PNG: длина (4 байта) + тип (4 байта) перед содержимым. */
        const val PNG_TYPE_OFFSET = 4
        const val PNG_DATA_OFFSET = 8

        /** Размер полей в чанке и в записи EXIF. */
        const val INT_SIZE = 4
        const val ORIENTATION_NORMAL = 1

        /** Оценка запаса при распаковке: сжатое изображение обычно вчетверо меньше исходного. */
        const val INFLATED_SIZE_FACTOR = 4

        const val JPEG_TYPE = "image/jpeg"
        const val PNG_TYPE = "image/png"
        const val IDAT = "IDAT"
        const val IEND = "IEND"

        const val MARKER_PREFIX: Byte = 0xFF.toByte()
        const val SOS = 0xDA
        const val EOI = 0xD9
        const val COM = 0xFE
        const val APP1 = 0xE1

        const val CHUNK_HEADER_SIZE = 12
        const val IDAT_CHUNK_SIZE = 8192
        const val INFLATE_BUFFER = 64 * 1024
        const val TIFF_HEADER_SIZE = 8

        /** Маркер JPEG занимает два байта: `FF` и код. */
        const val MARKER_LENGTH = 2

        val SOI = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
        val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        )
        const val EXIF_HEADER = "Exif\u0000\u0000"
    }
}
