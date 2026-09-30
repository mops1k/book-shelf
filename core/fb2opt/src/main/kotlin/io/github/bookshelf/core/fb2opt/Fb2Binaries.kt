package io.github.bookshelf.core.fb2opt

/**
 * Блок картинки в разметке FB2: где он лежит и что в нём.
 *
 * @param start позиция тега `<binary`.
 * @param end позиция сразу после `</binary>`.
 * @param attributes атрибуты тега как строка: идентификатор и тип картинки берутся из них.
 * @param id идентификатор, на который ссылаются из тела книги; `null` — атрибута нет.
 * @param contentType тип картинки из `content-type`.
 * @param payloadStart начало base64-содержимого.
 * @param payloadEnd конец base64-содержимого.
 */
internal data class Fb2Binary(
    val start: Int,
    val end: Int,
    val attributes: String,
    val id: String?,
    val contentType: String,
    val payloadStart: Int,
    val payloadEnd: Int,
)

/**
 * Поиск блоков `<binary>` по байтам.
 *
 * Отдельный сканер, а не регулярное выражение: разметку книги нельзя превращать в строку ради
 * поиска картинок — книга в 13 МБ как `String` занимает вдвое больше памяти, а на e-ink-читалке
 * это и был отказ (проверено на устройстве 30.09.2026). Атрибуты блока — ASCII, поэтому строка
 * строится только для них, а не для всей книги.
 */
internal object Fb2Binaries {

    /** Все блоки картинок по порядку следования в файле. */
    fun scan(content: ByteArray): List<Fb2Binary> {
        val found = mutableListOf<Fb2Binary>()
        var index = indexOf(content, OPEN, 0)
        while (index >= 0) {
            // Незакрытый блок означает битый файл: дальше искать нечего, а чинить его не наша задача.
            val binary = parse(content, index) ?: break
            found += binary
            index = indexOf(content, OPEN, binary.end)
        }
        return found
    }

    /** Разобрать блок, начинающийся на позиции [start]; `null` — блок не закрыт. */
    private fun parse(content: ByteArray, start: Int): Fb2Binary? {
        val tagEnd = indexOfByte(content, GT.toByte(), start + OPEN.size)
        if (tagEnd < 0) return null
        val close = indexOf(content, CLOSE, tagEnd)
        if (close < 0) return null
        val attributes = String(content, start + OPEN.size, tagEnd - start - OPEN.size, Charsets.ISO_8859_1)
        return Fb2Binary(
            start = start,
            end = close + CLOSE.size,
            attributes = attributes,
            id = Fb2Patterns.ID.find(attributes)?.groupValues?.get(1),
            contentType = Fb2Patterns.CONTENT_TYPE.find(attributes)?.groupValues?.get(1).orEmpty(),
            payloadStart = tagEnd + 1,
            payloadEnd = close,
        )
    }

    private fun indexOfByte(content: ByteArray, byte: Byte, from: Int): Int {
        var index = from
        while (index < content.size) {
            if (content[index] == byte) return index
            index += 1
        }
        return -1
    }

    private fun indexOf(content: ByteArray, marker: ByteArray, from: Int): Int {
        var index = from
        while (index + marker.size <= content.size) {
            var offset = 0
            while (offset < marker.size && content[index + offset] == marker[offset]) offset += 1
            if (offset == marker.size) return index
            index += 1
        }
        return -1
    }

    private const val GT = '>'
    private val OPEN = "<binary".toByteArray(Charsets.US_ASCII)
    private val CLOSE = "</binary>".toByteArray(Charsets.US_ASCII)
}
