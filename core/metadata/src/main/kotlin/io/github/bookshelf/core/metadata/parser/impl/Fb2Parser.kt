package io.github.bookshelf.core.metadata.parser.impl

import android.util.Base64
import android.util.Xml
import io.github.bookshelf.core.metadata.encoding.DefaultCharsetDetector
import io.github.bookshelf.core.metadata.model.BookMetadata
import io.github.bookshelf.core.metadata.model.CoverBytes
import io.github.bookshelf.core.metadata.model.MetadataOrigin
import io.github.bookshelf.core.metadata.parser.BookInput
import io.github.bookshelf.core.metadata.parser.BookMetadataParser
import io.github.bookshelf.core.metadata.parser.ParseResult
import io.github.bookshelf.core.metadata.binary.FormatHint
import io.github.bookshelf.core.metadata.binary.MagicBytes
import io.github.bookshelf.core.metadata.zip.ZipLimits
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.StringReader
import java.nio.charset.Charset
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.xmlpull.v1.XmlPullParser

/**
 * FB2 / FB2.ZIP — потоковый разбор XML через `XmlPullParser` (docs/formats.md §3).
 *
 * Поток сначала копируется во временный файл: это позволяет безопасно открыть ZIP как [ZipFile],
 * увидеть центральный каталог и применить [ZipLimits] **до** чтения распакованных записей.
 * Обычный FB2 читается тем же временным файлом, чтобы не терять поток после определения BOM.
 *
 * Кодировка выбирается в порядке BOM → XML-пролог → эвристика [DefaultCharsetDetector] →
 * `windows-1251`. Метаданные всегда берутся из файла, поэтому [MetadataOrigin.EMBEDDED] —
 * правильный источник даже при отсутствии части необязательных полей.
 */
