package io.github.bookshelf.core.metadata.parser.impl

import io.github.bookshelf.core.metadata.encoding.CharsetDetector
import io.github.bookshelf.core.metadata.encoding.DefaultCharsetDetector
import io.github.bookshelf.core.metadata.model.BookMetadata
import io.github.bookshelf.core.metadata.model.MetadataOrigin
import io.github.bookshelf.core.metadata.parser.BookInput
import io.github.bookshelf.core.metadata.parser.BookMetadataParser
import io.github.bookshelf.core.metadata.parser.ParseFailureKind
import io.github.bookshelf.core.metadata.parser.ParseResult
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.Charset
import kotlin.coroutines.cancellation.CancellationException

/**
 * TXT — определение кодировки и осторожный поиск заголовка (docs/formats.md §7).
 *
 * Читаем только префикс файла: текстовая книга может занимать десятки мегабайт, а держать её
 * целиком на e-ink нельзя. Кодировку определяет готовый [CharsetDetector], а MIME используется
 * только как объявление `charset`, если провайдер его передал.
 *
 * Эвристика заголовка намеренно простая и прозрачная: среди первых десяти непустых строк ищем
 * строку длиной 20–120 символов без завершающей точки. Такой заголовок обычно отделён от текста
 * пустыми строками или форматированием, тогда как обычная проза часто длиннее или заканчивается точкой.
 * Если кандидата нет, используем имя файла без расширения. Авторов и описание из TXT не выдумываем.
 */
class TxtParser(
    private val charsetDetector: CharsetDetector = DefaultCharsetDetector(),
) : BookMetadataParser {

    override val id: String = ID

    override val sprint: Int = SPRINT

    override fun supports(extension: String, mimeType: String?): Boolean =
        extension.trim().lowercase().removePrefix(".") in EXTENSIONS ||
            mimeType?.substringBefore(';')?.trim()?.lowercase()?.let { it in MIME_TYPES } == true

    override suspend fun parse(input: BookInput): ParseResult {
        return try {
            val prefix = input.openStream().use { stream -> readPrefix(stream) }
            if (prefix.isEmpty()) {
                return ParseResult.Failed("TXT: файл пуст", ParseFailureKind.EMPTY_FILE)
            }

            val text = decode(prefix, input.mimeType)
            if (!isReadableText(text)) {
                return ParseResult.Failed("TXT: содержимое не похоже на читаемый текст")
            }

            val titleChoice = chooseTitle(text, input.fileName)
            val metadata = BookMetadata(
                title = titleChoice.value,
                source = titleChoice.source,
            )
            ParseResult.Success(metadata = metadata, cover = null)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            val detail = error.message?.takeIf { it.isNotBlank() } ?: "неизвестная ошибка"
            ParseResult.Failed("TXT: не удалось прочитать файл: $detail")
        }
    }

    /** Читает не более [MAX_PREFIX_BYTES], чтобы размер книги не влиял на память. */
    private fun readPrefix(stream: InputStream): ByteArray {
        val result = ByteArrayOutputStream()
        val buffer = ByteArray(READ_BUFFER_BYTES)
        var remaining = MAX_PREFIX_BYTES

        while (remaining > 0) {
            val count = stream.read(buffer, 0, minOf(buffer.size, remaining))
            if (count < 0) break
            if (count == 0) {
                val singleByte = stream.read()
                if (singleByte < 0) break
                result.write(singleByte)
                remaining--
            } else {
                result.write(buffer, 0, count)
                remaining -= count
            }
        }
        return result.toByteArray()
    }

    private fun decode(prefix: ByteArray, mimeType: String?): String {
        val charsetName = charsetDetector.detect(prefix, declaredCharset(mimeType))
        val charset = Charset.forName(charsetName)
        return String(prefix, charset).removePrefix(UTF8_BOM)
    }

    /** MIME `text/plain` сам по себе не является именем кодировки, поэтому ищем только `charset=`. */
    private fun declaredCharset(mimeType: String?): String? {
        val parameter = mimeType
            ?.split(';')
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith(CHARSET_PARAMETER, ignoreCase = true) }
            ?: return null
        return parameter
            .substringAfter('=')
            .trim()
            .removeSurrounding("\"")
            .removeSurrounding("'")
            .takeIf { it.isNotBlank() }
    }

    private fun isReadableText(text: String): Boolean {
        if (text.isBlank() || text.contains(REPLACEMENT_CHARACTER)) return false
        return text.none { character ->
            character.code in CONTROL_CHARACTERS_LOW..CONTROL_CHARACTERS_HIGH &&
                character != '\n' && character != '\r' && character != '\t'
        }
    }

    private fun chooseTitle(text: String, fileName: String): TitleChoice {
        val candidate = text.lineSequence()
            .map { it.trim().removePrefix(UTF8_BOM).trim() }
            .filter { it.isNotEmpty() }
            .filterNot { it.startsWith(XML_DECLARATION, ignoreCase = true) && it.endsWith(XML_END) }
            .take(MAX_TITLE_LINES)
            .firstOrNull { it.length in MIN_TITLE_LENGTH..MAX_TITLE_LENGTH && !it.endsWith('.') }

        return candidate?.let { TitleChoice(value = it, source = MetadataOrigin.EMBEDDED) }
            ?: TitleChoice(value = titleFromFileName(fileName), source = MetadataOrigin.FILENAME)
    }

    private fun titleFromFileName(fileName: String): String {
        val title = fileName.substringBeforeLast('.', fileName).trim()
        return title.ifEmpty { fileName.trim() }
    }

    private data class TitleChoice(
        val value: String,
        val source: MetadataOrigin,
    )

    private companion object {
        const val ID = "txt"
        const val SPRINT = 3
        const val MAX_PREFIX_BYTES = 64 * 1024
        const val READ_BUFFER_BYTES = 8 * 1024
        const val MAX_TITLE_LINES = 10
        const val MIN_TITLE_LENGTH = 20
        const val MAX_TITLE_LENGTH = 120
        const val CHARSET_PARAMETER = "charset="
        const val XML_DECLARATION = "<?xml"
        const val XML_END = "?>"
        const val CONTROL_CHARACTERS_LOW = 0
        const val CONTROL_CHARACTERS_HIGH = 0x1F
        const val UTF8_BOM = "\uFEFF"
        const val REPLACEMENT_CHARACTER = '\uFFFD'
        val EXTENSIONS = setOf("txt")
        val MIME_TYPES = setOf("text/plain")
    }
}
