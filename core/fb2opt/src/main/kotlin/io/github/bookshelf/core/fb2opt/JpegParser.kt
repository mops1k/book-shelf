package io.github.bookshelf.core.fb2opt

/**
 * Разбор JPEG на части, нужные для пережатия сжатых данных.
 *
 * Поддерживается только **обычный последовательный** JPEG (`SOF0`/`SOF1`): именно такие картинки
 * лежат в книгах (проверено на 120 картинках из трёх книг). Прогрессивный (`SOF2`) и всё остальное
 * не разбирается — оптимизатор вернёт «не трогать», а не испортит картинку.
 */
internal object JpegParser {

    /** Разобрать [jpeg]; `null` — формат не поддерживается или файл битый. */
    fun parse(jpeg: ByteArray): JpegStructure? = runCatching { parseOrThrow(jpeg) }.getOrNull()

    /** Временная диагностика: причина, по которой разбор не прошёл. */
    internal fun parseWithReason(jpeg: ByteArray): String = runCatching { parseOrThrow(jpeg) }
        .fold(onSuccess = { "разобран" }, onFailure = { it.message ?: it.toString() })

    private fun parseOrThrow(jpeg: ByteArray): JpegStructure {
        if (jpeg.size < SOI.size + EOI_BYTES.size || !jpeg.startsWith(SOI)) {
            throw JpegFormatException("не JPEG")
        }
        val head = mutableListOf<ByteArray>()
        val tables = mutableMapOf<Int, JpegHuffmanTable>()
        var frame: JpegFrame? = null
        var scan: ByteArray? = null
        var restartInterval = 0
        var index = SOI.size
        var reachedScan = false
        while (!reachedScan && index + 1 < jpeg.size) {
            if (jpeg[index] != MARKER_PREFIX) throw JpegFormatException("ожидался маркер")
            val marker = jpeg[index + 1].toInt() and BYTE_MASK
            when (marker) {
                EOI -> reachedScan = true
                SOS -> {
                    val end = segmentEnd(jpeg, index)
                    scan = jpeg.copyOfRange(index, end)
                    index = end
                    reachedScan = true
                }

                DHT -> {
                    val end = segmentEnd(jpeg, index)
                    readHuffmanTables(jpeg, index + PAYLOAD, end, tables)
                    index = end
                }

                DRI -> {
                    val end = segmentEnd(jpeg, index)
                    restartInterval = readShort(jpeg, index + PAYLOAD)
                    index = end
                }

                SOF0, SOF1 -> {
                    val end = segmentEnd(jpeg, index)
                    frame = readFrame(jpeg, index + PAYLOAD, end)
                    head += jpeg.copyOfRange(index, end)
                    index = end
                }

                else -> {
                    val end = segmentEnd(jpeg, index)
                    head += jpeg.copyOfRange(index, end)
                    index = end
                }
            }
        }
        val actualFrame = frame ?: throw JpegFormatException("нет кадра")
        val actualScan = scan ?: throw JpegFormatException("нет сканирования")
        val entropyEnd = indexOfEoi(jpeg, index)
        val entropy = jpeg.copyOfRange(index, entropyEnd)
        return JpegStructure(
            head = head,
            scan = actualScan,
            entropy = entropy,
            frame = actualFrame,
            tables = tables,
            restartInterval = restartInterval,
        )
    }

    /** Компоненты сканирования: их порядок и таблицы — то, чем кодируются данные. */
    fun scanComponents(scan: ByteArray): List<JpegScanComponent> {
        val count = scan[PAYLOAD].toInt() and BYTE_MASK
        var at = PAYLOAD + 1
        return (0 until count).map {
            val id = scan[at].toInt() and BYTE_MASK
            val tableByte = scan[at + 1].toInt() and BYTE_MASK
            at += COMPONENT_ENTRY
            JpegScanComponent(id = id, dcTable = tableByte shr 4, acTable = tableByte and 0x0F)
        }
    }

