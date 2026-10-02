package io.github.bookshelf.core.metadata.parser.impl

import android.util.Xml
import io.github.bookshelf.core.metadata.encoding.DefaultCharsetDetector
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
 * ODT — ZIP с `meta.xml` (docs/formats.md §7).
 *
 * `dc:title`, `dc:creator` (или `meta:initial-creator`), `dc:language` и число страниц из
 * `meta:document-statistic`. `odfdom-java` заброшена, поэтому разбор свой: потоковое чтение ZIP
 * и `XmlPullParser`. Обложки в ODT нет.
 */
class OdtParser(private val limits: ZipLimits = ZipLimits.DEFAULT) : BookMetadataParser {

    override val id: String = ID

    override val sprint: Int = SPRINT

    override fun supports(extension: String, mimeType: String?): Boolean =
        extension.trim().lowercase().removePrefix(".") in EXTENSIONS ||
            (mimeType != null && mimeType.lowercase() in MIME_TYPES)

    override suspend fun parse(input: BookInput): ParseResult {
        return try {
            val xml = ZipEntries(input, limits).readEntry(META_XML, MAX_META_XML_BYTES)
            val metadata = xml?.let { readMeta(it) }
            when {
                metadata != null -> ParseResult.Success(metadata, cover = null)

                else -> ParseResult.Partial(
                    metadata = BookMetadata(
                        title = titleFromFileName(input.fileName),
                        source = MetadataOrigin.FILENAME,
                    ),
                    reason = "ODT: не найден $META_XML",
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (limit: ZipLimitExceeded) {
            ParseResult.Failed(
                reason = "ODT: ${limit.message}",
                kind = ParseFailureKind.ARCHIVE_LIMIT,
            )
        } catch (error: Throwable) {
            ParseResult.Failed(
                reason = "ODT: ${error.message ?: "не удалось прочитать файл"}",
                kind = ParseFailureKind.CORRUPT_FILE,
            )
        }
    }

    /** Поля `office:meta`; число страниц приходит атрибутом `meta:page-count` у статистики. */
    private fun readMeta(bytes: ByteArray): BookMetadata? {
        val parsed = readMetaFields(bytes)
        val title = parsed.fields[TITLE].orEmpty().ifBlank { null } ?: return null
        return BookMetadata(
            title = title,
            // `dc:creator` — автор, `meta:initial-creator` — тот, кто создал файл: второе годится
            // только как запасной вариант, когда автор не указан.
            authors = listOfNotNull(
                parsed.fields[CREATOR].orEmpty().ifBlank { null }
                    ?: parsed.fields[INITIAL_CREATOR].orEmpty().ifBlank { null },
            ),
            language = parsed.fields[LANGUAGE].orEmpty().ifBlank { null },
            pageCount = parsed.pageCount?.takeIf { it > 0 },
            source = MetadataOrigin.EMBEDDED,
        )
    }

    private fun readMetaFields(bytes: ByteArray): MetaFields {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
            setInput(decodeXmlText(bytes, charsetDetector).reader())
        }
        val fields = mutableMapOf<String, String>()
        var pageCount: Int? = null
        var current: String? = null
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val name = localPart(parser.name)
                    current = name
                    if (name == STATISTIC) pageCount = pageCountOf(parser)
                }
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
        return MetaFields(fields = fields, pageCount = pageCount)
    }

    /** `meta:page-count` у `meta:document-statistic`; другого места у числа страниц в ODF нет. */
    private fun pageCountOf(parser: XmlPullParser): Int? {
        for (index in 0 until parser.attributeCount) {
            if (parser.getAttributeName(index) == PAGE_COUNT) {
                return parser.getAttributeValue(index)?.toIntOrNull()
            }
        }
        return null
    }

    /** Имя без префикса пространства имён: `dc:title` → `title`. */
    private fun localPart(name: String?): String = name?.substringAfterLast(':').orEmpty()

    private fun titleFromFileName(fileName: String): String =
        fileName.substringBeforeLast('.').trim().ifEmpty { fileName }

    private data class MetaFields(val fields: Map<String, String>, val pageCount: Int?)

    private companion object {
        const val ID = "odt"
        const val SPRINT = 6
        const val META_XML = "meta.xml"
        const val MAX_META_XML_BYTES = 1 * 1_024 * 1_024
        const val TITLE = "title"
        const val CREATOR = "creator"
        const val INITIAL_CREATOR = "initial-creator"
        const val LANGUAGE = "language"
        const val STATISTIC = "document-statistic"
        const val PAGE_COUNT = "page-count"
        val charsetDetector = DefaultCharsetDetector()

        val EXTENSIONS = setOf("odt")
        val MIME_TYPES = setOf(
            "application/vnd.oasis.opendocument.text",
            "application/x-vnd.oasis.opendocument.text",
        )
    }
}
