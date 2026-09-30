package io.github.bookshelf.core.fb2opt

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.Inflater
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Пересборка PNG **без потери пикселей**.
 *
 * Что делается:
 * 1. `IDAT` распаковывается, строки снимаются с фильтров — получаются сами пиксели;
 * 2. строки заново фильтруются несколькими способами (ничем, `Sub`, `Up`, `Average`, `Paeth` и
 *    построчно «как выгоднее») и каждый вариант сжимается отдельно;
 * 3. берётся наименьший сжатый вариант, из него собирается файл;
 * 4. результат проверяется обратным ходом: распаковка → снятие фильтров → сравнение с исходными
 *    пикселями. Не совпало — файл не отдаётся вовсе.
 *
 * Зачем перебирать фильтры: они не меняют картинку, но сильно меняют сжатие. На настоящем снимке
 * экрана из книги (1072×1448) вариант «все строки без фильтра» дал 72 165 байт против 83 103 у
 * прежнего поведения «оставить фильтры как в файле» — экономия 13 % только на выборе фильтров.
 *
 * Что НЕ трогается:
 * * чанки кроме `IDAT` — цветовой профиль и прочие служебные данные остаются как были;
 * * чересстрочные PNG (`Adam7`): строки там идут по проходам, и перебор фильтров требует разбора
 *   проходов. Такой файл просто пересобирается с прежними фильтрами;
 * * битовые глубины меньше 8 (палитровые картинки) обрабатываются: байт на пиксель для фильтров
 *   берётся равным единице, как требует формат.
 *
 * Возвращается `null`, если выигрыша нет: «не стало меньше» — не повод переписывать файл.
 */
@Singleton
class PngImageOptimizer @Inject constructor() {

    /** Пересобрать PNG плотнее; `null` — формат не наш, файл битый или выигрыша нет. */
    fun optimize(bytes: ByteArray): ByteArray? {
        val image = PngImage.parse(bytes) ?: return null
        val layout = image.layout
        val raw = inflate(image.compressedIdat, layout?.rawSize) ?: return null
        val pixels = if (layout == null) null else unfilter(raw, layout)
        val compressed = if (layout != null && pixels != null) {
            bestCompressed(raw, pixels, layout)
        } else {
            deflate(raw)
        }
        val result = image.rebuilt(compressed)
        if (result.size >= bytes.size) return null
        // Проверка перед выдачей: распакованное содержимое обязано совпасть — это и есть пиксели.
        val check = PngImage.parse(result)?.compressedIdat?.let { inflate(it, layout?.rawSize) }
            ?: return null
        val same = if (layout == null) {
            check.contentEquals(raw)
        } else {
            pixels!!.contentEquals(unfilter(check, layout))
        }
        return if (same) result else null
    }

    /**
     * Наименьший сжатый вариант из перебранных фильтров.
     *
     * Вариант «как в файле» участвует наравне с остальными: он гарантирует, что новый файл не будет
     * хуже прежнего поведения, даже если перебор ничего не нашёл.
     */
    private fun bestCompressed(raw: ByteArray, pixels: ByteArray, layout: PngLayout): ByteArray {
        var best = deflate(raw)
        for (filter in FIXED_FILTERS) {
            val candidate = deflate(filtered(pixels, layout, filter))
            if (candidate.size < best.size) best = candidate
        }
        val adaptive = deflate(adaptiveFiltered(pixels, layout))
        return if (adaptive.size < best.size) adaptive else best
    }

