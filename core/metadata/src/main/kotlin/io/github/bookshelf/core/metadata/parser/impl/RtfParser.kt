package io.github.bookshelf.core.metadata.parser.impl

import io.github.bookshelf.core.metadata.model.BookMetadata
import io.github.bookshelf.core.metadata.model.MetadataOrigin
import io.github.bookshelf.core.metadata.parser.BookInput
import io.github.bookshelf.core.metadata.parser.BookMetadataParser
import io.github.bookshelf.core.metadata.parser.ParseFailureKind
import io.github.bookshelf.core.metadata.parser.ParseResult
import java.io.InputStream
import java.nio.charset.Charset
import kotlin.coroutines.cancellation.CancellationException

/**
 * RTF — текст с группой `{\info …}` (docs/formats.md §7).
 *
 * Метаданные лежат в первых сотнях байт, поэтому читается только префикс файла: документ может
 * занимать десятки мегабайт. Префикс разбирается как ISO-8859-1 — байт в байт, потому что
 * кириллица в RTF приходит либо экранированием `\'hh` в объявленной кодовой странице
 * (`\ansicpg`), либо юникод-эскейпом `\uN`; «живых» многобайтных символов в RTF не бывает.
 *
 * Читаются `\title`, `\author`, `\subject`, `\keywords`, `\company`, год из `\creatim\yr…`
 * и язык из `\lang…`.
 */
class RtfParser : BookMetadataParser {

    override val id: String = ID

    override val sprint: Int = SPRINT

    override fun supports(extension: String, mimeType: String?): Boolean =
        extension.trim().lowercase().removePrefix(".") in EXTENSIONS ||
            (mimeType != null && mimeType.lowercase() in MIME_TYPES)

