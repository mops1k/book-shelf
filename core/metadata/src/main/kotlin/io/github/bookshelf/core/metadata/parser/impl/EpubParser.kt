package io.github.bookshelf.core.metadata.parser.impl

import android.util.Xml
import io.github.bookshelf.core.metadata.encoding.DefaultCharsetDetector
import io.github.bookshelf.core.metadata.model.BookMetadata
import io.github.bookshelf.core.metadata.model.CoverBytes
import io.github.bookshelf.core.metadata.model.MetadataOrigin
import io.github.bookshelf.core.metadata.parser.BookInput
import io.github.bookshelf.core.metadata.parser.BookMetadataParser
import io.github.bookshelf.core.metadata.parser.ParseResult
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
 * EPUB 2 / 3 — разбор ZIP-контейнера и OPF без внешней библиотеки (docs/formats.md §4).
 *
 * Сначала читается только центральный каталог ZIP и проверяются [ZipLimits]; распакованные записи
 * не открываются, пока безопасность архива не подтверждена. `container.xml` задаёт единственный
 * путь к OPF, а Dublin Core и manifest дают метаданные и обложку. Стандартная EPUB-серия имеет
 * приоритет над calibre-расширением, потому что она является частью спецификации EPUB 3.
 */
class EpubParser : BookMetadataParser {

    override val id: String = ID

    override val sprint: Int = SPRINT

    override fun supports(extension: String, mimeType: String?): Boolean =
        extension.trim().lowercase().removePrefix(".") in EXTENSIONS ||
            (mimeType != null && mimeType.lowercase() in MIME_TYPES)

    override suspend fun parse(input: BookInput): ParseResult {
        var spooled: SpooledInput? = null
        var archive: SafeZip? = null
        return try {
            val context = currentCoroutineContext()
            val checkActive: () -> Unit = { context.ensureActive() }
            spooled = spool(input, checkActive)
            archive = SafeZip.open(spooled.file, checkActive)
            parseArchive(archive, checkActive)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: EpubFailure) {
            ParseResult.Failed(error.message ?: "Не удалось разобрать EPUB")
        } catch (error: Throwable) {
            val detail = error.message?.takeIf { it.isNotBlank() } ?: "неизвестная ошибка"
            ParseResult.Failed("Не удалось разобрать EPUB: $detail")
        } finally {
            // Ни закрытие архива, ни удаление временного файла не бросают: исключение из `finally`
            // перекрыло бы исходную ошибку разбора (см. `closeQuietly`).
            archive?.closeQuietly()
            spooled?.file?.delete()
        }
    }

    private fun parseArchive(archive: SafeZip, checkActive: () -> Unit): ParseResult {
        val containerEntry = archive.entries.firstOrNull {
            it.name.equals(CONTAINER_PATH, ignoreCase = true)
        } ?: throw EpubFailure("В EPUB отсутствует META-INF/container.xml")
        val containerBytes = archive.read(containerEntry, MAX_XML_BYTES, checkActive)
            ?: throw EpubFailure("container.xml превышает допустимый размер XML")
        val opfPath = parseContainer(containerBytes, checkActive)
        val opfEntry = archive.find(opfPath)
            ?: throw EpubFailure("OPF из container.xml не найден в архиве: $opfPath")
        val opfBytes = archive.read(opfEntry, MAX_XML_BYTES, checkActive)
            ?: throw EpubFailure("OPF превышает допустимый размер XML")
        return parseOpf(opfBytes, opfPath, archive, checkActive)
    }

    /**
     * Начало элемента в `container.xml`: проверка корня и запоминание `full-path` первого rootfile.
     *
     * Вынесено из `parseContainer` отдельной функцией: та перевалила за порог
     * `CyclomaticComplexMethod`, а разбор одного элемента читается отдельно от цикла.
     */
    private fun containerStartTag(
        parser: XmlPullParser,
        stack: MutableList<String>,
        path: String?,
    ): String? {
        val name = localName(parser.name)
        if (stack.isEmpty() && name != CONTAINER_ROOT) {
            throw EpubFailure("Ожидался корень container")
        }
        stack.add(name)
        if (name != ROOTFILE_ELEMENT) return path
        val namespace = parser.namespace
        if (namespace != null && namespace != CONTAINER_NAMESPACE) {
            throw EpubFailure("Неверный namespace container.xml")
        }
        if (path != null) return path
        return attribute(parser, "full-path", null)?.let(::normalizePath)
    }

