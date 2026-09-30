package io.github.bookshelf.core.fb2opt

import java.io.ByteArrayOutputStream

/**
 * Минификация разметки FB2 без потери данных — **по байтам**.
 *
 * Что сжимается:
 * 1. комментарии `<!-- … -->` вне `CDATA` — в книгах это пометки конвертеров, читателю они не нужны;
 * 2. отступы и переводы строк между тегами — они не текст, а форматирование файла;
 * 3. пробелы внутри тегов (между именем и атрибутами) — до одного.
 *
 * Что НЕ трогается и почему:
 * * текст между тегами: пробел между двумя строчными тегами значим (`<p>Одно <emphasis>слово</emphasis>`),
 *   и его удаление склеило бы слова;
 * * `CDATA` — байт-в-байт: там может лежать разметка или код как текст;
 * * содержимое `<binary>` — картинки в base64, их обрабатывает отдельный шаг.
 *
 * **Почему байты, а не строка.** Книга в 13 МБ как `String` занимает вдвое больше — UTF-16, — и
 * прежняя реализация держала в памяти сразу три таких копии (исходную, минифицированную и
 * пересобранную). На e-ink-читалке с потолком кучи 256 МБ это заканчивалось тем, что систему
 * убивала процесс (проверено на устройстве 30.09.2026). Байтовый проход пишет результат сразу в
 * буфер и не создаёт ни одной полной копии текста.
 *
 * **Почему диапазон, а не весь файл.** Картинки лежат в конце книги и занимают почти весь объём,
 * поэтому разметка сжимается по частям — между блоками `<binary>`. Чтобы результат не отличался
 * от сжатия целиком, края куска описываются байтами соседей ([previousByte], [nextByte]): пробел
 * на стыке кусков обрабатывается так же, как внутри одного.
 */
class Fb2XmlMinifier {

    /**
     * Сжать разметку в диапазоне `[from, to)`.
     *
     * @param previousByte байт, стоящий перед `from` (`-1` — начало документа).
     * @param nextByte байт, стоящий на позиции `to` (`-1` — конец документа).
     */
    fun minify(
        source: ByteArray,
        from: Int = 0,
        to: Int = source.size,
        previousByte: Int = NO_BYTE,
        nextByte: Int = NO_BYTE,
    ): ByteArray {
        if (from >= to) return EMPTY
        val out = ByteArrayOutputStream(to - from)
        var last = previousByte

        fun writeByte(value: Int) {
            out.write(value)
            last = value
        }

        fun writeRange(start: Int, end: Int) {
            if (end <= start) return
            out.write(source, start, end - start)
            last = source[end - 1].toInt() and BYTE_MASK
        }

        /** Скопировать тег, сжав пробелы внутри него до одного. */
        fun copyTag(start: Int): Int {
            val end = findTagEnd(source, start, to)
            if (end < 0) {
                writeRange(start, to)
                return to
            }
            writeByte(LT)
            var index = start + 1
            var pendingSpace = false
            while (index < end) {
                val byte = source[index].toInt() and BYTE_MASK
                if (isWhitespace(byte)) {
                    pendingSpace = true
                } else {
                    if (pendingSpace && last != LT) writeByte(SPACE)
                    pendingSpace = false
                    writeByte(byte)
                }
                index += 1
            }
            writeByte(GT)
            return end + 1
        }

        var index = from
        while (index < to) {
            val byte = source[index].toInt() and BYTE_MASK
            when {
                // Комментарий: выбрасываем целиком. Незакрытый — оставляем как есть: битый файл
                // чинить не наша задача, а потерять данные из-за него нельзя.
                startsWith(source, index, to, COMMENT_OPEN) -> {
                    val end = indexOf(source, index, to, COMMENT_CLOSE)
                    if (end < 0) {
                        writeRange(index, to)
                        index = to
                    } else {
                        index = end + COMMENT_CLOSE.size
                    }
                }

                // CDATA: байт-в-байт.
                startsWith(source, index, to, CDATA_OPEN) -> {
                    val end = indexOf(source, index, to, CDATA_CLOSE)
                    if (end < 0) {
                        writeRange(index, to)
                        index = to
                    } else {
                        writeRange(index, end + CDATA_CLOSE.size)
                        index = end + CDATA_CLOSE.size
                    }
                }

                byte == LT -> index = copyTag(index)

                isWhitespace(byte) -> {
                    val end = skipWhitespace(source, index, to)
                    // Отступ МЕЖДУ тегами (`>` … `<`) — форматирование файла, его убираем. Но если
                    // хотя бы с одной стороны текст, пробел значим: `<p>Одно <emphasis>слово`
                    // без него склеилось бы в «Однослово».
                    val before = last
                    val after = if (end < to) source[end].toInt() and BYTE_MASK else nextByte
                    val atEdge = before == NO_BYTE || after == NO_BYTE
                    if (!atEdge && !(before == GT && after == LT)) writeByte(SPACE)
                    index = end
                }

                else -> {
                    writeByte(byte)
                    index += 1
                }
            }
        }
        return out.toByteArray()
    }

    /**
     * Конец тега.
     *
     * Кавычки учитываются: в значении атрибута может встретиться `>`, и обрывать тег по нему нельзя.
     */
    private fun findTagEnd(source: ByteArray, start: Int, to: Int): Int {
        var index = start + 1
        var quote = NO_BYTE
        while (index < to) {
            val byte = source[index].toInt() and BYTE_MASK
            when {
                quote != NO_BYTE -> if (byte == quote) quote = NO_BYTE
                byte == QUOTE || byte == APOSTROPHE -> quote = byte
                byte == GT -> return index
            }
            index += 1
        }
        return -1
    }

    private fun skipWhitespace(source: ByteArray, start: Int, to: Int): Int {
        var index = start
        while (index < to && isWhitespace(source[index].toInt() and BYTE_MASK)) index += 1
        return index
    }

    private fun startsWith(source: ByteArray, index: Int, to: Int, marker: ByteArray): Boolean {
        if (index + marker.size > to) return false
        for (offset in marker.indices) if (source[index + offset] != marker[offset]) return false
        return true
    }

    private fun indexOf(source: ByteArray, from: Int, to: Int, marker: ByteArray): Int {
        var index = from
        while (index + marker.size <= to) {
            if (startsWith(source, index, to, marker)) return index
            index += 1
        }
        return -1
    }

    private companion object {
        const val BYTE_MASK = 0xFF

        /** «Байта нет»: начало и конец документа. */
        const val NO_BYTE = -1

        const val LT = '<'.code
        const val GT = '>'.code
        const val SPACE = ' '.code
        const val QUOTE = '"'.code
        const val APOSTROPHE = '\''.code

        /** Пробельные байты XML: пробел, табуляция, перевод строки, возврат каретки. */
        fun isWhitespace(byte: Int): Boolean =
            byte == ' '.code || byte == '\t'.code || byte == '\n'.code || byte == '\r'.code

        val EMPTY = ByteArray(0)
        val COMMENT_OPEN = "<!--".toByteArray(Charsets.US_ASCII)
        val COMMENT_CLOSE = "-->".toByteArray(Charsets.US_ASCII)
        val CDATA_OPEN = "<![CDATA[".toByteArray(Charsets.US_ASCII)
        val CDATA_CLOSE = "]]>".toByteArray(Charsets.US_ASCII)
    }
}
