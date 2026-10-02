package io.github.bookshelf.core.fb2opt

import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Оптимизация картинок внутри FB2 **без потери данных**.
 *
 * Что делается и почему это не портит картинку:
 * * **JPEG** — убираются только служебные сегменты (`COM` и `APP1`/Exif), а сжатые данные после
 *   `SOS` пережимаются таблицами Хаффмана, посчитанными по самой картинке. Коэффициенты
 *   дискретного косинусного преобразования при этом те же — пиксели не меняются;
 * * **PNG** — пересобирается `IDAT` ([PngImageOptimizer]): строки заново фильтруются и сжимаются,
 *   а результат проверяется сравнением самих пикселей.
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
    private val png: PngImageOptimizer = PngImageOptimizer(),
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

        PNG_TYPE -> png.optimize(bytes)
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
            // Начало сжатых данных: дальше только они и маркер конца — копируем без разбора.
            if (marker == SOS || marker == EOI) {
                out.write(bytes, index, bytes.size - index)
                return if (dropped && out.size() < bytes.size) out.toByteArray() else null
            }
            val end = segmentEnd(bytes, index) ?: return null
            // Метаданные (комментарий и EXIF) — единственное, что мы снимаем. Ориентация в EXIF —
            // исключение: снятие перевернуло бы картинку, поэтому тогда оптимизация не делается.
            when (bytes.metadataDecision(marker = marker, start = index, end = end)) {
                MetadataDecision.ABORT -> return null
                MetadataDecision.DROP -> dropped = true
                MetadataDecision.KEEP -> out.write(bytes, index, end - index)
            }
            index = end
        }
        return null
    }

    /** Что делать с сегментом JPEG: снять метаданные, скопировать как есть или отказаться. */
    private enum class MetadataDecision { DROP, KEEP, ABORT }

    /**
     * Решение по сегменту: комментарий и EXIF снимаются, ориентация в EXIF — повод отказаться.
     *
     * Вынесено из `optimizeJpeg` отдельной функцией: условия в цикле давали порог
     * `CyclomaticComplexMethod`, а само решение читается как три случая.
     */
    private fun ByteArray.metadataDecision(
        marker: Int,
        start: Int,
        end: Int,
    ): MetadataDecision = when {
        marker != COM && marker != APP1 -> MetadataDecision.KEEP
        marker == APP1 && hasOrientation(start, end) -> MetadataDecision.ABORT
        else -> MetadataDecision.DROP
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

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (size < prefix.size) return false
        for (index in prefix.indices) if (this[index] != prefix[index]) return false
        return true
    }

    private companion object {
        const val BYTE_MASK = 0xFF
        const val SECOND_BYTE_SHIFT = 8

        /** В сегменте JPEG длина записана двумя байтами сразу после маркера. */
        const val SEGMENT_LENGTH_FIELD = 2

        const val ORIENTATION_NORMAL = 1

        const val JPEG_TYPE = "image/jpeg"
        const val PNG_TYPE = "image/png"

        const val MARKER_PREFIX: Byte = 0xFF.toByte()
        const val SOS = 0xDA
        const val EOI = 0xD9
        const val COM = 0xFE
        const val APP1 = 0xE1

        const val TIFF_HEADER_SIZE = 8

        /** Маркер JPEG занимает два байта: `FF` и код. */
        const val MARKER_LENGTH = 2

        val SOI = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
        const val EXIF_HEADER = "Exif\u0000\u0000"
    }
}