    /** Строки со снятыми фильтрами — это и есть пиксели. `null` — данные не сходятся с заголовком. */
    private fun unfilter(raw: ByteArray, layout: PngLayout): ByteArray? {
        if (raw.size != layout.rawSize) return null
        val out = ByteArray(layout.pixelBytes)
        val previous = ByteArray(layout.rowBytes)
        var source = 0
        var target = 0
        repeat(layout.height) {
            val filter = raw[source].toInt() and BYTE_MASK
            if (filter > PAETH) return null
            val rowStart = source + 1
            for (index in 0 until layout.rowBytes) {
                val value = raw[rowStart + index].toInt() and BYTE_MASK
                val left = byteAt(out, target, index - layout.bytesPerPixel)
                val up = previous[index].toInt() and BYTE_MASK
                val upLeft = byteAt(previous, 0, index - layout.bytesPerPixel)
                out[target + index] = ((value + predictor(filter, left, up, upLeft)) and BYTE_MASK).toByte()
            }
            System.arraycopy(out, target, previous, 0, layout.rowBytes)
            source += layout.rowBytes + 1
            target += layout.rowBytes
        }
        return out
    }

    /** Закодировать все строки одним и тем же фильтром. */
    private fun filtered(pixels: ByteArray, layout: PngLayout, filter: Int): ByteArray {
        val out = ByteArray(layout.rawSize)
        val previous = ByteArray(layout.rowBytes)
        var source = 0
        var target = 0
        repeat(layout.height) {
            out[target] = filter.toByte()
            for (index in 0 until layout.rowBytes) {
                val value = pixels[source + index].toInt() and BYTE_MASK
                val left = byteAt(pixels, source, index - layout.bytesPerPixel)
                val up = previous[index].toInt() and BYTE_MASK
                val upLeft = byteAt(previous, 0, index - layout.bytesPerPixel)
                out[target + 1 + index] =
                    ((value - predictor(filter, left, up, upLeft)) and BYTE_MASK).toByte()
            }
            System.arraycopy(pixels, source, previous, 0, layout.rowBytes)
            source += layout.rowBytes
            target += layout.rowBytes + 1
        }
        return out
    }

    /**
     * Фильтр выбирается для каждой строки отдельно.
     *
     * Признак — сумма модулей отфильтрованных байтов: чем меньше «размах» значений, тем лучше они
     * сжимаются. Это тот же эвристический приём, которым пользуются PNG-оптимизаторы: он не требует
     * сжимать каждую строку всеми способами.
     */
    private fun adaptiveFiltered(pixels: ByteArray, layout: PngLayout): ByteArray {
        val out = ByteArray(layout.rawSize)
        val previous = ByteArray(layout.rowBytes)
        var source = 0
        var target = 0
        repeat(layout.height) {
            var bestFilter = NONE
            var bestScore = Long.MAX_VALUE
            var bestRow: ByteArray? = null
            for (filter in FIXED_FILTERS) {
                val candidate = ByteArray(layout.rowBytes)
                var score = 0L
                for (index in 0 until layout.rowBytes) {
                    val value = pixels[source + index].toInt() and BYTE_MASK
                    val left = byteAt(pixels, source, index - layout.bytesPerPixel)
                    val up = previous[index].toInt() and BYTE_MASK
                    val upLeft = byteAt(previous, 0, index - layout.bytesPerPixel)
                    val filtered = (value - predictor(filter, left, up, upLeft)) and BYTE_MASK
                    candidate[index] = filtered.toByte()
                    // Байт со знаком: 200 и −56 — одно и то же отклонение, и «размах» у них общий.
                    score += if (filtered < SIGN_BIT) filtered else BYTE_MASK - filtered
                }
                if (score < bestScore) {
                    bestScore = score
                    bestFilter = filter
                    bestRow = candidate
                }
            }
            out[target] = bestFilter.toByte()
            bestRow?.let { chosen -> System.arraycopy(chosen, 0, out, target + 1, layout.rowBytes) }
            System.arraycopy(pixels, source, previous, 0, layout.rowBytes)
            source += layout.rowBytes
            target += layout.rowBytes + 1
        }
        return out
    }

    /** Предсказание по фильтру PNG: то, что вычитается из значения пикселя. */
    private fun predictor(filter: Int, left: Int, up: Int, upLeft: Int): Int = when (filter) {
        NONE -> 0
        SUB -> left
        UP -> up
        AVERAGE -> (left + up) / 2
        else -> paeth(left, up, upLeft)
    }

