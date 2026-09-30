package io.github.bookshelf.core.fb2opt

import java.io.ByteArrayOutputStream

/**
 * Прочитанные коэффициенты картинки: то, из чего состоит изображение до сжатия.
 *
 * Коэффициенты — единственное, что определяет пиксели. Поэтому пережатие сжатых данных (замена
 * таблиц Хаффмана) на них не влияет: меняется только запись, а не значения.
 *
 * @param blocks блоки 8×8 по компонентам скана, в порядке обхода MCU.
 * @param dcFrequencies сколько раз встречался каждый символ DC — по таблицам.
 * @param acFrequencies то же для AC.
 */
internal class JpegCoefficients(
    val blocks: List<List<ShortArray>>,
    val dcFrequencies: Map<Int, IntArray>,
    val acFrequencies: Map<Int, IntArray>,
) {

    /** Совпадают ли коэффициенты: так проверяется, что пережатие ничего не изменило. */
    fun sameAs(other: JpegCoefficients): Boolean {
        if (blocks.size != other.blocks.size) return false
        return blocks.indices.all { component ->
            val mine = blocks[component]
            val theirs = other.blocks[component]
            mine.size == theirs.size && mine.indices.all { block -> mine[block].contentEquals(theirs[block]) }
        }
    }
}

/** Чтение битов JPEG: старший бит вперёд, `FF00` — это один байт `FF`. */
internal class JpegBitReader(private val data: ByteArray) {

    private var index = 0
    private var current = 0
    private var bitsLeft = 0

    /** Следующий бит; `null` — данные кончились. */
    fun readBit(): Int? {
        if (bitsLeft == 0) {
            if (index >= data.size) return null
            current = data[index++].toInt() and BYTE_MASK
            if (current == MARKER && index < data.size && (data[index].toInt() and BYTE_MASK) == 0) {
                index++
            }
            bitsLeft = BITS_IN_BYTE
        }
        bitsLeft--
        return (current shr bitsLeft) and 1
    }

    /** Прочитать [count] бит как беззнаковое число. */
    fun readBits(count: Int): Int? {
        var value = 0
        repeat(count) {
            val bit = readBit() ?: return null
            value = (value shl 1) or bit
        }
        return value
    }

    /** Выровняться по границе байта: перед маркером рестарта данные дополнены единицами. */
    fun alignToByte() {
        bitsLeft = 0
    }

    /**
     * Пропустить маркер рестарта `FF D0…D7`.
     *
     * Маркеры стоят на границе байта и в битовый поток не входят, поэтому их надо снять отдельно.
     */
    fun skipRestartMarker(): Boolean {
        if (index + 1 >= data.size) return false
        if ((data[index].toInt() and BYTE_MASK) != MARKER) return false
        val kind = data[index + 1].toInt() and BYTE_MASK
        if (kind < RESTART_FIRST || kind > RESTART_LAST) return false
        index += 2
        return true
    }

    private companion object {
        const val BYTE_MASK = 0xFF
        const val MARKER = 0xFF
        const val BITS_IN_BYTE = 8
        const val RESTART_FIRST = 0xD0
        const val RESTART_LAST = 0xD7
    }
}

/** Запись битов JPEG: старший бит вперёд, `FF` экранируется нулём. */
internal class JpegBitWriter {

    private val out = ByteArrayOutputStream()
    private var current = 0
    private var bitsUsed = 0

    /** Записать [count] младших бит значения [value]. */
    fun writeBits(value: Int, count: Int) {
        for (position in count - 1 downTo 0) {
            current = (current shl 1) or ((value shr position) and 1)
            bitsUsed++
            if (bitsUsed == BITS_IN_BYTE) flushByte()
        }
    }

    /**
     * Выровняться по границе байта.
     *
     * Незаполненные биты дополняются единицами: так требует формат, иначе после маркера рестарта
     * декодер прочитал бы лишний нулевой байт как данные.
     */
    fun alignWithOnes() {
        while (bitsUsed != 0) writeBits(1, 1)
    }

    /** Дописать маркер рестарта `FF D0…D7`. */
    fun writeRestartMarker(number: Int) {
        alignWithOnes()
        out.write(MARKER)
        out.write(RESTART_FIRST + (number and RESTART_MASK))
    }

    fun toByteArray(): ByteArray {
        alignWithOnes()
        return out.toByteArray()
    }

    private fun flushByte() {
        out.write(current and BYTE_MASK)
        if ((current and BYTE_MASK) == MARKER) out.write(0)
        current = 0
        bitsUsed = 0
    }

    private companion object {
        const val BYTE_MASK = 0xFF
        const val MARKER = 0xFF
        const val BITS_IN_BYTE = 8
        const val RESTART_FIRST = 0xD0
        const val RESTART_MASK = 0x07
    }
}