    /** Текстовый узел OPF: складывается в текущий захват состояния. */
    private fun appendTextNode(parser: XmlPullParser, state: EpubState) {
        val text = parser.text
        if (!text.isNullOrEmpty()) state.appendText(text)
    }

    /**
     * Конец элемента OPF: закрываются захваты этой глубины и особые элементы (группа, серия).
     *
     * Вынесено из `parseOpf` отдельной функцией: та перевалила за порог `CyclomaticComplexMethod`.
     */
    private fun endTag(state: EpubState, stack: MutableList<String>) {
        val ending = state.captures
            .filter { it.depth == stack.size }
            .sortedByDescending { it.depth }
        state.captures.removeAll(ending.toSet())
        ending.forEach { it.finish() }
        if (stack.isEmpty()) return
        val closed = stack.removeAt(stack.lastIndex)
        if (closed == GROUP_POSITION_ELEMENT) state.endGroupPosition()
        if (closed == SERIES_ELEMENT) state.finishSeries()
    }

    private fun parseContainer(bytes: ByteArray, checkActive: () -> Unit): String {
        val text = decodeXml(bytes, checkActive)
        val parser = newXmlParser()
        parser.setInput(StringReader(text))
        val stack = mutableListOf<String>()
        var path: String? = null

        while (true) {
            checkActive()
            when (parser.next()) {
                XmlPullParser.START_TAG -> path = containerStartTag(parser, stack, path)

                XmlPullParser.END_DOCUMENT -> break
                XmlPullParser.END_TAG -> if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
            }
        }
        if (stack.isNotEmpty()) throw EpubFailure("container.xml: нарушена структура XML")
        return path ?: throw EpubFailure("В container.xml не указан full-path для OPF")
    }

    private fun parseOpf(
        bytes: ByteArray,
        opfPath: String,
        archive: SafeZip,
        checkActive: () -> Unit,
    ): ParseResult {
        val text = decodeXml(bytes, checkActive)
        val parser = newXmlParser()
        parser.setInput(StringReader(text))
        val state = EpubState()
        val stack = mutableListOf<String>()

        while (true) {
            checkActive()
            when (parser.next()) {
                XmlPullParser.START_TAG -> onStartTag(parser, state, stack)
                XmlPullParser.TEXT,
                XmlPullParser.CDSECT,
                XmlPullParser.ENTITY_REF -> appendTextNode(parser, state)

                XmlPullParser.END_TAG -> endTag(state = state, stack = stack)

                XmlPullParser.END_DOCUMENT -> break
            }
        }

        if (stack.isNotEmpty()) throw EpubFailure("OPF: нарушена структура XML")
        val metadata = state.toMetadata()
            ?: return ParseResult.Failed("В OPF не найден dc:title")
        val cover = state.findCover(opfPath, archive, checkActive)
        val warning = state.namespaceWarning
        return if (warning == null) {
            ParseResult.Success(metadata, cover)
        } else {
            ParseResult.Partial(metadata, warning)
        }
    }

    private fun onStartTag(parser: XmlPullParser, state: EpubState, stack: MutableList<String>) {
        val name = localName(parser.name)
        if (stack.isEmpty()) {
            if (name != PACKAGE_ROOT) throw EpubFailure("Ожидался корень package")
            val namespace = parser.namespace
            if (namespace != null && namespace != OPF_NAMESPACE) {
                state.namespaceWarning = "OPF namespace не соответствует EPUB 3"
            }
        }
        stack.add(name)

        when {
            name in DC_FIELDS -> {
                val namespace = parser.namespace
                if (namespace != null && namespace != DC_NAMESPACE && namespace != OPF_NAMESPACE) {
                    state.namespaceWarning = "Некоторые DC-элементы имеют неизвестный namespace"
                }
                val scheme = attribute(parser, "scheme", OPF_NAMESPACE)
                    ?: attribute(parser, "scheme", null)
                state.addCapture(name, stack.size, onEnd = { capture ->
                    state.finishDc(
                        name,
                        capture.text.toString(),
                        scheme?.trim()?.equals("ISBN", ignoreCase = true) == true,
                    )
                })
            }

            name == META_ELEMENT -> handleMetaElement(parser = parser, state = state, stack = stack)

            name == SERIES_ELEMENT -> startSeriesIfRealSeries(parser, state)

            name == GROUP_POSITION_ELEMENT -> startGroupPosition(parser, state, stack)

            name == ITEM_ELEMENT -> state.readManifestItem(parser)
        }
    }

