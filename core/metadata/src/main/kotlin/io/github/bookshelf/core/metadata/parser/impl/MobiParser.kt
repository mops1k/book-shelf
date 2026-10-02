package io.github.bookshelf.core.metadata.parser.impl

import io.github.bookshelf.core.metadata.model.BookMetadata
import io.github.bookshelf.core.metadata.model.CoverBytes
import io.github.bookshelf.core.metadata.model.MetadataOrigin
import io.github.bookshelf.core.metadata.parser.BookInput
import io.github.bookshelf.core.metadata.parser.BookMetadataParser
import io.github.bookshelf.core.metadata.parser.ParseFailureKind
import io.github.bookshelf.core.metadata.parser.ParseResult
import java.nio.charset.Charset
import kotlin.coroutines.cancellation.CancellationException

/**
 * MOBI / AZW3 / PRC — свой низкоуровневый парсер (docs/formats.md §6).
 *
 * Palm DB → PalmDOC-заголовок → MOBI-заголовок → записи EXTH. Теги: `100` author, `101` publisher,
 * `103` description, `104` ISBN, `105` subject, `106` date, `201` coveroffset, `503` обновлённый
 * заголовок, `524` язык; обложка — запись `first image index + coveroffset`.
 *
 * Файл НЕ читается целиком и временная копия не нужна: используются `BookInput.bytes` (чтение
 * диапазона) — из файла берутся PDB-заголовок, список записей, запись 0 и запись обложки.
 *
 * DRM метаданным не мешает: шифрование применяется к тексту, а заголовок, EXTH и картинки
 * читаются. Контент не расшифровываем (docs/decisions.md).
 */
class MobiParser : BookMetadataParser {

    override val id: String = ID

    override val sprint: Int = SPRINT

    override fun supports(extension: String, mimeType: String?): Boolean =
        extension.trim().lowercase().removePrefix(".") in EXTENSIONS ||
            (mimeType != null && mimeType.lowercase() in MIME_TYPES)