class Fb2Parser(
    /**
     * Предел размера XML, который разбирается целиком.
     *
     * Предел нужен: без него архив-бомба распаковывается в память целиком, а [ZipLimits] проверяет
     * только заявленный размер записи. 16 МБ оказалось мало для настоящих книг: «Книга Уткина
     * Мы тут жизнь живём» с полки устройства — XML 19 465 467 Б, и предел отказывал ей в разборе,
     * книга навсегда оставалась `FAILED` (найдено на устройстве 30.09.2026). 32 МБ покрывают такие
     * книги с запасом; пик памяти проверен на устройстве (см. `docs/TESTING.md`).
     */
    private val maxXmlBytes: Int = MAX_XML_BYTES,
) : BookMetadataParser {

    override val id: String = ID

    override val sprint: Int = SPRINT

    override fun supports(extension: String, mimeType: String?): Boolean =
        extension.trim().lowercase().removePrefix(".") in EXTENSIONS ||
            (mimeType != null && mimeType.lowercase() in MIME_TYPES)

    override suspend fun parse(input: BookInput): ParseResult {
        return try {
            val context = currentCoroutineContext()
            val checkActive: () -> Unit = { context.ensureActive() }
            val spooled = spool(input, checkActive)
            try {
                val extension = input.extension.trim().lowercase().removePrefix(".")
                val zipBySignature = MagicBytes.sniff(spooled.prefix) == FormatHint.ZIP_CONTAINER
                if (extension == ZIP_EXTENSION || zipBySignature) {
                    parseZip(spooled.file, checkActive)
                } else {
                    parsePlainFb2(spooled.file, checkActive)
                }
            } finally {
                spooled.file.delete()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Fb2Failure) {
            ParseResult.Failed(error.message ?: "Не удалось разобрать FB2")
        } catch (error: Throwable) {
            val detail = error.message?.takeIf { it.isNotBlank() } ?: "неизвестная ошибка"
            ParseResult.Failed("Не удалось разобрать FB2 XML: $detail")
        }
    }

    private fun parsePlainFb2(file: File, checkActive: () -> Unit): ParseResult {
        val bytes = readFile(file, maxXmlBytes.toLong(), checkActive)
        if (bytes.isEmpty()) throw Fb2Failure("Пустой FB2-файл")
        return parseXml(bytes, checkActive)
    }

    private fun parseZip(file: File, checkActive: () -> Unit): ParseResult {
        val archive = SafeZip.open(file, checkActive)
        try {
            val entry = archive.entries.firstOrNull { !it.isDirectory && it.name.hasFb2Extension() }
                ?: throw Fb2Failure("В FB2.ZIP не найден файл с расширением fb2")
            val bytes = archive.read(entry, maxXmlBytes, checkActive)
                ?: throw Fb2Failure("FB2 внутри архива превышает допустимый размер XML")
            return parseXml(bytes, checkActive)
        } finally {
            // Закрытие временного архива не должно подменять результат разбора исключением:
            // `close()` не приостанавливает выполнение, поэтому CancellationException здесь взяться
            // не может, а ошибку закрытия глушим осознанно (см. `closeQuietly`).
            archive.closeQuietly()
        }
    }

    private fun parseXml(bytes: ByteArray, checkActive: () -> Unit): ParseResult {
        val text = decodeFb2(bytes, checkActive)
        val parser = newXmlParser()
        parser.setInput(StringReader(text))
        val state = Fb2State()
        val stack = mutableListOf<String>()

        while (true) {
            checkActive()
            when (parser.next()) {
                XmlPullParser.START_TAG -> onStartTag(parser, state, stack)
                XmlPullParser.TEXT,
                XmlPullParser.CDSECT,
                XmlPullParser.ENTITY_REF -> appendText(parser.text, state.captures)

                XmlPullParser.END_TAG -> {
                    val ending = state.captures
                        .filter { it.depth == stack.size }
                        .sortedByDescending { it.depth }
                    state.captures.removeAll(ending.toSet())
                    ending.forEach { it.finish() }
                    if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
                }

                XmlPullParser.END_DOCUMENT -> break
            }
        }

        if (stack.isNotEmpty()) throw Fb2Failure("FB2: нарушена структура XML")
        if (!state.rootFound) throw Fb2Failure("В FB2 не найден корень FictionBook")
        return state.toResult()
    }

    private fun onStartTag(parser: XmlPullParser, state: Fb2State, stack: MutableList<String>) {
        val name = localName(parser.name)
        if (stack.isEmpty()) {
            if (name != ROOT_ELEMENT) throw Fb2Failure("Ожидался корень FictionBook")
            state.rootFound = true
        }
        val parent = stack.lastOrNull()
        stack.add(name)

        when {
            name == "sequence" && parent == "title-info" -> {
                state.seriesName = attribute(parser, "name", null)?.trim()?.takeIf { it.isNotEmpty() }
                state.seriesIndex = attribute(parser, "number", null)
                    ?.replace(',', '.')
                    ?.toDoubleOrNull()
            }

            name == "book-title" && parent == "title-info" ->
                state.addCapture(name, stack.size, onEnd = { state.title = it.text.toString() })

            name == "annotation" && parent == "title-info" ->
                state.addCapture(name, stack.size, onEnd = { state.description = it.text.toString() })

            name == "lang" && parent == "title-info" ->
                state.addCapture(name, stack.size, onEnd = { state.language = it.text.toString() })

            name == "genre" && parent == "title-info" ->
                state.addCapture(name, stack.size, onEnd = { state.addTag(it.text.toString()) })

            name == "author" && parent == "title-info" -> state.startAuthor(stack.size)

            name in AUTHOR_FIELDS && parent == "author" ->
                state.addAuthorFieldCapture(name, stack.size)

            name == "isbn" && parent == "publish-info" ->
                state.addCapture(name, stack.size, onEnd = { state.isbn = it.text.toString() })

            name == "year" && parent == "publish-info" ->
                state.addCapture(name, stack.size, onEnd = { state.year = it.text.toString() })

            name == "publisher" && parent == "publish-info" ->
                state.addCapture(name, stack.size, onEnd = { state.publisher = it.text.toString() })

            name == "image" && parent == "coverpage" -> {
                val href = attribute(parser, "href", XLINK_NAMESPACE)?.trim()
                    ?: attribute(parser, "href", null)?.trim()
                if (href?.startsWith('#') == true) state.coverId = href.removePrefix("#")
            }

            name == "binary" -> {
                val id = attribute(parser, "id", null)?.trim().orEmpty()
                val contentType = attribute(parser, "content-type", null)
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                if (id.isNotEmpty()) {
                    state.startBinary(id, contentType, stack.size)
                }
            }
        }
    }

    private fun appendText(value: String?, captures: List<Fb2Capture>) {
        if (value.isNullOrEmpty()) return
        captures.forEach { it.append(value) }
    }

    private fun decodeFb2(bytes: ByteArray, checkActive: () -> Unit): String {
        checkActive()
        val prefix = bytes.copyOf(minOf(bytes.size, CHARSET_PREFIX_SIZE))
        val detected = DefaultCharsetDetector().detect(prefix, null)
        val charset = runCatching { Charset.forName(detected) }
            .getOrElse { Charset.forName(WINDOWS_1251) }
        return String(bytes, charset).removePrefix("\uFEFF")
    }

    private fun newXmlParser(): XmlPullParser = Xml.newPullParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
    }

    private fun attribute(parser: XmlPullParser, name: String, namespace: String?): String? {
        for (index in 0 until parser.attributeCount) {
            val local = localName(parser.getAttributeName(index))
            if (local != name) continue
            if (namespace == null || parser.getAttributeNamespace(index) == namespace) {
                return parser.getAttributeValue(index)
            }
            if (namespace == XLINK_NAMESPACE && local == "href") return parser.getAttributeValue(index)
        }
        return null
    }

    private fun localName(name: String?): String = name?.substringAfterLast(':').orEmpty()

    private fun String.hasFb2Extension(): Boolean =
        substringAfterLast('/').substringAfterLast('.').equals("fb2", ignoreCase = true)

    private fun spool(input: BookInput, checkActive: () -> Unit): SpooledInput {
        val file = File.createTempFile("bookshelf-fb2-", ".tmp")
        var completed = false
        try {
            input.openStream().use { source ->
                FileOutputStream(file).use { target -> copy(source, target, checkActive) }
            }
            val prefix = readPrefix(file, checkActive)
            completed = true
            return SpooledInput(file, prefix)
        } finally {
            if (!completed) file.delete()
        }
    }

    private fun readPrefix(file: File, checkActive: () -> Unit): ByteArray {
        val result = ByteArray(minOf(file.length(), MAGIC_PREFIX_SIZE).toInt())
        if (result.isEmpty()) return result
        FileInputStream(file).use { stream ->
            var offset = 0
            while (offset < result.size) {
                checkActive()
                val read = stream.read(result, offset, result.size - offset)
                if (read < 0) break
                if (read > 0) offset += read
            }
        }
        return result
    }

    private fun copy(source: InputStream, target: FileOutputStream, checkActive: () -> Unit) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            checkActive()
            val count = source.read(buffer)
            if (count < 0) return
            if (count > 0) target.write(buffer, 0, count)
        }
    }

    private fun readFile(file: File, maxBytes: Long, checkActive: () -> Unit): ByteArray {
        if (file.length() > maxBytes) throw Fb2Failure("FB2 превышает допустимый размер XML")
        val output = ByteArrayOutputStream(file.length().toInt().coerceAtLeast(INITIAL_BUFFER_SIZE))
        FileInputStream(file).use { stream ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            while (true) {
                checkActive()
                val count = stream.read(buffer)
                if (count < 0) break
                if (count <= 0) continue
                total += count
                if (total > maxBytes) throw Fb2Failure("FB2 превышает допустимый размер XML")
                output.write(buffer, 0, count)
            }
        }
        return output.toByteArray()
    }

    private data class SpooledInput(val file: File, val prefix: ByteArray)

    private class Fb2Failure(message: String, cause: Throwable? = null) : Exception(message, cause)

    private class SafeZip private constructor(
        private val zip: ZipFile,
        val entries: List<ZipEntry>,
    ) : Closeable {

        fun read(entry: ZipEntry, maxBytes: Int, checkActive: () -> Unit): ByteArray? {
            if (entry.size >= 0 && entry.size > maxBytes) return null
            val output = ByteArrayOutputStream(
                minOf(entry.size.coerceAtLeast(0L), maxBytes.toLong()).toInt().coerceAtLeast(INITIAL_BUFFER_SIZE),
            )
            zip.getInputStream(entry).use { stream ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    checkActive()
                    val count = stream.read(buffer)
                    if (count < 0) break
                    if (count <= 0) continue
                    total += count
                    if (total > maxBytes) return null
                    output.write(buffer, 0, count)
                }
            }
            return output.toByteArray()
        }

        override fun close() {
            zip.close()
        }

        /**
         * Закрытие, при котором ошибка закрытия не подменяет результат разбора.
         *
         * Раньше здесь был `throw` внутри `finally` — компилятор и detekt справедливо ругались:
         * исключение из `finally` перекрывает исходную ошибку разбора. `close()` не приостанавливает
         * выполнение, поэтому терять CancellationException тут нечего, а сбой закрытия временного
         * архива не важнее того, что уже случилось в разборе.
         */
        fun closeQuietly() {
            try {
                close()
            } catch (_: Throwable) {
                // Осознанно: результат разбора важнее ошибки закрытия временного архива.
            }
        }

        companion object {
            fun open(file: File, checkActive: () -> Unit): SafeZip {
                val zip = try {
                    ZipFile(file)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    // Причина сохраняется: без неё в отчёте видно только «архив повреждён»,
                    // хотя настоящая ошибка (обрыв, нет места, неверный формат) — в cause.
                    throw Fb2Failure("FB2.ZIP: архив повреждён или не является ZIP", error)
                }
                try {
                    val entries = mutableListOf<ZipEntry>()
                    val enumeration = zip.entries()
                    var uncompressed = 0L
                    var compressed = 0L
                    var unknownSize = false
                    while (enumeration.hasMoreElements()) {
                        checkActive()
                        val entry = enumeration.nextElement()
                        entries += entry
                        if (entry.size >= 0) uncompressed += entry.size else unknownSize = true
                        if (entry.compressedSize >= 0) compressed += entry.compressedSize else unknownSize = true
                    }
                    val compressedForCheck = if (unknownSize) maxOf(1L, compressed) else compressed
                    if (ZipLimits.DEFAULT.isSuspicious(
                            entries = entries.size,
                            uncompressed = uncompressed,
                            compressed = compressedForCheck,
                        )
                    ) {
                        throw Fb2Failure("FB2.ZIP: архив превышает лимиты безопасности")
                    }
                    return SafeZip(zip, entries)
                } catch (cancelled: CancellationException) {
                    try {
                        zip.close()
                    } catch (_: Throwable) {
                        // Оригинальная отмена важнее ошибки закрытия.
                    }
                    throw cancelled
                } catch (error: Throwable) {
                    try {
                        zip.close()
                    } catch (_: Throwable) {
                        // Оригинальная ошибка важнее ошибки закрытия.
                    }
                    throw error
                }
            }
        }
    }

    private class Fb2State {
        var rootFound = false
        var title: String? = null
        var description: String? = null
        var language: String? = null
        var seriesName: String? = null
        var seriesIndex: Double? = null
        var isbn: String? = null
        var year: String? = null
        var publisher: String? = null
        var coverId: String? = null
        val authors = mutableListOf<String>()
        val tags = mutableListOf<String>()
        val captures = mutableListOf<Fb2Capture>()
        val binaries = mutableMapOf<String, BinaryValue>()
        private var activeAuthor: Fb2Author? = null
        private var activeAuthorDepth = -1

        fun addCapture(
            name: String,
            depth: Int,
            maxLength: Int = MAX_TEXT_CAPTURE,
            onEnd: (Fb2Capture) -> Unit,
        ) {
            captures += Fb2Capture(name, depth, maxLength, onEnd)
        }

        fun startAuthor(depth: Int) {
            if (activeAuthor != null) finishAuthor()
            activeAuthor = Fb2Author()
            activeAuthorDepth = depth
            addCapture("author", depth, onEnd = { capture -> finishAuthor(capture.text.toString()) })
        }

        fun addAuthorFieldCapture(name: String, depth: Int) {
            addCapture(name, depth, onEnd = { capture ->
                activeAuthor?.fields?.getOrPut(name) { StringBuilder() }?.append(capture.text.toString())
            })
        }

        fun startBinary(id: String, contentType: String?, depth: Int) {
            addCapture("binary", depth, maxLength = MAX_COVER_BASE64_CHARS, onEnd = { capture ->
                binaries[id] = BinaryValue(capture.text.toString(), contentType, capture.overflow)
            })
        }

        fun addTag(value: String) {
            val tag = normalize(value) ?: return
            if (tag !in tags) tags += tag
        }

        fun toResult(): ParseResult {
            val bookTitle = normalize(title)
            if (bookTitle.isNullOrEmpty()) return ParseResult.Failed("В FB2 не найден book-title")
            val metadata = BookMetadata(
                title = bookTitle,
                authors = authors.mapNotNull { normalize(it) },
                seriesName = normalize(seriesName),
                seriesIndex = seriesIndex,
                language = normalize(language),
                publishedYear = year?.let(::yearFromText),
                publisher = normalize(publisher),
                isbn = normalize(isbn),
                description = normalize(description),
                tags = tags,
                source = MetadataOrigin.EMBEDDED,
            )
            return ParseResult.Success(metadata, findCover())
        }

        private fun finishAuthor(rawText: String? = null) {
            val author = activeAuthor ?: return
            if (activeAuthorDepth >= 0) {
                val value = if (author.fields.isNotEmpty()) {
                    author.fields.values.joinToString(" ") { normalize(it.toString()).orEmpty() }.trim()
                } else {
                    normalize(rawText ?: author.raw.toString())
                }
                if (!value.isNullOrEmpty()) authors += value
            }
            activeAuthor = null
            activeAuthorDepth = -1
        }

        private fun findCover(): CoverBytes? {
            val id = coverId ?: return null
            val binary = binaries[id] ?: return null
            if (binary.overflow) return null
            val encoded = binary.value.filterNot(Char::isWhitespace)
            if (encoded.isEmpty()) return null
            val bytes = runCatching { Base64.decode(encoded, Base64.DEFAULT) }.getOrNull() ?: return null
            if (bytes.isEmpty() || bytes.size > MAX_COVER_BYTES) return null
            return CoverBytes(bytes, binary.mimeType ?: DEFAULT_COVER_MIME, null, null)
        }

        private fun normalize(value: String?): String? =
            value?.replace(WHITESPACE, " ")?.trim()?.takeIf { it.isNotEmpty() }

        private fun yearFromText(value: String): Int? =
            YEAR.find(value)?.value?.toIntOrNull()
    }

    private class Fb2Author {
        val raw = StringBuilder()
        val fields = linkedMapOf<String, StringBuilder>()
    }

    private class BinaryValue(val value: String, val mimeType: String?, val overflow: Boolean)

    private class Fb2Capture(
        val name: String,
        val depth: Int,
        private val maxLength: Int,
        private val onEnd: (Fb2Capture) -> Unit,
    ) {
        val text = StringBuilder()
        var overflow = false

        fun append(value: String) {
            if (overflow || value.isEmpty()) return
            val separator = text.isNotEmpty() &&
                !text.last().isWhitespace() &&
                !value.first().isWhitespace()
            val additionalLength = value.length + if (separator) 1 else 0
            if (text.length + additionalLength > maxLength) {
                overflow = true
                text.setLength(0)
                return
            }
            if (separator) text.append(' ')
            text.append(value)
        }

        fun finish() {
            onEnd(this)
        }
    }

    private companion object {
        const val ID = "fb2"
        const val SPRINT = 3
        const val ZIP_EXTENSION = "fb2.zip"
        const val ROOT_ELEMENT = "FictionBook"
        const val WINDOWS_1251 = "windows-1251"
        const val DEFAULT_COVER_MIME = "application/octet-stream"
        const val XLINK_NAMESPACE = "http://www.w3.org/1999/xlink"
        const val MAX_COVER_BYTES = 2 * 1_024 * 1_024
        const val MAX_XML_BYTES = 32 * 1_024 * 1_024
        const val MAGIC_PREFIX_SIZE = 256L
        const val CHARSET_PREFIX_SIZE = 4_096
        const val MAX_TEXT_CAPTURE = 4 * 1_024 * 1_024
        const val INITIAL_BUFFER_SIZE = 32
        const val MAX_COVER_BASE64_CHARS = ((MAX_COVER_BYTES + 2) / 3) * 4 + 4
        val EXTENSIONS = setOf("fb2", ZIP_EXTENSION)
        val MIME_TYPES = setOf("application/fb2", "application/x-fictionbook+xml")
        val AUTHOR_FIELDS = setOf(
            "first-name",
            "middle-name",
            "last-name",
            "nickname",
            "name",
        )
        val WHITESPACE = Regex("\\s+")
        val YEAR = Regex("\\d{4}")
    }
}