    private fun decodeXml(bytes: ByteArray, checkActive: () -> Unit): String {
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
        }
        return null
    }

    private fun localName(name: String?): String = name?.substringAfterLast(':').orEmpty()

    private fun spool(input: BookInput, checkActive: () -> Unit): SpooledInput {
        val file = File.createTempFile("bookshelf-epub-", ".tmp")
        var completed = false
        try {
            input.openStream().use { source ->
                FileOutputStream(file).use { target -> copy(source, target, checkActive) }
            }
            completed = true
            return SpooledInput(file)
        } finally {
            if (!completed) file.delete()
        }
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

    private data class SpooledInput(val file: File)

    private class EpubFailure(message: String, cause: Throwable? = null) : Exception(message, cause)

    private class SafeZip private constructor(
        private val zip: ZipFile,
        val entries: List<ZipEntry>,
    ) : Closeable {

        fun find(path: String): ZipEntry? = entries.firstOrNull { !it.isDirectory && it.name == path }
            ?: entries.firstOrNull { !it.isDirectory && it.name.equals(path, ignoreCase = true) }

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
         * Исключение из `finally` перекрывает исходную ошибку — именно поэтому `throw` здесь
         * больше нет. `close()` не приостанавливает выполнение, терять CancellationException нечего.
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
                    // Причина сохраняется: без неё видно только «архив повреждён», хотя настоящая
                    // ошибка (обрыв чтения, нет места, другой формат) остаётся в cause.
                    throw EpubFailure("EPUB: архив повреждён или не является ZIP", error)
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
                        throw EpubFailure("EPUB: архив превышает лимиты безопасности")
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

    private inner class EpubState {
        var title: String? = null
        val authors = mutableListOf<String>()
        var language: String? = null
        var description: String? = null
        var publisher: String? = null
        var isbn: String? = null
        var year: String? = null
        var namespaceWarning: String? = null
        var seriesId: String? = null
        var seriesName: String? = null
        var seriesIndex: Double? = null
        var calibreSeries: String? = null
        var calibreSeriesIndex: Double? = null
        var coverMetaId: String? = null
        var coverImageId: String? = null
        var coverImageHref: String? = null
        val manifest = linkedMapOf<String, ManifestItem>()
        val captures = mutableListOf<EpubCapture>()
        private val seriesPositions = mutableListOf<SeriesPosition>()
        private var activeSeries: SeriesValue? = null
        private var groupPositionDepth = -1

        fun addCapture(
            name: String,
            depth: Int,
            maxLength: Int = MAX_TEXT_CAPTURE,
            onEnd: (EpubCapture) -> Unit,
        ) {
            captures += EpubCapture(name, depth, maxLength, onEnd)
        }

        fun appendText(value: String) {
            captures.forEach { it.append(value) }
            if (groupPositionDepth < 0) activeSeries?.raw?.append(value)
        }

        fun finishDc(name: String, raw: String, isbnCandidate: Boolean) {
            val value = normalize(raw) ?: return
            when (name) {
                "creator" -> addCreator(value)
                "identifier" -> isbn = isbnOrExisting(isbn, value, isbnCandidate)
                else -> applyTextDc(name = name, value = value)
            }
        }

        /**
         * Текстовые поля `dc:*`: первое значение побеждает, повторы с тем же именем игнорируются.
         *
         * Вынесено из `finishDc` отдельной функцией: семь условий в одном `when` давали порог
         * `CyclomaticComplexMethod`, а правило «первое значение важнее» здесь читается целиком.
         */
        private fun applyTextDc(name: String, value: String) {
            when (name) {
                "title" -> if (title == null) title = value
                "language" -> if (language == null) language = value
                "description" -> if (description == null) description = value
                "publisher" -> if (publisher == null) publisher = value
                "date" -> if (year == null) year = value
            }
        }

        /** Авторы не дублируются: один и тот же `dc:creator` в OPF встречается не раз. */
        private fun addCreator(value: String) {
            if (value !in authors) authors += value
        }

        /**
         * ISBN из `dc:identifier`: берётся, только если прежнего нет и значение похоже на ISBN.
         *
         * `dc:identifier` в EPUB нередко содержит UUID или ссылку, поэтому принимается либо явная
         * пометка `ISBN` (в значении или в атрибуте `scheme`), либо строка, начинающаяся с «ISBN».
         *
         * Вынесено из `finishDc` отдельной функцией: условия в `when` давали порог
         * `CyclomaticComplexMethod`, а правило выбора ISBN читается отдельно.
         */
        private fun isbnOrExisting(known: String?, value: String, isbnCandidate: Boolean): String? {
            if (known != null) return known
            if (!isbnCandidate && !value.startsWith("ISBN", ignoreCase = true)) return null
            return value.removePrefix("ISBN").trim().takeIf { it.isNotEmpty() }
        }

        fun readMeta(name: String?, property: String?, content: String?) {
            val metaName = (name ?: property)?.trim()
            when {
                metaName?.equals(CALIBRE_SERIES, ignoreCase = true) == true ->
                    calibreSeries = normalize(content) ?: calibreSeries

                metaName?.equals(CALIBRE_SERIES_INDEX, ignoreCase = true) == true ->
                    calibreSeriesIndex = content?.toSeriesIndex() ?: calibreSeriesIndex

                metaName?.equals(META_SERIES_PROPERTY, ignoreCase = true) == true ->
                    seriesName = normalize(content) ?: seriesName

                metaName?.equals(META_GROUP_POSITION, ignoreCase = true) == true ->
                    finishGroupPosition(content.orEmpty(), null)

                metaName?.equals("cover", ignoreCase = true) == true ->
                    coverMetaId = content?.trim()?.takeIf { it.isNotEmpty() } ?: coverMetaId
            }
        }

        fun startMetaSeries(id: String?, depth: Int) {
            seriesId = id?.trim()?.removePrefix("#")?.takeIf { it.isNotEmpty() } ?: seriesId
            addCapture(META_SERIES_PROPERTY, depth, onEnd = { capture ->
                val value = normalize(capture.text.toString())
                if (value != null && seriesName == null) seriesName = value
            })
        }

        fun startGroupPosition() {
            groupPositionDepth = 1
        }

        fun endGroupPosition() {
            groupPositionDepth = -1
        }

        fun startSeries(id: String?) {
            activeSeries = SeriesValue()
            seriesId = id?.trim()?.removePrefix("#")?.takeIf { it.isNotEmpty() } ?: seriesId
        }

        fun finishGroupPosition(raw: String, refines: String?) {
            val value = raw.toSeriesIndex()
            if (value == null) {
                groupPositionDepth = -1
                return
            }
            val target = refines?.trim()?.removePrefix("#")
            seriesPositions += SeriesPosition(value, target)
            if (activeSeries != null) seriesIndex = value
            groupPositionDepth = -1
        }

        fun finishSeries() {
            val series = activeSeries ?: return
            val value = normalize(series.raw.toString())
            if (!value.isNullOrEmpty() && seriesName == null) seriesName = value
            activeSeries = null
        }

        fun readManifestItem(parser: XmlPullParser) {
            val id = attribute(parser, "id", null)?.trim().orEmpty()
            val href = attribute(parser, "href", null)?.trim().orEmpty()
            if (id.isEmpty() || href.isEmpty()) return
            val properties = attribute(parser, "properties", null)
                ?.split(WHITESPACE)
                ?.filter(String::isNotEmpty)
                .orEmpty()
            val item = ManifestItem(id, href, attribute(parser, "media-type", null), properties)
            manifest[id] = item
            if (properties.any { it.equals("cover-image", ignoreCase = true) }) {
                if (coverImageId == null) coverImageId = id
                if (coverImageHref == null) coverImageHref = href
            }
        }

        fun toMetadata(): BookMetadata? {
            val bookTitle = normalize(title) ?: return null
            val positions = seriesPositions
            val standardIndex = if (seriesName != null) {
                positions.firstOrNull { it.target == null && seriesId == null }?.value
                    ?: positions.firstOrNull { it.target == seriesId }?.value
                    ?: positions.firstOrNull()?.value
            } else {
                null
            }
            return BookMetadata(
                title = bookTitle,
                authors = authors,
                seriesName = normalize(seriesName) ?: normalize(calibreSeries),
                seriesIndex = seriesIndex ?: standardIndex ?: calibreSeriesIndex,
                language = normalize(language),
                publishedYear = year?.let(::yearFromText),
                publisher = normalize(publisher),
                isbn = normalize(isbn),
                description = normalize(description),
                source = MetadataOrigin.EMBEDDED,
            )
        }

        fun findCover(opfPath: String, archive: SafeZip, checkActive: () -> Unit): CoverBytes? {
            val item = coverManifestItem() ?: return null
            val path = resolvePath(opfPath, item.href) ?: return null
            val bytes = readCoverBytes(archive = archive, path = path, checkActive = checkActive)
                ?: return null
            val mime = item.mimeType ?: mimeFromPath(path)
            return CoverBytes(bytes, mime, null, null)
        }

        /**
         * Запись манифеста с обложкой: сначала по `properties="cover-image"`, затем по `meta name="cover"`.
         *
         * Вынесено из `findCover` отдельной функцией: цепочка `?:` давала заметный вклад в
         * `CyclomaticComplexMethod`, а порядок поиска читается отдельно.
         */
        private fun coverManifestItem(): ManifestItem? =
            coverImageId?.let { manifest[it] }
                ?: coverMetaId?.let { manifest[it] }
                ?: coverImageHref?.let { href -> ManifestItem("", href, null, emptyList()) }

        /**
         * Байты обложки из архива; битая картинка — не ошибка разбора.
         *
         * Обложка необязательна: причина отказа намеренно не используется (имя `_` это и означает),
         * а отмена пробрасывается дальше — иначе отменённый разбор выглядел бы завершённым.
         */
        private fun readCoverBytes(
            archive: SafeZip,
            path: String,
            checkActive: () -> Unit,
        ): ByteArray? {
            val entry = archive.find(path) ?: return null
            val bytes = try {
                archive.read(entry, MAX_COVER_BYTES, checkActive)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                null
            } ?: return null
            if (bytes.isEmpty() || bytes.size > MAX_COVER_BYTES) return null
            return bytes
        }

        private fun normalize(value: String?): String? =
            value?.replace(WHITESPACE, " ")?.trim()?.takeIf { it.isNotEmpty() }

        private fun yearFromText(value: String): Int? =
            YEAR.find(value)?.value?.toIntOrNull()

        private fun String.toSeriesIndex(): Double? =
            replace(',', '.').toDoubleOrNull()

        private fun mimeFromPath(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            else -> DEFAULT_COVER_MIME
        }
    }

    private class SeriesValue {
        val raw = StringBuilder()
    }

    private data class SeriesPosition(val value: Double, val target: String?)

    private data class ManifestItem(
        val id: String,
        val href: String,
        val mimeType: String?,
        val properties: List<String>,
    )

    private class EpubCapture(
        val name: String,
        val depth: Int,
        private val maxLength: Int,
        private val onEnd: (EpubCapture) -> Unit,
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

    private fun normalizePath(value: String): String? {
        val path = value.substringBefore('#').substringBefore('?').replace('\\', '/').removePrefix("/")
        val segments = path.split('/')
        if (segments.isEmpty() || segments.any { it == ".." }) return null
        val normalized = segments.filter { it.isNotEmpty() && it != "." }.joinToString("/")
        return normalized.takeIf { it.isNotEmpty() }
    }

    private fun resolvePath(opfPath: String, href: String): String? {
        val cleanHref = href.substringBefore('#').substringBefore('?').replace('\\', '/')
        if (cleanHref.startsWith("/")) return normalizePath(cleanHref)
        val segments = mutableListOf<String>()
        if (opfPath.contains('/')) segments += opfPath.substringBeforeLast('/').split('/')
        cleanHref.split('/').forEach { part ->
            when (part) {
                "", "." -> Unit
                ".." -> if (segments.isEmpty()) return null else segments.removeAt(segments.lastIndex)
                else -> segments += part
            }
        }
        return segments.joinToString("/").takeIf { it.isNotEmpty() }
    }

    private companion object {
        const val ID = "epub"
        const val SPRINT = 3
        const val CONTAINER_PATH = "META-INF/container.xml"
        const val CONTAINER_ROOT = "container"
        const val ROOTFILE_ELEMENT = "rootfile"
        const val PACKAGE_ROOT = "package"
        const val META_ELEMENT = "meta"
        const val ITEM_ELEMENT = "item"
        const val SERIES_ELEMENT = "belongs-to-collection"
        const val GROUP_POSITION_ELEMENT = "group-position"
        const val CONTAINER_NAMESPACE = "urn:oasis:names:tc:opendocument:xmlns:container"
        const val OPF_NAMESPACE = "http://www.idpf.org/2007/opf"
        const val DC_NAMESPACE = "http://purl.org/dc/elements/1.1/"
        const val WINDOWS_1251 = "windows-1251"
        const val DEFAULT_COVER_MIME = "application/octet-stream"
        const val CALIBRE_SERIES = "calibre:series"
        const val CALIBRE_SERIES_INDEX = "calibre:series_index"
        const val META_SERIES_PROPERTY = "belongs-to-collection"
        const val META_GROUP_POSITION = "group-position"
        const val MAX_COVER_BYTES = 2 * 1_024 * 1_024
        const val MAX_XML_BYTES = 16 * 1_024 * 1_024
        const val CHARSET_PREFIX_SIZE = 4_096
        const val MAX_TEXT_CAPTURE = 4 * 1_024 * 1_024

        /** Минимальный размер буфера при чтении записи архива. */
        const val INITIAL_BUFFER_SIZE = 32
        val EXTENSIONS = setOf("epub")
        val MIME_TYPES = setOf("application/epub+zip")
        val DC_FIELDS = setOf(
            "title",
            "creator",
            "language",
            "description",
            "publisher",
            "identifier",
            "date",
        )
        val WHITESPACE = Regex("\\s+")
        val YEAR = Regex("\\d{4}")
    }


    /**
     * Элемент `<meta>` в OPF: серия Calibre, позиция в группе или обычное поле.
     *
     * Вынесено из `onStartTag` отдельной функцией: та перевалила за предел длины detekt, а разбор
     * `<meta>` — цельный кусок со своими тремя ветками.
     */
    /**
     * Начало `<meta property="belongs-to-collection">`: серию начинаем только для `collection-type="series"`.
     *
     * Вынесено из `onStartTag` отдельной функцией: ветки того `when` давали порог
     * `CyclomaticComplexMethod`, а условия EPUB-разметки читаются отдельно от цикла разбора.
     */
    private fun startSeriesIfRealSeries(parser: XmlPullParser, state: EpubState) {
        val collectionType = attribute(parser, "collection-type", null)
        if (collectionType?.trim()?.equals("series", ignoreCase = true) == true) {
            state.startSeries(attribute(parser, "id", null))
        }
    }

    /** Начало `<meta property="group-position">`: значение собирается до закрывающего тега. */
    private fun startGroupPosition(
        parser: XmlPullParser,
        state: EpubState,
        stack: MutableList<String>,
    ) {
        val refines = attribute(parser, "refines", null)
        state.addCapture(META_ELEMENT, stack.size, onEnd = { capture ->
            state.finishGroupPosition(capture.text.toString(), refines)
        })
        state.startGroupPosition()
    }

    private fun handleMetaElement(parser: XmlPullParser, state: EpubState, stack: MutableList<String>) {
                val metaName = attribute(parser, "name", null)
                val property = attribute(parser, "property", null)?.trim()
                val content = attribute(parser, "content", null)
                when {
                    property?.equals(META_SERIES_PROPERTY, ignoreCase = true) == true && content == null -> {
                        state.startMetaSeries(attribute(parser, "id", null), stack.size)
                    }

                    property?.equals(META_GROUP_POSITION, ignoreCase = true) == true -> {
                        val refines = attribute(parser, "refines", null)
                        if (content == null) {
                            state.addCapture(META_ELEMENT, stack.size, onEnd = { capture ->
                                state.finishGroupPosition(capture.text.toString(), refines)
                            })
                            state.startGroupPosition()
                        } else {
                            state.finishGroupPosition(content, refines)
                        }
                    }

                    else -> state.readMeta(metaName, property, content)
                }
        
    }
}