    override suspend fun parse(input: BookInput): ParseResult {
        return try {
            val first = input.bytes(0L, PDB_HEADER)
            if (first.isEmpty()) {
                return ParseResult.Failed("MOBI: файл пуст", ParseFailureKind.EMPTY_FILE)
            }
            val palm = readPalmHeader(input = input, first = first)
                ?: return ParseResult.Failed(
                    reason = "MOBI: нет метки $BOOK_TYPE$MOBI_CREATOR",
                    kind = ParseFailureKind.CORRUPT_FILE,
                )
            val record0 = readRecord0(input = input, palm = palm) ?: ByteArray(0)
            val mobi = readMobiHeader(record0)
                ?: return ParseResult.Failed(
                    reason = "MOBI: в записи 0 нет заголовка MOBI",
                    kind = ParseFailureKind.CORRUPT_FILE,
                )
            val exth = readExth(record0 = record0, mobi = mobi)
            val metadata = buildMetadata(record0 = record0, mobi = mobi, exth = exth)
            val cover = readCover(input = input, palm = palm, record0 = record0, exth = exth)
            if (metadata != null) {
                return ParseResult.Success(metadata, cover)
            }
            // Заголовка в файле не нашлось: книга всё равно попадёт в библиотеку с именем файла.
            ParseResult.Partial(
                metadata = BookMetadata(
                    title = titleFromFileName(input.fileName),
                    authors = authorsOf(exth, mobi.textEncoding),
                    source = MetadataOrigin.FILENAME,
                ),
                reason = "MOBI: заголовок не найден",
                cover = cover,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            ParseResult.Failed(
                reason = "MOBI: ${error.message ?: "не удалось разобрать файл"}",
                kind = ParseFailureKind.CORRUPT_FILE,
            )
        }
    }

    /** Заголовок Palm DB: проверка метки и список смещений записей. */
    private suspend fun readPalmHeader(input: BookInput, first: ByteArray): PalmHeader? {
        val type = String(first, PDB_TYPE_AT, BOOK_TYPE.length, Charsets.US_ASCII)
        val creator = String(first, PDB_CREATOR_AT, MOBI_CREATOR.length, Charsets.US_ASCII)
        if (type != BOOK_TYPE || creator != MOBI_CREATOR) return null
        val count = int16At(first, PDB_COUNT_AT) ?: return null
        if (count < MIN_RECORDS || count > MAX_RECORDS) return null
        val list = input.bytes(PDB_HEADER.toLong(), count * RECORD_INFO)
        val offsets = (0 until count).mapNotNull { index -> longAt(list, index * RECORD_INFO) }
        return PalmHeader(offsets = offsets)
    }

    /** Запись 0 целиком (в разумных пределах): в ней PalmDOC, MOBI, EXTH и имя книги. */
    private suspend fun readRecord0(input: BookInput, palm: PalmHeader): ByteArray? {
        val offset = palm.offsets.firstOrNull() ?: return null
        val next = palm.offsets.getOrNull(1)
        val size = if (next != null) next - offset else MAX_HEADER_RECORD.toLong()
        return input.bytes(offset, size.coerceIn(1L, MAX_HEADER_RECORD.toLong()).toInt())
    }

    private fun readMobiHeader(record0: ByteArray): MobiHeader? {
        if (record0.size < MOBI_HEADER_FLOOR) return null
        if (String(record0, MOBI_MAGIC_AT, MOBI_MAGIC.length, Charsets.US_ASCII) != MOBI_MAGIC) return null
        val headerLength = int32At(record0, MOBI_LENGTH_AT) ?: return null
        val exthFlag = int32At(record0, MOBI_EXTH_FLAG_AT) ?: 0
        return MobiHeader(
            textEncoding = int32At(record0, MOBI_ENCODING_AT) ?: CODE_PAGE_1252,
            nameOffset = int32At(record0, MOBI_NAME_OFFSET_AT) ?: 0,
            nameLength = int32At(record0, MOBI_NAME_LENGTH_AT) ?: 0,
            exthOffset = if (exthFlag and EXTH_FLAG != 0) MOBI_MAGIC_AT + headerLength else null,
        )
    }

    /** Записи EXTH: тип → данные (у одного типа может быть несколько значений). */
    private fun readExth(record0: ByteArray, mobi: MobiHeader): Map<Int, List<ByteArray>> {
        val start = mobi.exthOffset ?: return emptyMap()
        if (start + EXTH_HEADER > record0.size) return emptyMap()
        if (String(record0, start, EXTH_MAGIC.length, Charsets.US_ASCII) != EXTH_MAGIC) return emptyMap()
        val count = int32At(record0, start + EXTH_COUNT_AT) ?: return emptyMap()
        val found = mutableMapOf<Int, MutableList<ByteArray>>()
        var at = start + EXTH_HEADER
        var index = 0
        while (index < count && at + EXTH_RECORD_HEADER <= record0.size) {
            val type = int32At(record0, at)
            val length = int32At(record0, at + EXTH_LENGTH_AT)
            val end = if (length == null) record0.size else at + length
            if (type == null || end > record0.size || end < at + EXTH_RECORD_HEADER) break
            found.getOrPut(type) { mutableListOf() } += record0.copyOfRange(at + EXTH_RECORD_HEADER, end)
            at = end
            index += 1
        }
        return found
    }

    private fun buildMetadata(
        record0: ByteArray,
        mobi: MobiHeader,
        exth: Map<Int, List<ByteArray>>,
    ): BookMetadata? {
        val charset = charsetOf(mobi.textEncoding)
        // Пересобранный заголовок (тег 503) точнее имени в заголовке книги: так Kindle правит
        // названия, полученные от издателя.
        val title = firstValue(exth, TAG_UPDATED_TITLE, charset)
            ?: nameOf(record0, mobi, charset)
            ?: return null
        return BookMetadata(
            title = title,
            authors = authorsOf(exth, mobi.textEncoding),
            language = firstValue(exth, TAG_LANGUAGE, charset),
            publishedYear = firstValue(exth, TAG_DATE, charset)?.take(YEAR_DIGITS)?.toIntOrNull(),
            publisher = firstValue(exth, TAG_PUBLISHER, charset),
            isbn = firstValue(exth, TAG_ISBN, charset),
            description = firstValue(exth, TAG_DESCRIPTION, charset),
            tags = valuesOf(exth, TAG_SUBJECT, charset),
            source = MetadataOrigin.EMBEDDED,
        )
    }

    /**
     * Обложка: запись `первая картинка + coveroffset`.
     *
     * Байты берутся по смещению из списка записей Palm DB, размер — до следующей записи.
     */
    private suspend fun readCover(
        input: BookInput,
        palm: PalmHeader,
        record0: ByteArray,
        exth: Map<Int, List<ByteArray>>,
    ): CoverBytes? {
        val coverOffset = int32Of(exth[TAG_COVER_OFFSET]?.firstOrNull()) ?: return null
        val textRecords = int16At(record0, PALMDOC_TEXT_RECORDS_AT) ?: return null
        val index = FIRST_CONTENT_RECORD + textRecords + coverOffset
        val offset = palm.offsets.getOrNull(index) ?: return null
        val next = palm.offsets.getOrNull(index + 1)
        val size = if (next != null) next - offset else MAX_COVER_BYTES.toLong()
        val bytes = input.bytes(offset, size.coerceIn(1L, MAX_COVER_BYTES.toLong()).toInt())
        if (bytes.isEmpty()) return null
        return CoverBytes(bytes = bytes, mimeType = mimeOfImage(bytes), width = null, height = null)
    }

    private fun authorsOf(exth: Map<Int, List<ByteArray>>, encoding: Int): List<String> =
        valuesOf(exth, TAG_AUTHOR, charsetOf(encoding))

    private fun valuesOf(exth: Map<Int, List<ByteArray>>, tag: Int, charset: Charset): List<String> =
        exth[tag].orEmpty().mapNotNull { bytes -> decode(bytes, charset) }

    private fun firstValue(exth: Map<Int, List<ByteArray>>, tag: Int, charset: Charset): String? =
        exth[tag]?.firstOrNull()?.let { bytes -> decode(bytes, charset) }

    /** Имя книги из заголовка MOBI: смещение и длина лежат в самой записи 0. */
    private fun nameOf(record0: ByteArray, mobi: MobiHeader, charset: Charset): String? {
        val from = mobi.nameOffset
        val to = from + mobi.nameLength
        if (from < 0 || to > record0.size || to <= from) return null
        return decode(record0.copyOfRange(from, to), charset)
    }

    /** Текст EXTH заканчивается нулём: пустые значения не превращаются в пустые строки. */
    private fun decode(bytes: ByteArray, charset: Charset): String? =
        String(bytes, charset).trimEnd('\u0000').trim().ifBlank { null }

    private fun int32Of(bytes: ByteArray?): Int? = bytes?.let { int32At(it, 0) }

    private fun charsetOf(encoding: Int): Charset {
        val name = if (encoding == CODE_PAGE_UTF8) UTF8_NAME else WINDOWS_1252
        return runCatching { Charset.forName(name) }.getOrDefault(Charset.forName(WINDOWS_1252))
    }

    /** Тип картинки по «магии» первых байт: расширения у записей архива нет. */
    private fun mimeOfImage(bytes: ByteArray): String = when {
        bytes.size >= JPEG_MAGIC_LENGTH && bytes[0] == JPEG_MAGIC_0 && bytes[1] == JPEG_MAGIC_1 -> "image/jpeg"
        bytes.size >= PNG_MAGIC.size && PNG_MAGIC.contentEquals(bytes.copyOf(PNG_MAGIC.size)) -> "image/png"
        bytes.size >= GIF_MAGIC.size && GIF_MAGIC.contentEquals(bytes.copyOf(GIF_MAGIC.size)) -> "image/gif"
        else -> "application/octet-stream"
    }

    private fun titleFromFileName(fileName: String): String =
        fileName.substringBeforeLast('.').trim().ifEmpty { fileName }

    private fun int16At(bytes: ByteArray, at: Int): Int? {
        if (at + INT16_BYTES > bytes.size) return null
        return ((bytes[at].toInt() and BYTE_MASK) shl SHIFT_8) or (bytes[at + 1].toInt() and BYTE_MASK)
    }

    private fun int32At(bytes: ByteArray, at: Int): Int? {
        if (at < 0 || at + INT32_BYTES > bytes.size) return null
        return ((bytes[at].toInt() and BYTE_MASK) shl SHIFT_24) or
            ((bytes[at + 1].toInt() and BYTE_MASK) shl SHIFT_16) or
            ((bytes[at + 2].toInt() and BYTE_MASK) shl SHIFT_8) or
            (bytes[at + INT32_LAST].toInt() and BYTE_MASK)
    }

    /** Смещение записи Palm DB: беззнаковое 32-битное, поэтому отрицательное значение отбрасываем. */
    private fun longAt(bytes: ByteArray, at: Int): Long? {
        val value = int32At(bytes, at) ?: return null
        return if (value < 0) null else value.toLong()
    }

    private data class PalmHeader(val offsets: List<Long>)

    private data class MobiHeader(
        val textEncoding: Int,
        val nameOffset: Int,
        val nameLength: Int,
        val exthOffset: Int?,
    )

    private companion object {
        const val ID = "mobi"
        const val SPRINT = 6
        const val PDB_HEADER = 78
        const val RECORD_INFO = 8
        const val PDB_TYPE_AT = 60
        const val PDB_CREATOR_AT = 64
        const val PDB_COUNT_AT = 76
        const val BOOK_TYPE = "BOOK"
        const val MOBI_CREATOR = "MOBI"
        const val MIN_RECORDS = 2
        const val MAX_RECORDS = 100_000
        const val PALMDOC_HEADER = 16
        const val PALMDOC_TEXT_RECORDS_AT = 8
        const val MOBI_MAGIC = "MOBI"
        const val MOBI_MAGIC_AT = PALMDOC_HEADER
        const val MOBI_LENGTH_AT = MOBI_MAGIC_AT + 4
        const val MOBI_ENCODING_AT = MOBI_MAGIC_AT + 12
        const val MOBI_NAME_OFFSET_AT = MOBI_MAGIC_AT + 84
        const val MOBI_NAME_LENGTH_AT = MOBI_MAGIC_AT + 88
        const val MOBI_EXTH_FLAG_AT = MOBI_MAGIC_AT + 128
        const val MOBI_HEADER_FLOOR = MOBI_EXTH_FLAG_AT + 4
        const val EXTH_MAGIC = "EXTH"
        const val EXTH_COUNT_AT = 8
        const val EXTH_HEADER = 12
        const val EXTH_RECORD_HEADER = 8
        const val EXTH_LENGTH_AT = 4
        const val EXTH_FLAG = 0x40
        const val MAX_HEADER_RECORD = 256 * 1_024
        const val MAX_COVER_BYTES = 8 * 1_024 * 1_024
        const val FIRST_CONTENT_RECORD = 1
        const val CODE_PAGE_1252 = 1252
        const val CODE_PAGE_UTF8 = 65_001
        const val UTF8_NAME = "UTF-8"
        const val WINDOWS_1252 = "windows-1252"
        const val YEAR_DIGITS = 4
        const val TAG_AUTHOR = 100
        const val TAG_PUBLISHER = 101
        const val TAG_DESCRIPTION = 103
        const val TAG_ISBN = 104
        const val TAG_SUBJECT = 105
        const val TAG_DATE = 106
        const val TAG_COVER_OFFSET = 201
        const val TAG_UPDATED_TITLE = 503
        const val TAG_LANGUAGE = 524
        const val INT16_BYTES = 2
        const val INT32_BYTES = 4
        const val INT32_LAST = 3
        const val JPEG_MAGIC_LENGTH = 3
        const val BYTE_MASK = 0xFF
        const val SHIFT_24 = 24
        const val SHIFT_16 = 16
        const val SHIFT_8 = 8
        val JPEG_MAGIC_0 = 0xFF.toByte()
        val JPEG_MAGIC_1 = 0xD8.toByte()
        val PNG_MAGIC = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        val GIF_MAGIC = "GIF8".toByteArray(Charsets.US_ASCII)

        val EXTENSIONS = setOf("mobi", "azw3", "prc")
        val MIME_TYPES = setOf("application/x-mobipocket-ebook", "application/vnd.amazon.ebook")
    }
}
