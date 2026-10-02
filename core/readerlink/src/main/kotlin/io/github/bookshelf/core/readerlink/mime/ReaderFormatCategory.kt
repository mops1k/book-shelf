package io.github.bookshelf.core.readerlink.mime

/**
 * Категория формата на экране «Читалки».
 *
 * Экран сначала спрашивает, о чём речь — о книгах, документах, манге или о прочем, и только потом
 * показывает форматы (Александр, 01.10.2026). Порядок объявления — порядок на экране.
 */
enum class ReaderFormatCategory(val extensionLabel: String) {

    /** Художественное: FB2, EPUB, MOBI, AZW3, PRC. */
    BOOKS("Книги"),

    /** Документы: PDF, TXT, DOCX, ODT, RTF. */
    DOCUMENTS("Документы"),

    /** Манга и комиксы: CBZ, CBR — ради них и нужен выбор читалки по формату. */
    MANGA("Манги"),

    /** Всё остальное, включая сканы DJVU и форматы, которых мы ещё не знаем. */
    OTHER("Другое"),
}

/** Категория формата по расширению. Регистр и ведущая точка не важны, неизвестное — [OTHER]. */
fun formatCategoryOf(extension: String): ReaderFormatCategory {
    val normalized = extension.trim().removePrefix(".").lowercase()
    return when (normalized) {
        "fb2", "fb2.zip", "epub", "mobi", "azw3", "prc" -> ReaderFormatCategory.BOOKS
        "pdf", "txt", "docx", "odt", "rtf" -> ReaderFormatCategory.DOCUMENTS
        "cbz", "cbr" -> ReaderFormatCategory.MANGA
        else -> ReaderFormatCategory.OTHER
    }
}
