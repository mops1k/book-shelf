package io.github.bookshelf.core.metadata.binary

/**
 * Вид формата, определённый по сигнатуре первых байт.
 *
 * ZIP-контейнер в таблице один: EPUB, FB2.ZIP, CBZ, DOCX и ODT различаются по расширению
 * и содержимому архива (`mimetype`, `META-INF/container.xml`, `[Content_Types].xml`,
 * `ComicInfo.xml`), поэтому [ZIP_CONTAINER] уточняется отдельно (docs/formats.md §2).
 */
enum class FormatHint {
    FB2,
    PDF,
    MOBI,
    RTF,
    CBR,
    DJVU,
    ZIP_CONTAINER,
}

/**
 * Определение формата по «магии» первых байт.
 *
 * Расширения в пользовательских библиотеках врут постоянно, поэтому сигнатура — самый
 * надёжный признак после расширения (docs/architecture.md §4.4, docs/formats.md §2).
 * Для FB2 сигнатура ищется после BOM и XML-пролога: реальные файлы начинаются с
 * `<?xml version="1.0" encoding="utf-8"?><FictionBook …>`.
 */
object MagicBytes {

    /** `null` — сигнатура неизвестна; последний шанс — TXT, решает реестр парсеров. */
    fun sniff(prefix: ByteArray): FormatHint? {
        if (prefix.isEmpty()) return null
        val text = String(prefix, Charsets.ISO_8859_1)
        return when {
            prefix.startsWith(PDF_MAGIC) -> FormatHint.PDF
            prefix.startsWith(ZIP_MAGIC) || prefix.startsWith(ZIP_EMPTY_MAGIC) -> FormatHint.ZIP_CONTAINER
            prefix.startsWith(RAR_MAGIC) -> FormatHint.CBR
            prefix.startsWith(DJVU_MAGIC) -> FormatHint.DJVU
            hasMobiSignature(text) -> FormatHint.MOBI
            looksLikeFb2(text) -> FormatHint.FB2
            text.startsWith(RTF_MAGIC) -> FormatHint.RTF
            else -> null
        }
    }

    /**
     * PalmDB-файлы MOBI/PalmDOC: подпись `BOOKMOBI`/`TEXtREAd` стоит по смещению 60,
     * но встречается и в начале — проверяем оба варианта в пределах первых 128 байт.
     */
    private fun hasMobiSignature(text: String): Boolean {
        val head = text.take(MOBI_SEARCH_LIMIT)
        val fromOffset = if (head.length >= MOBI_SIGNATURE_OFFSET) head.substring(MOBI_SIGNATURE_OFFSET) else head
        return fromOffset.startsWith(BOOKMOBI_MAGIC) ||
            fromOffset.startsWith(PALMDOC_MAGIC) ||
            head.contains(BOOKMOBI_MAGIC) ||
            head.contains(PALMDOC_MAGIC)
    }

    /** FB2 — XML, поэтому сигнатура идёт после BOM и XML-пролога. */
    private fun looksLikeFb2(text: String): Boolean {
        val withoutBom = text.trimStart().removePrefix(UTF8_BOM_TEXT)
        val withoutPrologue = if (withoutBom.startsWith(XML_PROLOGUE)) {
            withoutBom.substringAfter(PROLOGUE_END, missingDelimiterValue = withoutBom)
        } else {
            withoutBom
        }
        return withoutPrologue.trimStart().startsWith(FICTION_BOOK_MAGIC)
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (size < prefix.size) return false
        for (index in prefix.indices) if (this[index] != prefix[index]) return false
        return true
    }

    private val PDF_MAGIC = "%PDF-".toByteArray(Charsets.US_ASCII)
    private val ZIP_MAGIC = "PK\u0003\u0004".toByteArray(Charsets.ISO_8859_1)
    private val ZIP_EMPTY_MAGIC = "PK\u0005\u0006".toByteArray(Charsets.ISO_8859_1)
    private val RAR_MAGIC = "Rar!\u001A\u0007".toByteArray(Charsets.ISO_8859_1)
    private val DJVU_MAGIC = "AT&T".toByteArray(Charsets.US_ASCII)

    private const val BOOKMOBI_MAGIC = "BOOKMOBI"
    private const val PALMDOC_MAGIC = "TEXtREAd"
    private const val FICTION_BOOK_MAGIC = "<FictionBook"
    private const val RTF_MAGIC = "{\\rtf"
    private const val XML_PROLOGUE = "<?xml"
    private const val PROLOGUE_END = "?>"
    private const val UTF8_BOM_TEXT = "\u00EF\u00BB\u00BF"
    private const val MOBI_SIGNATURE_OFFSET = 60
    private const val MOBI_SEARCH_LIMIT = 128
}
