package io.github.bookshelf.core.metadata.parser.impl

import io.github.bookshelf.core.metadata.parser.BookInput
import io.github.bookshelf.core.metadata.parser.BookMetadataParser
import io.github.bookshelf.core.metadata.parser.ParseResult

/**
 * FB2 / FB2.ZIP — свой разбор `XmlPullParser` (docs/formats.md §3).
 *
 * `description > title-info`: `book-title`, `author`, `annotation`, `sequence name number`,
 * `lang`, `genre`; `publish-info`: `isbn`, `year`, `publisher`. Обложка —
 * `coverpage > image l:href="#id"` + соответствующий base64 `<binary>`.
 *
 * Кодировки — главный источник багов: BOM → XML-пролог → эвристики → фолбэк windows-1251
 * (см. [io.github.bookshelf.core.metadata.encoding.CharsetDetector]).
 */
class Fb2Parser : BookMetadataParser {

    override val id: String = ID

    override val sprint: Int = SPRINT

    override fun supports(extension: String, mimeType: String?): Boolean =
        extension.trim().lowercase().removePrefix(".") in EXTENSIONS ||
            (mimeType != null && mimeType.lowercase() in MIME_TYPES)

    override suspend fun parse(input: BookInput): ParseResult {
        // STUB(Sprint 3): XmlPullParser: title-info/publish-info, coverpage+binary, кодировки.
        TODO("STUB(Sprint 3): разобрать FB2/FB2.ZIP через XmlPullParser (title-info, coverpage, base64)")
    }

    private companion object {
        const val ID = "fb2"
        const val SPRINT = 3
        val EXTENSIONS = setOf("fb2", "fb2.zip")
        val MIME_TYPES = setOf("application/fb2", "application/x-fictionbook+xml")
    }
}
