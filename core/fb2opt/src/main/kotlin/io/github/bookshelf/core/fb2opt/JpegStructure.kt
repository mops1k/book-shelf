package io.github.bookshelf.core.fb2opt

/**
 * Разобранная структура JPEG, достаточная для пережатия сжатых данных.
 *
 * Хранятся не значения, а **куски файла как есть**: пересобрать картинку нужно ровно с теми же
 * заголовками, квантованием и кадром, что и были, — иначе пиксели изменятся. Меняются только
 * таблицы Хаффмана и сжатые данные.
 *
 * @param head всё до таблиц Хаффмана включительно, кроме самих таблиц: `SOI`, `APPn`, `COM`, `DQT`, кадр.
 * @param scan заголовок сканирования (`SOS`) с маркером и длиной, как в файле.
 * @param entropy сжатые данные между `SOS` и `EOI`.
 * @param frame параметры кадра: размеры, компоненты, их прореживание и таблицы квантования.
 * @param tables таблицы Хаффмана файла; ключ — класс (0 — DC, 1 — AC) и номер таблицы.
 * @param restartInterval интервал рестарта (`DRI`); 0 — маркеров рестарта нет.
 */
internal data class JpegStructure(
    val head: List<ByteArray>,
    val scan: ByteArray,
    val entropy: ByteArray,
    val frame: JpegFrame,
    val tables: Map<Int, JpegHuffmanTable>,
    val restartInterval: Int,
)

/** Параметры кадра JPEG. */
internal data class JpegFrame(
    val width: Int,
    val height: Int,
    val components: List<JpegComponent>,
) {
    /** Максимальное прореживание по горизонтали: столько блоков в MCU по ширине. */
    val maxHorizontal: Int get() = components.maxOf { it.horizontal }

    /** Максимальное прореживание по вертикали. */
    val maxVertical: Int get() = components.maxOf { it.vertical }

    /** Блоков в одном MCU: сумма произведений прореживаний компонентов. */
    val blocksPerMcu: Int get() = components.sumOf { it.horizontal * it.vertical }
}

/** Компонент кадра: как прорежен и какой таблицей квантуется. */
internal data class JpegComponent(
    val id: Int,
    val horizontal: Int,
    val vertical: Int,
    val quantTable: Int,
)

/** Компонент в скане: какой таблицей Хаффмана кодируется его DC и AC. */
internal data class JpegScanComponent(
    /** Идентификатор компонента из кадра (в скане он же `Cs`). */
    val id: Int,
    val dcTable: Int,
    val acTable: Int,
)

/**
 * Таблица Хаффмана JPEG: длины кодов и символы, плюс готовые коды для чтения и записи.
 *
 * Формат тот же, что в файле: `bits[i]` — сколько кодов длины `i + 1`, `values` — символы в порядке
 * возрастания кодов. Коды канонические: это требование формата, а не наш выбор.
 */
internal class JpegHuffmanTable(
    val bits: IntArray,
    val values: IntArray,
) {
    private val codes = IntArray(MAX_SYMBOLS)
    private val lengths = IntArray(MAX_SYMBOLS)
    private val symbolsByCode = mutableMapOf<Long, Int>()

    init {
        var code = 0
        var index = 0
        for (length in bits.indices) {
            repeat(bits[length]) {
                val symbol = values[index++]
                codes[symbol] = code
                lengths[symbol] = length + 1
                symbolsByCode[(length + 1).toLong() shl BITS_IN_INT or code.toLong()] = symbol
                code++
            }
            code = code shl 1
        }
    }

    /** Длина кода символа; 0 — символа в таблице нет. */
    fun lengthOf(symbol: Int): Int = lengths[symbol]

    /** Код символа (без ведущих нулей; пишется ровно [lengthOf] битами). */
    fun codeOf(symbol: Int): Int = codes[symbol]

    /** Прочитать следующий символ из потока бит; `null` — данных не хватило. */
    fun decode(reader: JpegBitReader): Int? {
        var code = 0
        for (length in 1..MAX_CODE_LENGTH) {
            val bit = reader.readBit() ?: return null
            code = (code shl 1) or bit
            symbolsByCode[length.toLong() shl BITS_IN_INT or code.toLong()]?.let { return it }
        }
        return null
    }

    companion object {
        const val MAX_SYMBOLS = 256
        const val MAX_CODE_LENGTH = 16
        private const val BITS_IN_INT = 32
    }
}

/** Ошибка разбора: файл не тот, за который себя выдаёт. */
internal class JpegFormatException(message: String) : Exception(message)