    override suspend fun parse(input: BookInput): ParseResult {
        return try {
            val prefix = input.openStream().use { readPrefix(it) }
            if (prefix.isEmpty()) {
                return ParseResult.Failed("RTF: файл пуст", ParseFailureKind.EMPTY_FILE)
            }
            val text = String(prefix, Charsets.ISO_8859_1)
            if (!isRtf(text)) {
                return ParseResult.Failed(
                    reason = "RTF: файл не начинается с {\\rtf",
                    kind = ParseFailureKind.CORRUPT_FILE,
                )
            }
            val metadata = readMetadata(text)
            when {
                metadata != null -> ParseResult.Success(metadata, cover = null)

                // `\info` — необязательная группа: её отсутствие не ошибка, а частичный разбор.
                else -> ParseResult.Partial(
                    metadata = BookMetadata(
                        title = titleFromFileName(input.fileName),
                        source = MetadataOrigin.FILENAME,
                    ),
                    reason = "RTF: группа \\info не найдена",
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            ParseResult.Failed(
                reason = "RTF: ${error.message ?: "не удалось разобрать файл"}",
                kind = ParseFailureKind.CORRUPT_FILE,
            )
        }
    }

    private fun readMetadata(text: String): BookMetadata? {
        val info = infoGroup(text) ?: return null
        val codePage = codePageOf(text)
        val title = fieldOf(info, TITLE)?.let { decodeEscapes(it, codePage) }?.trim().orEmpty()
        if (title.isEmpty()) return null
        val keywords = fieldOf(info, KEYWORDS)?.let { decodeEscapes(it, codePage) }.orEmpty()
        return BookMetadata(
            title = title,
            authors = listOfNotNull(
                fieldOf(info, AUTHOR)?.let { decodeEscapes(it, codePage) }?.trim()?.ifBlank { null },
            ),
            language = languageOf(text),
            publishedYear = yearOf(info),
            publisher = fieldOf(info, COMPANY)?.let { decodeEscapes(it, codePage) }?.trim()?.ifBlank { null },
            description = fieldOf(info, SUBJECT)?.let { decodeEscapes(it, codePage) }?.trim()?.ifBlank { null },
            tags = keywords.split(',').map { it.trim() }.filter { it.isNotEmpty() },
            source = MetadataOrigin.EMBEDDED,
        )
    }

    /** Содержимое группы `{\info …}`: ищется с учётом вложенных фигурных скобок. */
    private fun infoGroup(text: String): String? {
        val start = text.indexOf(INFO_MARKER)
        if (start < 0) return null
        var depth = 0
        var index = start
        while (index < text.length) {
            val char = text[index]
            if (char == ESCAPE) {
                // Экранированная скобка (`\{`, `\}`) — это ТЕКСТ, а не граница группы.
                index += escapeLength(text, index)
                continue
            }
            if (char == '{') depth += 1
            if (char == '}') {
                depth -= 1
                if (depth == 0) return text.substring(start + 1, index)
            }
            index += 1
        }
        return text.substring(start + 1)
    }

    /**
     * Значение поля внутри `\info`.
     *
     * Группа поля читается целиком (вложенные скобки учитываются), затем из неё вырезается
     * управляющее слово: `{\title …}` → `…`.
     */
    private fun fieldOf(info: String, name: String): String? {
        var found: String? = null
        var index = 0
        while (index < info.length && found == null) {
            val at = info.indexOf(ESCAPE, index)
            if (at < 0) break
            val wordEnd = controlWordEnd(info, at)
            if (info.substring(at + 1, wordEnd) == name) found = valueOf(info, wordEnd)
            index = wordEnd
        }
        return found
    }

    /** Конец имени управляющего слова, которое начинается на позиции `at` (там обратный слэш). */
    private fun controlWordEnd(info: String, at: Int): Int {
        var end = at + 1
        while (end < info.length && info[end].isLetter()) end += 1
        return end
    }

    /** Значение управляющего слова: до закрывающей скобки его группы. */
    private fun valueOf(info: String, from: Int): String {
        var depth = 0
        var index = from
        while (index < info.length) {
            val char = info[index]
            if (char == ESCAPE) {
                // `\{` и `\}` внутри значения — текст: без этого заголовок обрывался бы на первой
                // же экранированной скобке (поймано тестом).
                index += escapeLength(info, index)
                continue
            }
            if (char == '{') depth += 1
            if (char == '}') {
                if (depth == 0) return info.substring(from, index)
                depth -= 1
            }
            index += 1
        }
        return info.substring(from)
    }

    /** Длина эскейпа с позиции `\`: `\\` и `\{` — два символа, `\'hh` — четыре. */
    private fun escapeLength(text: String, at: Int): Int =
        if (text.getOrNull(at + 1) == HEX_QUOTE) ESCAPE_HEX_LENGTH else ESCAPE_PAIR_LENGTH

    /** Раскодировать `\'hh`, `\uN`, `\\`, `\{`, `\}` и служебные слова внутри значения. */
    private fun decodeEscapes(raw: String, codePage: Charset): String {
        val out = StringBuilder(raw.length)
        var index = 0
        var skipAfterUnicode = DEFAULT_UNICODE_SKIP
        while (index < raw.length) {
            val char = raw[index]
            if (char != ESCAPE) {
                out.append(char)
                index += 1
            } else {
                val escape = readEscape(
                    raw = raw,
                    at = index,
                    codePage = codePage,
                    unicodeSkip = skipAfterUnicode,
                )
                out.append(escape.text)
                skipAfterUnicode = escape.unicodeSkip
                index = escape.nextIndex
            }
        }
        return out.toString()
    }

    /**
     * Один эскейп с позиции `\`.
     *
     * Возвращает подставляемый текст, позицию продолжения и — для `\uc<N>` — новое число
     * символов подстановки, которые надо пропустить после `\uN`.
     */
    private fun readEscape(raw: String, at: Int, codePage: Charset, unicodeSkip: Int): Escape {
        val next = raw.getOrNull(at + 1)
            ?: return Escape(text = "", nextIndex = raw.length, unicodeSkip = unicodeSkip)
        return when {
            next == HEX_QUOTE -> readHexEscape(raw = raw, at = at, codePage = codePage, unicodeSkip = unicodeSkip)

            next == UNICODE_MARKER && isNumberStart(raw.getOrNull(at + ESCAPE_PAIR_LENGTH)) ->
                readUnicodeEscape(raw = raw, at = at, unicodeSkip = unicodeSkip)

            next in ESCAPED_CHARS ->
                Escape(ESCAPED_CHARS.getValue(next).toString(), at + ESCAPE_PAIR_LENGTH, unicodeSkip)

            next.isLetter() -> readControlEscape(raw = raw, at = at, unicodeSkip = unicodeSkip)
            else -> Escape(text = "", nextIndex = at + ESCAPE_PAIR_LENGTH, unicodeSkip = unicodeSkip)
        }
    }

    /** `\'hh` — байт в объявленной кодовой странице документа. */
    private fun readHexEscape(raw: String, at: Int, codePage: Charset, unicodeSkip: Int): Escape {
        val hex = raw.substring(at + ESCAPE_PAIR_LENGTH, minOf(at + HEX_ESCAPE_LENGTH, raw.length))
        val byte = hex.toIntOrNull(HEX_RADIX)?.toByte()
            ?: return Escape(text = "", nextIndex = at + HEX_ESCAPE_LENGTH, unicodeSkip = unicodeSkip)
        return Escape(
            text = String(byteArrayOf(byte), codePage),
            nextIndex = at + HEX_ESCAPE_LENGTH,
            unicodeSkip = unicodeSkip,
        )
    }

    /** `\uN` — код символа; отрицательное число означает «плюс 65536». */
    private fun readUnicodeEscape(raw: String, at: Int, unicodeSkip: Int): Escape {
        val parsed = readNumber(raw, at + ESCAPE_PAIR_LENGTH)
        val code = parsed.first
        val text = if (code == null) {
            ""
        } else {
            String(Character.toChars(if (code < 0) code + CODE_POINT_OFFSET else code))
        }
        return Escape(
            text = text,
            nextIndex = skipAnsiRepresentation(raw = raw, from = parsed.second, count = unicodeSkip),
            unicodeSkip = unicodeSkip,
        )
    }

    /** Управляющее слово: `\uc<N>` меняет пропуск после `\uN`, служебные слова читаются пробелом. */
    private fun readControlEscape(raw: String, at: Int, unicodeSkip: Int): Escape {
        val control = readControlWord(raw, at)
        val newSkip = if (control.word == UC) control.number ?: unicodeSkip else unicodeSkip
        return Escape(
            text = SPACING_WORDS[control.word].orEmpty(),
            nextIndex = control.third,
            unicodeSkip = newSkip,
        )
    }

    /** Разобранный эскейп: что подставить, откуда продолжить и актуальное значение `\uc`. */
    private data class Escape(val text: String, val nextIndex: Int, val unicodeSkip: Int)

    /** `\word123`: слово, число и позиция после слова. */
    private fun readControlWord(raw: String, from: Int): ControlWord {
        var end = from
        while (end < raw.length && raw[end].isLetter()) end += 1
        val word = raw.substring(from, end)
        val parsed = readNumber(raw, end)
        // Пробел после управляющего слова — разделитель, а не текст.
        val after = if (parsed.second < raw.length && raw[parsed.second] == ' ') parsed.second + 1 else parsed.second
        return ControlWord(word = word, number = parsed.first, third = after)
    }

    /** Начинается ли число: так `\u1042` отличается от управляющих слов `\uc`, `\ul`. */
    private fun isNumberStart(char: Char?): Boolean = char != null && (char.isDigit() || char == '-')

    /** Число с учётом знака: `\u-10179` — юникод-эскейп отрицательным числом. */
    private fun readNumber(raw: String, from: Int): Pair<Int?, Int> {
        var end = from
        if (end < raw.length && (raw[end] == '-' || raw[end] == '+')) end += 1
        while (end < raw.length && raw[end].isDigit()) end += 1
        if (end == from) return null to from
        return raw.substring(from, end).toIntOrNull() to end
    }

    /** Пропустить ANSI-представление после `\uN` (обычно `?`): столько символов задаёт `\uc`. */
    private fun skipAnsiRepresentation(raw: String, from: Int, count: Int): Int {
        var index = from
        var skipped = 0
        while (index < raw.length && skipped < count && raw[index] != '\\') {
            index += 1
            skipped += 1
        }
        return index
    }

    private fun codePageOf(text: String): Charset {
        val parsed = readNumber(text, (text.indexOf(CODE_PAGE_MARKER) + CODE_PAGE_MARKER.length).coerceAtLeast(0))
        val number = parsed.first
        // RTF без `\ansicpg` в русских библиотеках почти всегда windows-1251 — то же умолчание,
        // что и в разборах FB2/TXT/архивов.
        val charset = when (number) {
            null -> WINDOWS_1251
            CODE_PAGE_UTF8 -> Charsets.UTF_8.name()
            else -> "windows-$number"
        }
        return runCatching { Charset.forName(charset) }.getOrDefault(Charset.forName(WINDOWS_1251))
    }

    /** Год создания документа: `{\creatim\yr1869\mo1\dy1}`. */
    private fun yearOf(info: String): Int? {
        val at = info.indexOf(CREATION_MARKER)
        if (at < 0) return null
        val yearAt = info.indexOf(YEAR_MARKER, at)
        if (yearAt < 0) return null
        return readNumber(info, yearAt + YEAR_MARKER.length).first
    }

    /** Язык документа: `\lang1049` — это LANGID, а не код языка. */
    private fun languageOf(text: String): String? {
        val at = text.indexOf(LANGUAGE_MARKER)
        if (at < 0) return null
        val langId = readNumber(text, at + LANGUAGE_MARKER.length).first ?: return null
        return PRIMARY_LANGUAGES[langId and PRIMARY_LANGUAGE_MASK]
    }

    private fun readPrefix(stream: InputStream): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_SIZE)
        var total = 0
        while (total < MAX_PREFIX_BYTES) {
            val read = stream.read(buffer, 0, minOf(buffer.size, MAX_PREFIX_BYTES - total))
            if (read <= 0) break
            out.write(buffer, 0, read)
            total += read
        }
        return out.toByteArray()
    }

