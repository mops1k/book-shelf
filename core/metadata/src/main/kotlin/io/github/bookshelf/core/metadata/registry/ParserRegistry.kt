package io.github.bookshelf.core.metadata.registry

import io.github.bookshelf.core.metadata.binary.FormatHint
import io.github.bookshelf.core.metadata.binary.MagicBytes
import io.github.bookshelf.core.metadata.parser.BookMetadataParser

/**
 * Выбор парсера для файла.
 *
 * Порядок: расширение → MIME → «магия» первых байт (docs/architecture.md §4.4).
 * Расширение даёт первого кандидата, но если сигнатура однозначно указывает на другой формат,
 * побеждает сигнатура: неверное расширение — частая ситуация в пользовательских библиотеках
 * (docs/formats.md §2, пример — PDF с расширением `.fb2`).
 */
interface ParserRegistry {

    /**
     * @param extension расширение в нижнем регистре; допускается составное (`fb2.zip`);
     * @param mimeType MIME от провайдера (часто `null` или неверный);
     * @param prefix первые байты файла для [MagicBytes.sniff]; пустой массив допустим.
     * @return парсер или `null`, если формат неизвестен либо парсера для него нет (DJVU).
     */
    fun select(extension: String, mimeType: String?, prefix: ByteArray): BookMetadataParser?

    /** Все зарегистрированные парсеры — для диагностики и тестов. */
    fun all(): List<BookMetadataParser>
}

/**
 * Реестр по умолчанию.
 *
 * @param parsers набор из DI; сортируется по [BookMetadataParser.id], потому что порядок
 *   `Set`-биндингов Dagger не гарантирует, а выбор должен быть воспроизводимым.
 */
class DefaultParserRegistry(
    parsers: Set<BookMetadataParser>,
) : ParserRegistry {

    private val parsers: List<BookMetadataParser> = parsers.sortedBy { it.id }

    override fun select(extension: String, mimeType: String?, prefix: ByteArray): BookMetadataParser? {
        val normalizedExtension = extension.trim().lowercase().removePrefix(".")
        val byExtension = this.parsers.firstOrNull { it.supports(normalizedExtension, null) }
        val hint = MagicBytes.sniff(prefix)

        // ZIP — один контейнер на несколько форматов, поэтому уточняется расширением;
        // разбор содержимого архива (mimetype, container.xml, ComicInfo) появится в Sprint 3.
        if (hint == FormatHint.ZIP_CONTAINER) {
            return byExtension?.takeIf { it.id in ZIP_CONTAINER_IDS }
                ?: selectByMime(mimeType)?.takeIf { it.id in ZIP_CONTAINER_IDS }
        }
        if (hint != null) {
            val byMagic = parsers.firstOrNull { it.id in hint.candidateIds() }
            if (byMagic != null) return byMagic
        }
        if (byExtension != null) return byExtension
        return selectByMime(mimeType)
    }

    override fun all(): List<BookMetadataParser> = parsers

    private fun selectByMime(mimeType: String?): BookMetadataParser? {
        if (mimeType.isNullOrBlank()) return null
        return parsers.firstOrNull { it.supports("", mimeType) }
    }

    /** Какие `id` парсеров соответствуют сигнатуре; для DJVU парсера нет осознанно. */
    private fun FormatHint.candidateIds(): Set<String> = when (this) {
        FormatHint.FB2 -> setOf(ID_FB2)
        FormatHint.PDF -> setOf(ID_PDF)
        FormatHint.MOBI -> setOf(ID_MOBI)
        FormatHint.RTF -> setOf(ID_RTF)
        FormatHint.CBR -> setOf(ID_CBR)
        FormatHint.DJVU -> emptySet()
        FormatHint.ZIP_CONTAINER -> ZIP_CONTAINER_IDS
    }

    private companion object {
        const val ID_FB2 = "fb2"
        const val ID_PDF = "pdf"
        const val ID_MOBI = "mobi"
        const val ID_RTF = "rtf"
        const val ID_CBR = "cbr"

        val ZIP_CONTAINER_IDS = setOf("fb2", "epub", "cbz", "docx", "odt")
    }
}
