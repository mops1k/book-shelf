package io.github.bookshelf.core.metadata.parser.impl

import io.github.bookshelf.core.metadata.parser.BookInput
import io.github.bookshelf.core.metadata.parser.BookMetadataParser
import io.github.bookshelf.core.metadata.parser.ParseResult

/**
 * EPUB 2 / 3 — свой разбор OPF (docs/formats.md §4).
 *
 * ZIP → `META-INF/container.xml` → путь к OPF → `manifest`/`spine`/`metadata`. Dublin Core:
 * `dc:title`, `dc:creator` (с `refines`/`file-as`), `dc:language`, `dc:date`, `dc:identifier`,
 * `dc:description`. Обложка: EPUB 3 `properties="cover-image"`, EPUB 2 `meta name="cover"`
 * (+ `guide type="cover"`); EPUB 3 приоритетнее. Серия: `belongs-to-collection` +
 * `collection-type="series"` + `group-position`, фолбэк — `calibre:series`/`calibre:series_index`.
 */
class EpubParser : BookMetadataParser {

    override val id: String = ID

    override val sprint: Int = SPRINT

    override fun supports(extension: String, mimeType: String?): Boolean =
        extension.trim().lowercase().removePrefix(".") in EXTENSIONS ||
            (mimeType != null && mimeType.lowercase() in MIME_TYPES)

    override suspend fun parse(input: BookInput): ParseResult {
        // STUB(Sprint 3): container.xml → OPF, DC-метаданные, обложка обоими способами, серия.
        TODO("STUB(Sprint 3): разобрать EPUB (container.xml → OPF, DC, cover-image/meta cover, серия)")
    }

    private companion object {
        const val ID = "epub"
        const val SPRINT = 3
        val EXTENSIONS = setOf("epub")
        val MIME_TYPES = setOf("application/epub+zip")
    }
}
