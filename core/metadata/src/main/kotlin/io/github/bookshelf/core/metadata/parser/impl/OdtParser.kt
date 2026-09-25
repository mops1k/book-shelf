package io.github.bookshelf.core.metadata.parser.impl

import io.github.bookshelf.core.metadata.parser.BookInput
import io.github.bookshelf.core.metadata.parser.BookMetadataParser
import io.github.bookshelf.core.metadata.parser.ParseResult

/**
 * ODT — ZIP с `meta.xml` (docs/formats.md §7).
 *
 * `dc:title`, `meta:initial-creator`, `dc:creator`, `dc:language`. `odfdom-java` заброшена,
 * поэтому разбор свой, через `ZipFile` и `XmlPullParser`. Обложки нет.
 */
class OdtParser : BookMetadataParser {

    override val id: String = ID

    override val sprint: Int = SPRINT

    override fun supports(extension: String, mimeType: String?): Boolean =
        extension.trim().lowercase().removePrefix(".") in EXTENSIONS ||
            (mimeType != null && mimeType.lowercase() in MIME_TYPES)

    override suspend fun parse(input: BookInput): ParseResult {
        // STUB(Sprint 6): ZipFile + meta.xml (dc:title, meta:initial-creator, dc:creator, dc:language).
        TODO("STUB(Sprint 6): разобрать ODT (meta.xml) без odfdom")
    }

    private companion object {
        const val ID = "odt"
        const val SPRINT = 6
        val EXTENSIONS = setOf("odt")
        val MIME_TYPES = setOf("application/vnd.oasis.opendocument.text")
    }
}
