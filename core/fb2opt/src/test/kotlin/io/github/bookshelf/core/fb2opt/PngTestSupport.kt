package io.github.bookshelf.core.fb2opt

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Разбор PNG для тестов — **написанный заново по спецификации**, а не вызов проверяемого кода.
 *
 * Смысл в том, чтобы пиксели сверяла вторая реализация: если бы тест снимал фильтры тем же кодом,
 * что и оптимизатор, ошибка в нём осталась бы незамеченной — обе стороны сравнили бы одинаково
 * неверные данные. Правильность самой проверки подтверждает [PngTestSupportTest] на файле,
 * собранном вручную с заранее известными пикселями.
 */
internal object PngTestSupport {

    /** Пиксели картинки в виде `0xAARRGGBB`, построчно: то, что видит человек. */
    fun pixels(bytes: ByteArray): IntArray {
        val header = chunks(bytes).first { (type, _) -> type == "IHDR" }.second
        val width = intAt(header, 0)
        val height = intAt(header, 4)
        val bitDepth = header[8].toInt() and BYTE_MASK
        val colorType = header[9].toInt() and BYTE_MASK
        check(bitDepth == BITS_PER_CHANNEL) { "тест умеет только 8 бит на канал, а тут $bitDepth" }
        val channels = channelsFor(colorType)
        val rowBytes = width * channels
        val raw = idatInflated(bytes)
        val out = IntArray(width * height)
        var previous = IntArray(rowBytes)
        var source = 0
        for (row in 0 until height) {
            val filter = raw[source].toInt() and BYTE_MASK
            val line = reconstructRow(
                raw = raw,
                start = source + 1,
                rowBytes = rowBytes,
                channels = channels,
                filter = filter,
                previous = previous,
            )
            copyRow(out = out, row = row, width = width, channels = channels, line = line)
            previous = line
            source += rowBytes + 1
        }
        return out
    }

    /**
     * Число каналов по типу цвета PNG.
     *
     * Вынесено из `pixels` отдельной функцией: ветки `when` давали порог
     * `CyclomaticComplexMethod`, а таблица каналов читается отдельно от обхода строк.
     */
    private fun channelsFor(colorType: Int): Int = when (colorType) {
        0 -> 1
        2 -> 3
        4 -> 2
        6 -> 4
        else -> error("тест не умеет тип цвета $colorType")
    }

    /**
     * Строка пикселей из сырых байт: снятие фильтра PNG по спецификации.
     *
     * Фильтры читаются независимо от оптимизатора — иначе проверка сравнивала бы данные,
     * снятые одним и тем же (возможно, неверным) кодом.
     */
    private fun reconstructRow(
        raw: ByteArray,
        start: Int,
        rowBytes: Int,
        channels: Int,
        filter: Int,
        previous: IntArray,
    ): IntArray {
        val line = IntArray(rowBytes)
        for (index in 0 until rowBytes) {
            val value = raw[start + index].toInt() and BYTE_MASK
            val left = if (index >= channels) line[index - channels] else 0
            val up = previous[index]
            val upLeft = if (index >= channels) previous[index - channels] else 0
            val predictor = when (filter) {
                0 -> 0
                1 -> left
                2 -> up
                3 -> (left + up) / 2
                4 -> paeth(left, up, upLeft)
                else -> error("неизвестный фильтр строки $filter")
            }
            line[index] = (value + predictor) and BYTE_MASK
        }
        return line
    }

    /** Строка каналов → строки ARGB в общий массив. */
    private fun copyRow(out: IntArray, row: Int, width: Int, channels: Int, line: IntArray) {
        for (x in 0 until width) {
            val at = x * channels
            out[row * width + x] = when (channels) {
                1 -> argb(OPAQUE, line[at], line[at], line[at])
                2 -> argb(line[at + 1], line[at], line[at], line[at])
                3 -> argb(OPAQUE, line[at], line[at + 1], line[at + 2])
                else -> argb(line[at + 3], line[at], line[at + 1], line[at + 2])
            }
        }
    }

    /** Сколько байт занимают сжатые данные `IDAT` в файле. */
    fun idatCompressedLength(bytes: ByteArray): Int = chunks(bytes)
        .filter { (type, _) -> type == "IDAT" }
        .sumOf { (_, data) -> data.size }

    /**
     * Отпечаток пикселей: `SHA-256` от их байтов.
     *
     * Сравнивать сами массивы на настоящем снимке экрана нельзя — это 1,5 млн значений, и при
     * расхождении отчёт теста превратился бы в мегабайты чисел. Отпечаток даёт ту же проверку
     * «пиксели совпадают», а расхождение видно одной строкой.
     */
    fun pixelsDigest(bytes: ByteArray): String {
        val pixels = pixels(bytes)
        val raw = java.nio.ByteBuffer.allocate(pixels.size * Int.SIZE_BYTES)
        pixels.forEach { pixel -> raw.putInt(pixel) }
        return java.util.Base64.getEncoder().encodeToString(
            java.security.MessageDigest.getInstance("SHA-256").digest(raw.array()),
        )
    }

