package io.github.bookshelf.core.metadata.parser.impl

import android.util.Xml
import io.github.bookshelf.core.metadata.model.BookMetadata
import io.github.bookshelf.core.metadata.model.MetadataOrigin
import io.github.bookshelf.core.metadata.parser.BookInput
import io.github.bookshelf.core.metadata.parser.BookMetadataParser
import io.github.bookshelf.core.metadata.parser.ParseFailureKind
import io.github.bookshelf.core.metadata.parser.ParseResult
import io.github.bookshelf.core.metadata.zip.ZipEntries
import io.github.bookshelf.core.metadata.zip.ZipLimitExceeded
import io.github.bookshelf.core.metadata.zip.ZipLimits
import io.github.bookshelf.core.metadata.zip.decodeXmlText
import kotlin.coroutines.cancellation.CancellationException
import org.xmlpull.v1.XmlPullParser

/**
 * DOCX — ZIP с `docProps/core.xml` («Форматы» §7).
 *
 * `dc:title`, `dc:creator`, `dc:language`, `dcterms:created` (из него — год). Apache POI не нужен:
 * хватает потокового чтения ZIP и `XmlPullParser`. Обложки у DOCX обычно нет: картинка обложки
 * лежит в `word/media`, но её роль ничем не объявлена, и угадывать нечего.
 */
class DocxParser(private val limits: ZipLimits = ZipLimits.DEFAULT) : BookMetadataParser {

    override val id: String = ID

    override val sprint: Int = SPRINT

    override fun supports(extension: String, mimeType: String?): Boolean =
        extension.trim().lowercase().removePrefix(".") in EXTENSIONS ||
            (mimeType != null && mimeType.lowercase() in MIME_TYPES)

    override suspend fun parse(input: BookInput): ParseResult {
        return try {
            val xml = ZipEntries(input, limits).readEntry(CORE_XML, MAX_CORE_XML_BYTES)
            val metadata = xml?.let { readCoreProperties(it) }
            when {
                metadata != null -> ParseResult.Success(metadata, cover = null)

                // `core.xml` нет — файл всё равно открывается: отдаём имя файла и помечаем частичный разбор.
                else -> ParseResult.Partial(
                    metadata = BookMetadata(
                        title = titleFromFileName(input.fileName),
                        source = MetadataOrigin.FILENAME,
                    ),
                    reason = "DOCX: не найден $CORE_XML",
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (limit: ZipLimitExceeded) {
            ParseResult.Failed(
                reason = "DOCX: ${limit.message}",
                kind = ParseFailureKind.ARCHIVE_LIMIT,
            )
        } catch (error: Throwable) {
            ParseResult.Failed(
                reason = "DOCX: ${error.message ?: "не удалось прочитать файл"}",
                kind = ParseFailureKind.CORRUPT_FILE,
            )
        }
    }

    /** Поля Dublin Core в `cp:coreProperties`; неизвестные теги пропускаются. */
    private fun readCoreProperties(bytes: ByteArray): BookMetadata? {
        val fields = readFields(bytes)
        val title = fields[TITLE].orEmpty().ifBlank { null } ?: return null
        return BookMetadata(
            title = title,
            authors = listOfNotNull(fields[CREATOR].orEmpty().ifBlank { null }),
            language = fields[LANGUAGE].orEmpty().ifBlank { null },
            publishedYear = fields[CREATED]?.let(::yearOf),
            source = MetadataOrigin.EMBEDDED,
        )
    }

    /** Год из `dcterms:created` (`1869-01-01T00:00:00Z`): в core.xml отдельного поля года нет. */
    private fun yearOf(value: String): Int? = value.trim().take(YEAR_DIGITS).toIntOrNull()

    private fun readFields(bytes: ByteArray): Map<String, String> {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
            setInput(decodeXmlText(bytes, charsetDetector).reader())
        }
        val fields = mutableMapOf<String, String>()
        var current: String? = null
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> current = localName(parser.name)
                XmlPullParser.TEXT -> {
                    val name = current
                    if (name != null && parser.text.isNotBlank()) {
                        fields.putIfAbsent(name, parser.text.trim())
                    }
                }
                XmlPullParser.END_TAG -> current = null
            }
            event = parser.next()
        }
        return fields
    }

    /** Имя без префикса пространства имён: `dc:title` → `title`. */
    private fun localName(name: String?): String = name?.substringAfterLast(':').orEmpty()

    private fun titleFromFileName(fileName: String): String =
        fileName.substringBeforeLast('.').trim().ifEmpty { fileName }

    private companion object {
        const val ID = "docx"
        const val SPRINT = 6
        const val CORE_XML = "docProps/core.xml"
        const val MAX_CORE_XML_BYTES = 1 * 1_024 * 1_024
        const val YEAR_DIGITS = 4
        val charsetDetector = io.github.bookshelf.core.metadata.encoding.DefaultCharsetDetector()

        const val TITLE = "title"
        const val CREATOR = "creator"
        const val LANGUAGE = "language"
        const val CREATED = "created"

        val EXTENSIONS = setOf("docx")
        val MIME_TYPES = setOf(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        )
    }
}
