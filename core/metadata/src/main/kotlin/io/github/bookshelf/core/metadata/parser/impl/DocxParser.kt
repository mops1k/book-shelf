package io.github.bookshelf.core.metadata.parser.impl

import io.github.bookshelf.core.metadata.parser.BookInput
import io.github.bookshelf.core.metadata.parser.BookMetadataParser
import io.github.bookshelf.core.metadata.parser.ParseResult

/**
 * DOCX — ZIP с `docProps/core.xml` (docs/formats.md §7).
 *
 * `dc:title`, `dc:creator`, `dc:language`, `dcterms:created` + `app.xml`. Apache POI не нужен:
 * хватает `ZipFile` и `XmlPullParser` (~50 строк). Обложки у DOCX обычно нет.
 */
class DocxParser : BookMetadataParser {

    override val id: String = ID

    override val sprint: Int = SPRINT

    override fun supports(extension: String, mimeType: String?): Boolean =
        extension.trim().lowercase().removePrefix(".") in EXTENSIONS ||
            (mimeType != null && mimeType.lowercase() in MIME_TYPES)

    override suspend fun parse(input: BookInput): ParseResult {
        // STUB(Sprint 6): ZipFile + docProps/core.xml и app.xml, обложки нет.
        TODO("STUB(Sprint 6): разобрать DOCX (docProps/core.xml + app.xml) без Apache POI")
    }

    private companion object {
        const val ID = "docx"
        const val SPRINT = 6
        val EXTENSIONS = setOf("docx")
        val MIME_TYPES = setOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    }
}