    /** Распакованные данные `IDAT` — строки с фильтрами, как они лежат в файле. */
    fun idatInflated(bytes: ByteArray): ByteArray {
        val deflated = ByteArrayOutputStream()
        chunks(bytes).filter { (type, _) -> type == "IDAT" }.forEach { (_, data) -> deflated.write(data) }
        return inflate(deflated.toByteArray())
    }

    /** Сжать максимальным уровнем — так выглядело бы прежнее поведение оптимизатора. */
    fun deflate(data: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        deflater.setInput(data)
        deflater.finish()
        val out = ByteArrayOutputStream(data.size / 2)
        val buffer = ByteArray(BUFFER)
        while (!deflater.finished()) {
            val written = deflater.deflate(buffer)
            out.write(buffer, 0, written)
        }
        deflater.end()
        return out.toByteArray()
    }

    /** Чанки файла по порядку: тип и содержимое. */
    fun chunks(bytes: ByteArray): List<Pair<String, ByteArray>> {
        val result = mutableListOf<Pair<String, ByteArray>>()
        var index = PNG_SIGNATURE_SIZE
        while (index + CHUNK_HEADER_SIZE <= bytes.size) {
            val length = intAt(bytes, index)
            val type = String(bytes, index + TYPE_OFFSET, INT_SIZE, Charsets.ISO_8859_1)
            result += type to bytes.copyOfRange(index + DATA_OFFSET, index + DATA_OFFSET + length)
            index += CHUNK_HEADER_SIZE + length
        }
        return result
    }

    /**
     * Собрать PNG вручную: сигнатура, `IHDR`, `IDAT`, `IEND`.
     *
     * Нужно проверке самой проверки: у такого файла пиксели известны заранее, и по ним видно,
     * правильно ли тест снимает фильтры.
     */
    fun build(width: Int, height: Int, colorType: Int, raw: ByteArray): ByteArray {
        val header = ByteArrayOutputStream().apply {
            writeInt(width)
            writeInt(height)
            write(BITS_PER_CHANNEL)
            write(colorType)
            write(COMPRESSION)
            write(FILTER_METHOD)
            write(INTERLACE_NONE)
        }.toByteArray()
        val out = ByteArrayOutputStream()
        out.write(PNG_SIGNATURE)
        out.writeChunk("IHDR", header)
        out.writeChunk("IDAT", deflate(raw))
        out.writeChunk("IEND", ByteArray(0))
        return out.toByteArray()
    }

    private fun inflate(data: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(data)
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER)
        while (!inflater.finished()) {
            val read = inflater.inflate(buffer)
            if (read <= 0) break
            out.write(buffer, 0, read)
        }
        inflater.end()
        return out.toByteArray()
    }

    private fun paeth(left: Int, up: Int, upLeft: Int): Int {
        val estimate = left + up - upLeft
        val toLeft = kotlin.math.abs(estimate - left)
        val toUp = kotlin.math.abs(estimate - up)
        val toUpLeft = kotlin.math.abs(estimate - upLeft)
        return when {
            toLeft <= toUp && toLeft <= toUpLeft -> left
            toUp <= toUpLeft -> up
            else -> upLeft
        }
    }

    private fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int =
        (alpha shl ALPHA_SHIFT) or (red shl RED_SHIFT) or (green shl GREEN_SHIFT) or blue

    private fun intAt(bytes: ByteArray, at: Int): Int =
        ((bytes[at].toInt() and BYTE_MASK) shl 24) or
            ((bytes[at + 1].toInt() and BYTE_MASK) shl 16) or
            ((bytes[at + 2].toInt() and BYTE_MASK) shl 8) or
            (bytes[at + 3].toInt() and BYTE_MASK)

    private fun ByteArrayOutputStream.writeChunk(type: String, data: ByteArray) {
        writeInt(data.size)
        val name = type.toByteArray(Charsets.ISO_8859_1)
        write(name)
        write(data)
        val crc = CRC32()
        crc.update(name)
        crc.update(data)
        writeInt(crc.value.toInt())
    }

    private fun ByteArrayOutputStream.writeInt(value: Int) {
        write((value ushr 24) and BYTE_MASK)
        write((value ushr 16) and BYTE_MASK)
        write((value ushr 8) and BYTE_MASK)
        write(value and BYTE_MASK)
    }

    private const val BYTE_MASK = 0xFF
    private const val PNG_SIGNATURE_SIZE = 8
    private const val CHUNK_HEADER_SIZE = 12
    private const val TYPE_OFFSET = 4
    private const val DATA_OFFSET = 8
    private const val INT_SIZE = 4
    private const val BUFFER = 64 * 1024
    private const val BITS_PER_CHANNEL = 8
    private const val COMPRESSION = 0
    private const val FILTER_METHOD = 0
    private const val INTERLACE_NONE = 0
    private const val OPAQUE = 255
    private const val ALPHA_SHIFT = 24
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8

    private val PNG_SIGNATURE = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    )
}
