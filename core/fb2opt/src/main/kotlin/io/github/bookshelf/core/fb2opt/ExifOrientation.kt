package io.github.bookshelf.core.fb2opt

/**
 * Чтение тега `Orientation` из блока EXIF (TIFF) картинки.
 *
 * Нужно ровно для одного решения: можно ли снять EXIF. Если ориентация выставлена (фото повёрнуто),
 * снятие EXIF перевернуло бы картинку в читалке, поэтому такая картинка остаётся нетронутой.
 *
 * Реализовано вручную, а не через `androidx.exifinterface`: там чтение требует `InputStream` и
 * разбора всего файла, а нам нужен один тег из уже прочитанных байтов и без зависимости от Android —
 * оптимизатор должен проверяться обычными тестами на JVM.
 */
internal object ExifOrientation {

    /** Значение тега ориентации; `null` — тега нет или структура нечитаемая. */
    fun read(tiff: ByteArray): Int? {
        val littleEndian = endiannessOf(tiff) ?: return null
        val ifdOffset = readInt(tiff, IFD_OFFSET_FIELD, littleEndian)
        if (ifdOffset < TIFF_HEADER || ifdOffset + SHORT_SIZE > tiff.size) return null
        val entries = readShort(tiff, ifdOffset, littleEndian) ?: return null
        // Первая же запись с тегом ориентации даёт ответ: дальше искать нечего.
        return (0 until entries).firstNotNullOfOrNull { entry ->
            orientationOf(tiff, ifdOffset, entry, littleEndian)
        }
    }

    /** Значение ориентации из записи [entry] IFD0; `null` — запись не про ориентацию. */
    private fun orientationOf(
        tiff: ByteArray,
        ifdOffset: Int,
        entry: Int,
        littleEndian: Boolean,
    ): Int? {
        val at = ifdOffset + SHORT_SIZE + entry * ENTRY_SIZE
        if (at + ENTRY_SIZE > tiff.size) return null
        if (readShort(tiff, at + TAG_FIELD, littleEndian) != ORIENTATION_TAG) return null
        // Ориентация — одно короткое число, и оно лежит прямо в записи, а не по ссылке.
        val type = readShort(tiff, at + TYPE_FIELD, littleEndian)
        val count = readInt(tiff, at + COUNT_FIELD, littleEndian)
        return if (type == SHORT_TYPE && count == SINGLE_VALUE) {
            readShort(tiff, at + VALUE_FIELD, littleEndian)
        } else {
            null
        }
    }

    /** Порядок байт TIFF: `II` — от младшего, `MM` — от старшего; иначе структура не наша. */
    private fun endiannessOf(tiff: ByteArray): Boolean? = when {
        tiff.size < TIFF_HEADER -> null
        tiff[0] == MARKER_LITTLE && tiff[1] == MARKER_LITTLE -> true
        tiff[0] == MARKER_BIG && tiff[1] == MARKER_BIG -> false
        else -> null
    }

    private fun readShort(bytes: ByteArray, at: Int, littleEndian: Boolean): Int? {
        if (at + SHORT_SIZE > bytes.size) return null
        val first = bytes[at].toInt() and BYTE_MASK
        val second = bytes[at + 1].toInt() and BYTE_MASK
        return if (littleEndian) {
            (second shl SECOND_BYTE_SHIFT) or first
        } else {
            (first shl SECOND_BYTE_SHIFT) or second
        }
    }

    private fun readInt(bytes: ByteArray, at: Int, littleEndian: Boolean): Int {
        if (at + INT_SIZE > bytes.size) return MISSING_VALUE
        val b0 = bytes[at].toInt() and BYTE_MASK
        val b1 = bytes[at + SECOND_BYTE].toInt() and BYTE_MASK
        val b2 = bytes[at + THIRD_BYTE].toInt() and BYTE_MASK
        val b3 = bytes[at + FOURTH_BYTE].toInt() and BYTE_MASK
        return if (littleEndian) {
            (b3 shl FOURTH_BYTE_SHIFT) or (b2 shl THIRD_BYTE_SHIFT) or
                (b1 shl SECOND_BYTE_SHIFT) or b0
        } else {
            (b0 shl FOURTH_BYTE_SHIFT) or (b1 shl THIRD_BYTE_SHIFT) or
                (b2 shl SECOND_BYTE_SHIFT) or b3
        }
    }

    private const val BYTE_MASK = 0xFF
    private const val SECOND_BYTE = 1
    private const val THIRD_BYTE = 2
    private const val FOURTH_BYTE = 3

    /** Значение, когда поле выходит за границы блока: «прочитать нечего». */
    private const val MISSING_VALUE = 0
    private const val SINGLE_VALUE = 1
    private const val SECOND_BYTE_SHIFT = 8
    private const val THIRD_BYTE_SHIFT = 16
    private const val FOURTH_BYTE_SHIFT = 24
    private const val TIFF_HEADER = 8
    private const val IFD_OFFSET_FIELD = 4
    private const val SHORT_SIZE = 2
    private const val INT_SIZE = 4
    private const val ENTRY_SIZE = 12
    private const val TAG_FIELD = 0
    private const val TYPE_FIELD = 2
    private const val COUNT_FIELD = 4
    private const val VALUE_FIELD = 8
    private const val ORIENTATION_TAG = 0x0112
    private const val SHORT_TYPE = 3

    private const val MARKER_LITTLE: Byte = 0x49
    private const val MARKER_BIG: Byte = 0x4D
}
