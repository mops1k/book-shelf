package io.github.bookshelf.core.fb2opt

import java.security.MessageDigest

/**
 * Дедупликация одинаковых картинок внутри книги.
 *
 * Зачем это нужно: конвертеры нередко кладут одну и ту же картинку несколькими блоками `<binary>`
 * под разными идентификаторами — повторённый орнамент главы, дубль обложки. Содержимое одинаково,
 * и хранить его дважды незачем.
 *
 * Как это делается без потери данных:
 * 1. у каждой картинки считается отпечаток (`SHA-256`) её base64-содержимого — переводы строк
 *    внутри base64 ничего не значат и в отпечаток не входят;
 * 2. из одинаковых остаётся первая по порядку файла, остальные блоки выбрасываются;
 * 3. ссылки на выброшенные идентификаторы (`l:href="#id"`) переводятся на оставшийся.
 *
 * Отпечатки, а не сами картинки: держать в памяти декодированные изображения всей книги — это
 * десятки мегабайт, а на e-ink-читалке с потолком кучи 256 МБ именно так и теряется процесс.
 *
 * Что не трогается:
 * * блок без `id` — ссылаться на него нечем, значит и склеивать нечего;
 * * ссылки внутри `CDATA` — там лежит текст, а не разметка, и переписывать его нельзя;
 * * разные картинки — сравнение идёт по содержимому, а не по типу или размеру.
 *
 * Сравнение идёт по **исходному** содержимому, а не по пересобранному: одинаковые картинки в книге
 * почти всегда одинаковы уже в файле, а так выброшенные копии не приходится ещё и пересобирать.
 */
internal class Fb2BinaryDeduplicator {

    /**
     * Идентификаторы, которые надо заменить на оставшуюся копию: «дубликат → оставленный».
     *
     * Пустая карта — либо дубликатов нет, либо у блоков нет идентификаторов.
     */
    fun duplicateIds(content: ByteArray, binaries: List<Fb2Binary>): Map<String, String> {
        if (binaries.size < 2) return emptyMap()
        val keptByDigest = HashMap<String, String>()
        val redirect = HashMap<String, String>()
        for (binary in binaries) {
            val id = binary.id ?: continue
            val kept = keptByDigest.putIfAbsent(digestOf(content, binary), id)
            // Строка `kept != id` важна: одинаковый id дважды — это уже неверная книга, и молча
            // выбрасывать один из блоков значило бы чинить чужую ошибку за её автора.
            if (kept != null && kept != id) redirect[id] = kept
        }
        return redirect
    }

    /**
     * Переписать ссылки на выброшенные копии в куске разметки.
     *
     * Кусок перекодируется в `ISO-8859-1` и обратно: это отображение байт в символ без потерь,
     * поэтому кириллица в тексте остаётся байт-в-байт, а меняются только ASCII-ссылки. Внутри
     * `CDATA` текст копируется как есть.
     */
    fun rewriteReferences(markup: ByteArray, redirect: Map<String, String>): ByteArray {
        if (redirect.isEmpty() || markup.isEmpty()) return markup
        val text = String(markup, Charsets.ISO_8859_1)
        val out = StringBuilder(text.length)
        var index = 0
        while (index < text.length) {
            val cdata = text.indexOf(CDATA_OPEN, index)
            if (cdata < 0) {
                out.append(replaceReferences(text.substring(index), redirect))
                break
            }
            out.append(replaceReferences(text.substring(index, cdata), redirect))
            val end = text.indexOf(CDATA_CLOSE, cdata)
            val stop = if (end < 0) text.length else end + CDATA_CLOSE.length
            out.append(text, cdata, stop)
            index = stop
        }
        return out.toString().toByteArray(Charsets.ISO_8859_1)
    }

    /**
     * Отпечаток содержимого блока.
     *
     * Считается по base64 без пробельных байтов: в книгах одну и ту же картинку переносят по-разному
     * (`Base64` с переносами и без), и сравнивать сырые строки значило бы не найти дубликат.
     */
    private fun digestOf(content: ByteArray, binary: Fb2Binary): String {
        val digest = MessageDigest.getInstance(ALGORITHM)
        var runStart = binary.payloadStart
        for (index in binary.payloadStart until binary.payloadEnd) {
            if (isWhitespace(content[index])) {
                if (index > runStart) digest.update(content, runStart, index - runStart)
                runStart = index + 1
            }
        }
        if (binary.payloadEnd > runStart) {
            digest.update(content, runStart, binary.payloadEnd - runStart)
        }
        val bytes = digest.digest()
        val hex = StringBuilder(bytes.size * HEX_DIGITS_PER_BYTE)
        for (byte in bytes) {
            val value = byte.toInt() and BYTE_MASK
            hex.append(HEX[value ushr HEX_SHIFT]).append(HEX[value and HEX_MASK])
        }
        return hex.toString()
    }

    private fun replaceReferences(text: String, redirect: Map<String, String>): String =
        REFERENCE.replace(text) { match ->
            val kept = redirect[match.groupValues[REFERENCE_ID]]
            if (kept == null) {
                match.value
            } else {
                "${match.groupValues[REFERENCE_PREFIX]}#$kept${match.groupValues[REFERENCE_SUFFIX]}"
            }
        }

    private companion object {
        const val ALGORITHM = "SHA-256"
        const val BYTE_MASK = 0xFF
        const val HEX_SHIFT = 4
        const val HEX_MASK = 0x0F
        const val HEX_DIGITS_PER_BYTE = 2

        const val CDATA_OPEN = "<![CDATA["
        const val CDATA_CLOSE = "]]>"

        val REFERENCE = Regex("""(href\s*=\s*["'])#([^"']+)(["'])""")

        /** Номера групп в [REFERENCE]: начало атрибута, идентификатор и закрывающая кавычка. */
        const val REFERENCE_PREFIX = 1
        const val REFERENCE_ID = 2
        const val REFERENCE_SUFFIX = 3

        val HEX = "0123456789abcdef".toCharArray()

        fun isWhitespace(byte: Byte): Boolean =
            byte == ' '.code.toByte() || byte == '\t'.code.toByte() ||
                byte == '\n'.code.toByte() || byte == '\r'.code.toByte()
    }
}