    /** Предсказание Пейта: ближайший из трёх соседей по градиенту. */
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

    /**
     * Байт по смещению внутри строки.
     *
     * Смещение считается **от начала строки**: для первых пикселей строки «слева» и «слева сверху»
     * не существует, и там обязан быть ноль. Обращение по абсолютному индексу вернуло бы байт
     * предыдущей строки, и картинка поехала бы — причём незаметно, потому что снятие фильтров
     * и наложение фильтров ошиблись бы одинаково и сошлись бы друг с другом.
     */
    private fun byteAt(bytes: ByteArray, rowStart: Int, index: Int): Int =
        if (index < 0) 0 else bytes[rowStart + index].toInt() and BYTE_MASK

    private fun deflate(raw: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        deflater.setInput(raw)
        deflater.finish()
        val out = ByteArrayOutputStream(raw.size / 2)
        val buffer = ByteArray(BUFFER)
        while (!deflater.finished()) {
            val written = deflater.deflate(buffer)
            out.write(buffer, 0, written)
        }
        deflater.end()
        return out.toByteArray()
    }

    /**
     * Распаковать `IDAT`.
     *
     * @param limit ожидаемый размер; задан — распаковка останавливается на нём, и другое число
     *   байт считается ошибкой. Это и проверка формата, и защита от «PNG-бомбы»: распакованный
     *   файл бывает в сотни раз больше сжатого, и читать его целиком нельзя.
     */
    private fun inflate(data: ByteArray, limit: Int?): ByteArray? {
        val inflater = Inflater()
        return try {
            inflater.setInput(data)
            val out = ByteArrayOutputStream(initialCapacity(data.size, limit))
            val buffer = ByteArray(BUFFER)
            var written = 0
            var failed = false
            while (!inflater.finished() && !failed) {
                val read = inflater.inflate(buffer)
                failed = when {
                    // Ноль байт: либо поток распакован до конца, либо данных меньше, чем обещал заголовок.
                    read <= 0 -> !inflater.finished()
                    // Больше обещанного: это «PNG-бомба» либо заголовок врёт.
                    limit != null && written + read > limit -> true
                    else -> {
                        written += read
                        out.write(buffer, 0, read)
                        false
                    }
                }
            }
            val result = out.toByteArray()
            if (failed || !inflater.finished()) null
            else if (limit != null && result.size != limit) null
            else result.takeIf { it.isNotEmpty() }
        } catch (_: java.util.zip.DataFormatException) {
            // Сжатые данные испорчены: чинить их нечем, файл остаётся как есть.
            null
        } finally {
            inflater.end()
        }
    }

    /**
     * Начальный размер буфера распаковки.
     *
     * Заголовок PNG — это данные из файла, и верить им нельзя: без потолка «бомба» с полем размера
     * в гигабайт заставила бы сразу выделить гигабайт памяти. Буфер всё равно растёт по мере
     * надобности, поэтому потолок ничего не ломает.
     */
    private fun initialCapacity(dataSize: Int, limit: Int?): Int {
        val estimated = limit ?: (dataSize * INFLATED_SIZE_FACTOR)
        return estimated.coerceIn(MIN_CAPACITY, MAX_INITIAL_CAPACITY)
    }