    private fun isRtf(text: String): Boolean = text.trimStart().startsWith(RTF_MARKER)

    private fun titleFromFileName(fileName: String): String =
        fileName.substringBeforeLast('.').trim().ifEmpty { fileName }

    /** Управляющее слово с числовым параметром: `\uc1` → `uc`, `1`. */
    private data class ControlWord(val word: String, val number: Int?, val third: Int)

    private companion object {
        const val ID = "rtf"
        const val SPRINT = 6
        const val BUFFER_SIZE = 16 * 1_024
        const val MAX_PREFIX_BYTES = 256 * 1_024
        const val WINDOWS_1251 = "windows-1251"
        const val CODE_PAGE_UTF8 = 65001
        const val CODE_POINT_OFFSET = 65_536
        const val PRIMARY_LANGUAGE_MASK = 0x3FF
        const val RTF_MARKER = "{\\rtf"
        const val INFO_MARKER = "{\\info"
        const val CODE_PAGE_MARKER = "\\ansicpg"
        const val CREATION_MARKER = "\\creatim"
        const val YEAR_MARKER = "\\yr"
        const val LANGUAGE_MARKER = "\\lang"
        const val TITLE = "title"
        const val AUTHOR = "author"
        const val SUBJECT = "subject"
        const val KEYWORDS = "keywords"
        const val COMPANY = "company"
        const val UC = "uc"
        const val ESCAPE = '\\'
        const val HEX_QUOTE = '\''
        const val UNICODE_MARKER = 'u'
        const val HEX_ESCAPE_LENGTH = 4
        const val ESCAPE_HEX_LENGTH = 4
        const val ESCAPE_PAIR_LENGTH = 2
        const val DEFAULT_UNICODE_SKIP = 1
        const val HEX_RADIX = 16

        val EXTENSIONS = setOf("rtf")
        val MIME_TYPES = setOf("application/rtf", "text/rtf", "application/x-rtf")

        /** Простые эскейпы RTF: сам символ вместо обратного слэша. */
        val ESCAPED_CHARS = mapOf(
            '\\' to '\\',
            '{' to '{',
            '}' to '}',
            '~' to '\u00A0',
            '_' to '-',
            '-' to '\u00AD',
        )

        /** Служебные слова, которые в подписи читаются пробелом. */
        val SPACING_WORDS = mapOf("par" to " ", "tab" to " ", "line" to " ", "sect" to " ")

        /**
         * Основные языки по младшим 10 битам LANGID.
         *
         * Полный список в коде не нужен: карточка показывает язык только когда он распознан,
         * а для остальных случаев в базе остаётся `null` — выдумывать значение нельзя.
         */
        val PRIMARY_LANGUAGES = mapOf(
            0x09 to "en",
            0x07 to "de",
            0x0C to "fr",
            0x0A to "es",
            0x10 to "it",
            0x15 to "pl",
            0x19 to "ru",
            0x22 to "uk",
            0x0E to "hu",
            0x05 to "cs",
            0x1A to "hr",
            0x1F to "tr",
            0x11 to "ja",
            0x04 to "zh",
        )
    }
}
