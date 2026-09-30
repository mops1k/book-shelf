package io.github.bookshelf.core.fb2opt

import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Пережатие JPEG без потери качества: коэффициенты те же, таблицы Хаффмана — посчитанные по картинке.
 *
 * Что происходит: картинка разбирается, её сжатые данные читаются в коэффициенты (это и есть пиксели
 * до сжатия), по фактическим символам строятся новые таблицы Хаффмана, и коэффициенты записываются
 * заново — кодами короче. Значения не меняются, поэтому и картинка не меняется.
 *
 * Почему это важно именно для книг: таблицы Хаффмана в них обычно «стандартные», рассчитанные на
 * среднюю фотографию, а не на конкретную. Замена их на посчитанные уменьшает файл без потерь —
 * на электронной книге с малым запасом памяти каждый байт имеет смысл.
 *
 * Безопасность: после пережатия результат **разбирается заново и сверяется по коэффициентам**; если
 * хоть что-то не совпало или файл не стал меньше — возвращается `null`, и картинка остаётся прежней.
 */
@Singleton
class JpegEntropyOptimizer @Inject constructor() {

    /** Пережать [jpeg]; `null` — формат не поддерживается, что-то не сошлось или выигрыша нет. */
    fun optimize(jpeg: ByteArray): ByteArray? {
        val structure = JpegParser.parse(jpeg) ?: return null
        val transcoder = JpegEntropyTranscoder(structure)
        val coefficients = transcoder.decode() ?: return null
        val tables = optimizedTables(structure, coefficients) ?: return null
        val entropy = transcoder.encode(coefficients, tables) ?: return null
        val rebuilt = rebuild(structure, tables, entropy) ?: return null
        if (!isFaithful(rebuilt, coefficients)) return null
        return rebuilt.takeIf { it.size < jpeg.size }
    }

    /** Новые таблицы для каждой таблицы файла: DC и AC считаются отдельно. */
    private fun optimizedTables(
        structure: JpegStructure,
        coefficients: JpegCoefficients,
    ): Map<Int, JpegHuffmanTable>? {
        val tables = mutableMapOf<Int, JpegHuffmanTable>()
        for ((id, frequencies) in coefficients.dcFrequencies) {
            tables[key(JpegEntropyTranscoder.DC_CLASS, id)] =
                HuffmanOptimizer.build(frequencies) ?: return null
        }
        for ((id, frequencies) in coefficients.acFrequencies) {
            tables[key(JpegEntropyTranscoder.AC_CLASS, id)] =
                HuffmanOptimizer.build(frequencies) ?: return null
        }
        // Таблицы, которые в скане не использовались, переносим как были: иначе заголовок разъедется.
        for ((key, table) in structure.tables) {
            tables.putIfAbsent(key, table)
        }
        return tables.takeIf { it.isNotEmpty() }
    }

    /** Собрать файл: заголовки как были, таблицы Хаффмана новые, данные — пережатые. */
    private fun rebuild(
        structure: JpegStructure,
        tables: Map<Int, JpegHuffmanTable>,
        entropy: ByteArray,
    ): ByteArray? {
        val out = ByteArrayOutputStream(entropy.size + HEADER_RESERVE)
        out.write(JpegParser.SOI)
        structure.head.forEach { segment -> out.write(segment) }
        tables.entries.sortedBy { entry -> entry.key }.forEach { entry ->
            out.write(huffmanSegment(entry.key, entry.value))
        }
        out.write(structure.scan)
        out.write(entropy)
        out.write(JpegParser.EOI_BYTES)
        return out.toByteArray()
    }

    /** Сегмент `DHT` с таблицей: класс и номер, длины кодов, затем символы. */
    private fun huffmanSegment(key: Int, table: JpegHuffmanTable): ByteArray {
        val payload = ByteArrayOutputStream()
        payload.write(key and BYTE_MASK)
        table.bits.forEach { count -> payload.write(count) }
        table.values.forEach { symbol -> payload.write(symbol) }
        val body = payload.toByteArray()
        val length = body.size + SEGMENT_LENGTH_BYTES
        return byteArrayOf(
            MARKER_PREFIX,
            DHT.toByte(),
            ((length shr SECOND_BYTE_SHIFT) and BYTE_MASK).toByte(),
            (length and BYTE_MASK).toByte(),
        ) + body
    }

    /** Разобрать результат заново и сверить коэффициенты: своя же проверка после своей же записи. */
    private fun isFaithful(rebuilt: ByteArray, expected: JpegCoefficients): Boolean {
        val parsed = JpegParser.parse(rebuilt) ?: return false
        val actual = JpegEntropyTranscoder(parsed).decode() ?: return false
        return actual.sameAs(expected)
    }

    private fun key(huffmanClass: Int, id: Int): Int = (huffmanClass shl TABLE_ID_BITS) or id

    private companion object {
        const val TABLE_ID_BITS = 4
        const val HEADER_RESERVE = 1024
        const val SEGMENT_LENGTH_BYTES = 2
        const val SECOND_BYTE_SHIFT = 8
        const val BYTE_MASK = 0xFF
        const val MARKER_PREFIX: Byte = 0xFF.toByte()
        const val DHT = 0xC4
    }
}
