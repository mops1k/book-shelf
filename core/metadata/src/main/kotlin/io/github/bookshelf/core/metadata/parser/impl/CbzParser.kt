package io.github.bookshelf.core.metadata.parser.impl

import io.github.bookshelf.core.metadata.encoding.CharsetDetector
import io.github.bookshelf.core.metadata.encoding.DefaultCharsetDetector
import io.github.bookshelf.core.metadata.parser.BookInput
import io.github.bookshelf.core.metadata.parser.BookMetadataParser
import io.github.bookshelf.core.metadata.parser.ParseResult
import io.github.bookshelf.core.metadata.zip.ZipEntries
import io.github.bookshelf.core.metadata.zip.ZipLimits

/**
 * CBZ — обычный ZIP с `ComicInfo.xml` (docs/formats.md §7).
 *
 * Правила разбора комикса общие с CBR и живут в [ComicArchiveParser]; здесь только архив.
 * ZIP читается потоком и дважды (метаданные и обложка), поэтому ни временная копия файла,
 * ни весь архив в памяти не нужны: страницы комикса бывают по несколько мегабайт каждая.
 */
class CbzParser(
    private val limits: ZipLimits = ZipLimits.DEFAULT,
    private val charsetDetector: CharsetDetector = DefaultCharsetDetector(),
) : BookMetadataParser {

    private val comic = ComicArchiveParser(format = FORMAT, charsetDetector = charsetDetector)

    override val id: String = ID

    override val sprint: Int = SPRINT

    override fun supports(extension: String, mimeType: String?): Boolean =
        extension.trim().lowercase().removePrefix(".") in EXTENSIONS ||
            (mimeType != null && mimeType.lowercase() in MIME_TYPES)

    override suspend fun parse(input: BookInput): ParseResult = comic.parse(input.fileName) {
        ComicArchiveEntries { action ->
            // ZIP читается потоком: содержимое записи доступно только внутри обхода,
            // поэтому функция открытия возвращает уже готовый поток.
            ZipEntries(input, limits).forEach { entry, stream ->
                action(ComicArchiveEntry(name = entry.name, sizeBytes = entry.size) { stream })
            }
        }
    }

    private companion object {
        const val ID = "cbz"
        const val SPRINT = 6
        const val FORMAT = "CBZ"
        val EXTENSIONS = setOf("cbz")
        val MIME_TYPES = setOf("application/vnd.comicbook+zip", "application/x-cbz")
    }
}
