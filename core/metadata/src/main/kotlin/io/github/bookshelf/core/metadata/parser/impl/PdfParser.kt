package io.github.bookshelf.core.metadata.parser.impl

import io.github.bookshelf.core.metadata.parser.BookInput
import io.github.bookshelf.core.metadata.parser.BookMetadataParser
import io.github.bookshelf.core.metadata.parser.ParseResult

/**
 * PDF — метаданные из имени файла и XMP/Info, обложка через `PdfRenderer` (docs/formats.md §5).
 *
 * `pdfbox-android` (+3,25 МБ) сознательно не подключаем: сначала оцениваем качество разбора
 * XMP/Info вручную. Обложка — страница 0, отрендеренная в bitmap ~600 px с `inSampleSize`;
 * на защищённых паролем файлах `PdfRenderer` бросает `SecurityException` → `Partial` без обложки.
 * Большие PDF — риск OOM, поэтому bitmap ограничивается по памяти (docs/formats.md §9).
 */
class PdfParser : BookMetadataParser {

    override val id: String = ID

    override val sprint: Int = SPRINT

    override fun supports(extension: String, mimeType: String?): Boolean =
        extension.trim().lowercase().removePrefix(".") in EXTENSIONS ||
            (mimeType != null && mimeType.lowercase() in MIME_TYPES)

    override suspend fun parse(input: BookInput): ParseResult {
        // STUB(Sprint 3): XMP/Info вручную + PdfRenderer для страницы 0, SecurityException → Partial.
        TODO("STUB(Sprint 3): метаданные PDF из XMP/Info и обложка страницы 0 через PdfRenderer")
    }

    private companion object {
        const val ID = "pdf"
        const val SPRINT = 3
        val EXTENSIONS = setOf("pdf")
        val MIME_TYPES = setOf("application/pdf")
    }
}