    private fun readFrame(jpeg: ByteArray, from: Int, to: Int): JpegFrame {
        if (to - from < FRAME_FIXED) throw JpegFormatException("кадр обрезан")
        val precision = jpeg[from].toInt() and BYTE_MASK
        if (precision != BYTE_PRECISION) throw JpegFormatException("поддерживается только 8 бит")
        val height = readShort(jpeg, from + HEIGHT_FIELD)
        val width = readShort(jpeg, from + WIDTH_FIELD)
        val count = jpeg[from + COMPONENT_COUNT_FIELD].toInt() and BYTE_MASK
        val components = (0 until count).map { position ->
            val at = from + FRAME_FIXED + position * FRAME_COMPONENT
            JpegComponent(
                id = jpeg[at].toInt() and BYTE_MASK,
                horizontal = (jpeg[at + 1].toInt() and BYTE_MASK) shr 4,
                vertical = jpeg[at + 1].toInt() and 0x0F,
                quantTable = jpeg[at + 2].toInt() and BYTE_MASK,
            )
        }
        return JpegFrame(width = width, height = height, components = components)
    }

    private fun readHuffmanTables(
        jpeg: ByteArray,
        from: Int,
        to: Int,
        into: MutableMap<Int, JpegHuffmanTable>,
    ) {
        var at = from
        while (at < to) {
            val classAndId = jpeg[at].toInt() and BYTE_MASK
            val bits = IntArray(JpegHuffmanTable.MAX_CODE_LENGTH)
            var symbols = 0
            for (length in bits.indices) {
                bits[length] = jpeg[at + 1 + length].toInt() and BYTE_MASK
                symbols += bits[length]
            }
            val valuesAt = at + 1 + JpegHuffmanTable.MAX_CODE_LENGTH
            val values = IntArray(symbols) { offset -> jpeg[valuesAt + offset].toInt() and BYTE_MASK }
            into[classAndId] = JpegHuffmanTable(bits, values)
            at = valuesAt + symbols
        }
    }

    /** Конец сжатых данных: маркер `EOI` (он не экранирован, потому что после него данных нет). */
    private fun indexOfEoi(jpeg: ByteArray, from: Int): Int {
        var index = from
        while (index + 1 < jpeg.size) {
            if (jpeg[index] == MARKER_PREFIX && (jpeg[index + 1].toInt() and BYTE_MASK) == EOI) return index
            index += if (jpeg[index] == MARKER_PREFIX && (jpeg[index + 1].toInt() and BYTE_MASK) == 0) {
                // Экранированный FF00 — часть данных, через него перешагиваем.
                2
            } else {
                1
            }
        }
        return jpeg.size
    }

    private fun segmentEnd(jpeg: ByteArray, start: Int): Int {
        if (start + SEGMENT_HEADER + 1 >= jpeg.size) throw JpegFormatException("сегмент обрезан")
        val length = readShort(jpeg, start + 2)
        val end = start + SEGMENT_HEADER + length
        if (length < SEGMENT_HEADER || end > jpeg.size) throw JpegFormatException("длина сегмента")
        return end
    }

    private fun readShort(bytes: ByteArray, at: Int): Int {
        if (at + 2 > bytes.size) throw JpegFormatException("нет двух байт")
        return ((bytes[at].toInt() and BYTE_MASK) shl SECOND_BYTE_SHIFT) or
            (bytes[at + SECOND_BYTE].toInt() and BYTE_MASK)
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    private const val BYTE_MASK = 0xFF
    private const val MARKER_PREFIX: Byte = 0xFF.toByte()
    private const val SOF0 = 0xC0
    private const val SOF1 = 0xC1
    private const val DHT = 0xC4
    private const val SOS = 0xDA
    private const val DRI = 0xDD
    private const val EOI = 0xD9
    /** Маркер (2 байта) и длина (2 байта): с этого места начинается содержимое сегмента. */
    private const val PAYLOAD = 4
    private const val SEGMENT_HEADER = 2
    private const val SECOND_BYTE = 1
    private const val SECOND_BYTE_SHIFT = 8

    /** Поля кадра: высота, ширина и число компонентов идут подряд после точности. */
    private const val HEIGHT_FIELD = 1
    private const val WIDTH_FIELD = 3
    private const val COMPONENT_COUNT_FIELD = 5
    private const val BYTE_PRECISION = 8
    private const val FRAME_FIXED = 6
    private const val FRAME_COMPONENT = 3
    private const val COMPONENT_ENTRY = 2

    val SOI = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
    val EOI_BYTES = byteArrayOf(0xFF.toByte(), 0xD9.toByte())
}
