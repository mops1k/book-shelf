package io.github.bookshelf.core.metadata.parser.impl

import io.github.bookshelf.core.metadata.parser.BookInput
import io.github.bookshelf.core.metadata.parser.BookMetadataParser
import io.github.bookshelf.core.metadata.parser.ParseResult

/**
 * MOBI / AZW3 / PRC — свой низкоуровневый парсер (docs/formats.md §6).
 *
 * Palm DB → PalmDOC-заголовок → MOBI-заголовок → записи EXTH. Теги: `100` author, `101` publisher,
 * `103` description, `104` ISBN, `105` subject, `106` date, `201` coveroffset (`202` thumboffset);
 * обложка — запись `first image index + coveroffset`. Нужен произвольный доступ
 * (`BookInput.openSeekable`), иначе — временная копия.
 *
 * DRM метаданным не мешает: шифрование применяется к тексту, а заголовок, EXTH и картинки
 * читаются. Контент не расшифровываем.
 */
class MobiParser : BookMetadataParser {

    override val id: String = ID

    override val sprint: Int = SPRINT

    override fun supports(extension: String, mimeType: String?): Boolean =
        extension.trim().lowercase().removePrefix(".") in EXTENSIONS ||
            (mimeType != null && mimeType.lowercase() in MIME_TYPES)

    override suspend fun parse(input: BookInput): ParseResult {
        // STUB(Sprint 6): PalmDB → PalmDOC → MOBI → EXTH, coveroffset, DRM-флаг не мешает.
        TODO("STUB(Sprint 6): разобрать MOBI/AZW3/PRC (PalmDOC → MOBI → EXTH, coveroffset)")
    }

    private companion object {
        const val ID = "mobi"
        const val SPRINT = 6
        val EXTENSIONS = setOf("mobi", "azw3", "prc")
        val MIME_TYPES = setOf("application/x-mobipocket-ebook", "application/vnd.amazon.ebook")
    }
}