    /** Разобранный PNG: чанки без `IDAT`, собранные `IDAT` и геометрия строк. */
    private class PngImage(
        private val chunks: List<PngChunk>,
        val compressedIdat: ByteArray,
        val layout: PngLayout?,
    ) {

        /** Собрать файл заново с новыми сжатыми данными. */
        fun rebuilt(compressed: ByteArray): ByteArray {
            val out = ByteArrayOutputStream(compressed.size + chunks.size * CHUNK_HEADER_SIZE)
            out.write(PNG_SIGNATURE)
            var written = false
            for (chunk in chunks) {
                if (chunk.type == IEND && !written) {
                    writeIdat(out, compressed)
                    written = true
                }
                out.writeChunk(chunk)
            }
            if (!written) writeIdat(out, compressed)
            return out.toByteArray()
        }

        private fun writeIdat(out: ByteArrayOutputStream, data: ByteArray) {
            var offset = 0
            while (offset < data.size) {
                val size = minOf(IDAT_CHUNK_SIZE, data.size - offset)
                out.writeChunk(PngChunk(IDAT, data.copyOfRange(offset, offset + size)))
                offset += size
            }
        }

        companion object {

            /** Разобрать файл; `null` — это не PNG или чанки испорчены. */
            fun parse(bytes: ByteArray): PngImage? {
                if (bytes.size < PNG_SIGNATURE.size || !bytes.startsWith(PNG_SIGNATURE)) return null
                val chunks = mutableListOf<PngChunk>()
                val idat = ByteArrayOutputStream()
                var layout: PngLayout? = null
                var index = PNG_SIGNATURE.size
                while (index + CHUNK_HEADER_SIZE <= bytes.size) {
                    val length = pngIntAt(bytes, index)
                    if (length < 0 || index + CHUNK_HEADER_SIZE + length > bytes.size) return null
                    val type = String(bytes, index + TYPE_OFFSET, INT_SIZE, Charsets.ISO_8859_1)
                    val data = bytes.copyOfRange(index + DATA_OFFSET, index + DATA_OFFSET + length)
                    if (type == IDAT) {
                        idat.write(data)
                    } else {
                        chunks += PngChunk(type, data)
                        if (type == IHDR) layout = PngLayout.of(data)
                    }
                    index += CHUNK_HEADER_SIZE + length
                }
                if (idat.size() == 0) return null
                return PngImage(chunks, idat.toByteArray(), layout)
            }

            private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
                if (size < prefix.size) return false
                for (position in prefix.indices) if (this[position] != prefix[position]) return false
                return true
            }
        }
    }

    /** Геометрия строк PNG: без неё нельзя ни снять фильтры, ни поставить их заново. */
    private class PngLayout(
        val rowBytes: Int,
        val height: Int,
        val bytesPerPixel: Int,
    ) {

        /** Размер распакованного `IDAT`: строка фильтра плюс сами байты строки. */
        val rawSize: Int get() = (rowBytes + 1) * height

        /** Размер самих пикселей без строк фильтров. */
        val pixelBytes: Int get() = rowBytes * height

        companion object {

            /** Разобрать `IHDR`; `null` — чересстрочный файл или неизвестный тип цвета. */
            fun of(ihdr: ByteArray): PngLayout? {
                if (ihdr.size < IHDR_SIZE) return null
                val width = pngIntAt(ihdr, IHDR_WIDTH_OFFSET)
                val height = pngIntAt(ihdr, IHDR_HEIGHT_OFFSET)
                val bitDepth = ihdr[IHDR_BIT_DEPTH_OFFSET].toInt() and BYTE_MASK
                val colorType = ihdr[IHDR_COLOR_TYPE_OFFSET].toInt() and BYTE_MASK
                val interlace = ihdr[IHDR_INTERLACE_OFFSET].toInt() and BYTE_MASK
                if (interlace != INTERLACE_NONE) return null
                if (width <= 0 || height <= 0 || bitDepth <= 0) return null
                val channels = CHANNELS[colorType] ?: return null
                val bitsPerPixel = channels * bitDepth
                val rowBytes = (width.toLong() * bitsPerPixel + BITS_IN_BYTE - 1) / BITS_IN_BYTE
                if (rowBytes <= 0) return null
                // Размеры держатся в Int: иначе распаковка и массивы теряют смысл.
                if ((rowBytes + 1) * height > Int.MAX_VALUE) return null
                // Байт на пиксель для фильтров: у палитровых картинок (глубина меньше 8) это 1.
                val bytesPerPixel = maxOf(1, bitsPerPixel / BITS_IN_BYTE.toInt())
                return PngLayout(rowBytes.toInt(), height, bytesPerPixel)
            }

            /** Число каналов по типу цвета PNG. */
            private val CHANNELS = mapOf(0 to 1, 2 to 3, 3 to 1, 4 to 2, 6 to 4)
        }
    }

    private companion object {
        const val BITS_IN_BYTE = 8L

        /** Номера фильтров строк PNG: 0 — без фильтра, 4 — предсказание Пейта. */
        const val NONE = 0
        const val SUB = 1
        const val UP = 2
        const val AVERAGE = 3
        const val PAETH = 4
        val FIXED_FILTERS = intArrayOf(NONE, SUB, UP, AVERAGE, PAETH)

        const val INT_SIZE = 4
        const val TYPE_OFFSET = 4
        const val DATA_OFFSET = 8
        const val CHUNK_HEADER_SIZE = 12
        const val IDAT_CHUNK_SIZE = 8192
        const val BUFFER = 64 * 1024
        const val INFLATED_SIZE_FACTOR = 4
        const val IHDR_SIZE = 13
        const val INTERLACE_NONE = 0
        const val MIN_CAPACITY = 1024
        const val MAX_INITIAL_CAPACITY = 8 * 1024 * 1024

        const val IDAT = "IDAT"
        const val IEND = "IEND"
        const val IHDR = "IHDR"

        val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        )
    }
}

