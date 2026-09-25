package io.github.bookshelf.core.metadata.parser.impl

import io.github.bookshelf.core.metadata.parser.BookInput
import io.github.bookshelf.core.metadata.parser.BookMetadataParser
import io.github.bookshelf.core.metadata.parser.ParseResult

/**
 * CBZ — обычный ZIP с `ComicInfo.xml` (docs/formats.md §7).
 *
 * Поля схемы Anansi v2.0: `Series`, `Number`, `Title`, `Volume`, `Summary`, `Writer`,
 * `Publisher`, `Year/Month/Day`, `LanguageISO`, `PageCount`. Обложка — первая страница
 * в natural-sort порядке (`page2` < `page10`). Учитываем BOM и windows-1251 в `ComicInfo.xml`
 * и размещение файла в подпапке. Лимиты распаковки — см. [io.github.bookshelf.core.metadata.zip.ZipLimits].
 */
class CbzParser : BookMetadataParser {

    override val id: String = ID

    override val sprint: Int = SPRINT

    override fun supports(extension: String, mimeType: String?): Boolean =
        extension.trim().lowercase().removePrefix(".") in EXTENSIONS ||
            (mimeType != null && mimeType.lowercase() in MIME_TYPES)

    override suspend fun parse(input: BookInput): ParseResult {
        // STUB(Sprint 6): ZipFile + ComicInfo.xml v2.0, natural sort, первая страница как обложка.
        TODO("STUB(Sprint 6): разобрать CBZ (ComicInfo.xml, natural sort страниц, первая страница)")
    }

    private companion object {
        const val ID = "cbz"
        const val SPRINT = 6
        val EXTENSIONS = setOf("cbz")
        val MIME_TYPES = setOf("application/vnd.comicbook+zip", "application/x-cbz")
    }
}
