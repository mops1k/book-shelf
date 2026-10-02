package io.github.bookshelf.core.metadata.encoding

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Определение кодировки текста FB2/TXT.
 *
 * Порядок: BOM → явно объявленная кодировка → XML-декларация → «валидный UTF-8?» →
 * windows-1251 для кириллицы («Архитектура» §4.4, «Форматы» §3).
 * ICU4J не подключаем: ~15 МБ до R8 ради одной задачи неоправданно.
 */
interface CharsetDetector {

    /**
     * @param prefix первые байты файла (для BOM и XML-пролога достаточно 256 байт и меньше);
     * @param declared кодировка, объявленная провайдером/HTTP; `null`, если её нет.
     * @return каноническое имя Java-кодировки (`UTF-8`, `windows-1251`, `KOI8-R`, …).
     */
    fun detect(prefix: ByteArray, declared: String?): String
}

/**
 * Эвристический детектор без внешних зависимостей.
 *
 * `juniversalchardet` подключается начиная со Sprint 3; здесь только детерминированные правила,
 * которые можно проверить обычным JUnit без устройства.
 */
class DefaultCharsetDetector : CharsetDetector {

    override fun detect(prefix: ByteArray, declared: String?): String {
        detectBom(prefix)?.let { return it }
        val fromDeclaration = declared?.takeIf { it.isNotBlank() } ?: declaredInXmlPrologue(prefix)
        return when {
            fromDeclaration != null -> canonicalize(fromDeclaration)
            isValidUtf8(prefix) -> CHARSET_UTF8
            looksLikeCyrillicSingleByte(prefix) -> CHARSET_WINDOWS_1251
            else -> CHARSET_UTF8
        }
    }

    /** BOM — самый надёжный признак: он есть в файле, а не в чужом заголовке. */
    private fun detectBom(prefix: ByteArray): String? = when {
        prefix.startsWith(BOM_UTF8) -> CHARSET_UTF8
        prefix.startsWith(BOM_UTF16_LE) -> CHARSET_UTF16_LE
        prefix.startsWith(BOM_UTF16_BE) -> CHARSET_UTF16_BE
        else -> null
    }

    /**
     * Кодировка из `<?xml version="1.0" encoding="..."?>`.
     *
     * Пролог декодируется как ISO-8859-1: декларация всегда лежит в ASCII-совместимой части,
     * поэтому её можно прочитать до того, как кодировка определена («Форматы» §3).
     */
    private fun declaredInXmlPrologue(prefix: ByteArray): String? {
        val head = String(prefix.take(XML_DECLARATION_LIMIT).toByteArray(), Charsets.ISO_8859_1)
        if (!head.trimStart().startsWith(XML_PROLOGUE)) return null
        return XML_ENCODING.find(head)?.groupValues?.get(ENCODING_GROUP)
    }

    /** Строгая проверка UTF-8: невалидная последовательность означает однобайтовую кодировку. */
    private fun isValidUtf8(prefix: ByteArray): Boolean = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(prefix))
        true
    } catch (expected: CharacterCodingException) {
        false
    }

    /** Фолбэк для кириллицы: декодируем как windows-1251 и ищем буквы диапазона U+0400–U+04FF. */
    private fun looksLikeCyrillicSingleByte(prefix: ByteArray): Boolean {
        val text = String(prefix, Charset.forName(CHARSET_WINDOWS_1251))
        return text.any { it in CYRILLIC_RANGE }
    }

    private fun canonicalize(name: String): String =
        CANONICAL_NAMES[name.trim().lowercase()] ?: name.trim()

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (size < prefix.size) return false
        for (index in prefix.indices) if (this[index] != prefix[index]) return false
        return true
    }

    private companion object {
        const val CHARSET_UTF8 = "UTF-8"
        const val CHARSET_UTF16_LE = "UTF-16LE"
        const val CHARSET_UTF16_BE = "UTF-16BE"
        const val CHARSET_WINDOWS_1251 = "windows-1251"
        const val XML_DECLARATION_LIMIT = 256
        const val XML_PROLOGUE = "<?xml"
        const val ENCODING_GROUP = 1

        val BOM_UTF8 = "\u00EF\u00BB\u00BF".toByteArray(Charsets.ISO_8859_1)
        val BOM_UTF16_LE = "\u00FF\u00FE".toByteArray(Charsets.ISO_8859_1)
        val BOM_UTF16_BE = "\u00FE\u00FF".toByteArray(Charsets.ISO_8859_1)
        val CYRILLIC_RANGE = '\u0400'..'\u04FF'
        val XML_ENCODING = Regex("""encoding\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        val CANONICAL_NAMES = mapOf(
            "utf-8" to CHARSET_UTF8,
            "utf8" to CHARSET_UTF8,
            "utf-16" to "UTF-16",
            "utf-16le" to CHARSET_UTF16_LE,
            "utf-16be" to CHARSET_UTF16_BE,
            "windows-1251" to CHARSET_WINDOWS_1251,
            "cp1251" to CHARSET_WINDOWS_1251,
            "koi8-r" to "KOI8-R",
            "koi8r" to "KOI8-R",
            "ibm866" to "IBM866",
            "cp866" to "IBM866",
            "us-ascii" to "US-ASCII",
            "ascii" to "US-ASCII",
        )
    }
}