/** Чанк PNG: тип и содержимое. CRC считается при записи. */
private data class PngChunk(val type: String, val data: ByteArray)

/** Записать чанк PNG: длина, тип, содержимое, CRC по типу и содержимому. */
private fun ByteArrayOutputStream.writeChunk(chunk: PngChunk) {
    writeInt(chunk.data.size)
    val type = chunk.type.toByteArray(Charsets.ISO_8859_1)
    write(type)
    write(chunk.data)
    val crc = CRC32()
    crc.update(type)
    crc.update(chunk.data)
    writeInt(crc.value.toInt())
}

/** Записать число четырьмя байтами от старшего: так устроены длина чанка и CRC в PNG. */
private fun ByteArrayOutputStream.writeInt(value: Int) {
    write((value ushr FOURTH_SHIFT) and BYTE_MASK)
    write((value ushr THIRD_SHIFT) and BYTE_MASK)
    write((value ushr SECOND_SHIFT) and BYTE_MASK)
    write(value and BYTE_MASK)
}

/** Прочитать четыре байта от старшего: так записаны длина чанка, размеры картинки и её CRC. */
private fun pngIntAt(bytes: ByteArray, at: Int): Int =
    ((bytes[at].toInt() and BYTE_MASK) shl FOURTH_SHIFT) or
        ((bytes[at + SECOND_BYTE].toInt() and BYTE_MASK) shl THIRD_SHIFT) or
        ((bytes[at + THIRD_BYTE].toInt() and BYTE_MASK) shl SECOND_SHIFT) or
        (bytes[at + FOURTH_BYTE].toInt() and BYTE_MASK)

private const val BYTE_MASK = 0xFF
private const val SIGN_BIT = 0x80

/** Номера байтов в четырёхбайтовом поле и сдвиги при сборке числа из них. */
private const val SECOND_BYTE = 1
private const val THIRD_BYTE = 2
private const val FOURTH_BYTE = 3
private const val SECOND_SHIFT = 8
private const val THIRD_SHIFT = 16
private const val FOURTH_SHIFT = 24

/** Поля `IHDR`: ширина, высота, глубина цвета, тип цвета, признак чересстрочности. */
private const val IHDR_WIDTH_OFFSET = 0
private const val IHDR_HEIGHT_OFFSET = 4
private const val IHDR_BIT_DEPTH_OFFSET = 8
private const val IHDR_COLOR_TYPE_OFFSET = 9
private const val IHDR_INTERLACE_OFFSET = 12
