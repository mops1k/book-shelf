package io.github.bookshelf.core.metadata.parser.impl

import io.github.bookshelf.core.metadata.encoding.CharsetDetector
import io.github.bookshelf.core.metadata.encoding.DefaultCharsetDetector
import io.github.bookshelf.core.metadata.model.BookMetadata
import io.github.bookshelf.core.metadata.model.CoverBytes
import io.github.bookshelf.core.metadata.model.MetadataOrigin
import io.github.bookshelf.core.metadata.parser.BookInput
import io.github.bookshelf.core.metadata.parser.BookMetadataParser
import io.github.bookshelf.core.metadata.parser.ParseFailureKind
import io.github.bookshelf.core.metadata.parser.ParseResult
import io.github.bookshelf.core.metadata.zip.ZipEntries
import io.github.bookshelf.core.metadata.zip.ZipLimitExceeded
import io.github.bookshelf.core.metadata.zip.ZipLimits
import io.github.bookshelf.core.metadata.zip.decodeXmlText
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.coroutines.cancellation.CancellationException
import org.xmlpull.v1.XmlPullParser
import android.util.Xml

/**
 * CBZ — обычный ZIP с `ComicInfo.xml` (docs/formats.md §7).
 *
 * Поля схемы Anansi v2.0: `Series`, `Number`, `Title`, `Volume`, `Summary`, `Writer`,
 * `Publisher`, `Year/Month/Day`, `LanguageISO`, `PageCount`, `Genre`. Обложка — первая страница
 * в natural-sort порядке (`page2` < `page10`). Учитываем BOM и windows-1251 в `ComicInfo.xml`
 * и размещение файла в подпапке. Лимиты распаковки — см. [ZipLimits].
 *
 * Архив читается потоком и дважды: первый проход ищет `ComicInfo.xml` и имя первой страницы,
 * второй — байты этой страницы. Так не нужна ни временная копия файла, ни весь архив в памяти:
 * страницы комикса бывают по несколько мегабайт каждая.
 */
class CbzParser(
    private val limits: ZipLimits = ZipLimits.DEFAULT,
    private val charsetDetector: CharsetDetector = DefaultCharsetDetector(),
) : BookMetadataParser {

    override val id: String = ID

    override val sprint: Int = SPRINT

    override fun supports(extension: String, mimeType: String?): Boolean =
        extension.trim().lowercase().removePrefix(".") in EXTENSIONS ||
            (mimeType != null && mimeType.lowercase() in MIME_TYPES)

    override suspend fun parse(input: BookInput): ParseResult {
        return try {
            val scan = scan(input)
            val metadata = scan.comicInfo?.let { readComicInfo(it) }
            val cover = scan.coverName?.let { name -> readCover(input, name) }
            when {
                metadata != null -> ParseResult.Success(metadata, cover)

                // ComicInfo нет или он повреждён — это не ошибка: у большинства сборок комиксов
                // его просто не бывает. Заголовок берём из имени файла, а обложку всё равно отдаём.
                scan.coverName != null -> ParseResult.Partial(
                    metadata = BookMetadata(
                        title = titleFromFileName(input.fileName),
                        pageCount = scan.pageCount.takeIf { it > 0 },
                        source = MetadataOrigin.FILENAME,
                    ),
                    reason = "CBZ: ComicInfo.xml не найден",
                    cover = cover,
                )

                else -> ParseResult.Failed(
                    reason = "CBZ: в архиве нет ни ComicInfo.xml, ни страниц",
                    kind = ParseFailureKind.CORRUPT_FILE,
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (limit: ZipLimitExceeded) {
            ParseResult.Failed(
                reason = "CBZ: ${limit.message}",
                kind = ParseFailureKind.ARCHIVE_LIMIT,
            )
        } catch (error: Throwable) {
            ParseResult.Failed(
                reason = "CBZ: ${error.message ?: "не удалось прочитать архив"}",
                kind = ParseFailureKind.CORRUPT_FILE,
            )
        }
    }

    /** Первый проход: `ComicInfo.xml`, имя первой страницы и число страниц. */
    private fun scan(input: BookInput): ArchiveScan {
        val state = ScanState()
        ZipEntries(input, limits).forEach { entry, stream -> state.visit(entry, stream) }
        return ArchiveScan(
            comicInfo = state.comicInfo,
            coverName = state.coverName,
            pageCount = state.pageCount,
        )
    }

    /**
     * Накопители первого прохода: найденный `ComicInfo.xml`, имя обложки и число страниц.
     *
     * Отдельный класс, а не переменные в `scan`: разбор одной записи архива (`visit`) упирался
     * в порог `NestedBlockDepth`, а состояние прохода читается отдельно от самого цикла.
     */
    private inner class ScanState {
        var comicInfo: ByteArray? = null
        var coverName: String? = null
        var pageCount: Int = 0

        fun visit(entry: ZipEntry, stream: InputStream) {
            val name = entry.name
            if (isComicInfo(name)) {
                comicInfo = readLimited(stream, MAX_COMIC_INFO_BYTES)
            } else if (isImage(name)) {
                pageCount += 1
                val current = coverName
                // Обложка — первая страница в естественном порядке: имена страниц нумеруются
                // строками (`page2`, `page10`), и обычное сравнение поставило бы десятую раньше.
                if (current == null || naturalLess(name, current)) coverName = name
            }
        }
    }

    /** Второй проход: байты обложки; крупнее [MAX_COVER_BYTES] страница не берётся. */
    private fun readCover(input: BookInput, coverName: String): CoverBytes? {
        val bytes = ZipEntries(input, limits).readEntry(coverName, MAX_COVER_BYTES) ?: return null
        return bytes.takeIf { it.isNotEmpty() }?.let { data ->
            CoverBytes(bytes = data, mimeType = mimeOf(coverName), width = null, height = null)
        }
    }

    private fun readComicInfo(bytes: ByteArray): BookMetadata? {
        val text = decodeXmlText(bytes, charsetDetector)
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
            setInput(text.reader())
        }
        val fields = mutableMapOf<String, String>()
        var event = parser.eventType
        var current: String? = null
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> current = parser.name
                XmlPullParser.TEXT -> {
                    val name = current
                    if (name != null && parser.text.isNotBlank()) {
                        fields.putIfAbsent(name, parser.text.trim())
                    }
                }
                XmlPullParser.END_TAG -> current = null
            }
            event = parser.next()
        }
        val title = fields["Title"].orEmpty().ifBlank { null }
        val series = fields["Series"].orEmpty().ifBlank { null }
        if (title == null && series == null) return null
        return BookMetadata(
            // Заголовок выпуска у сборок часто пуст: тогда показываем серию с номером.
            title = title ?: listOfNotNull(series, fields["Number"]).joinToString(" № "),
            authors = listOfNotNull(fields["Writer"], fields["Penciller"])
                .flatMap { it.split(',') }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct(),
            seriesName = series,
            seriesIndex = fields["Number"]?.replace(',', '.')?.toDoubleOrNull(),
            language = fields["LanguageISO"].orEmpty().ifBlank { null },
            publishedYear = fields["Year"]?.take(YEAR_DIGITS)?.toIntOrNull(),
            publisher = fields["Publisher"].orEmpty().ifBlank { null },
            description = fields["Summary"].orEmpty().ifBlank { null },
            pageCount = fields["PageCount"]?.toIntOrNull(),
            tags = listOfNotNull(fields["Genre"].orEmpty().ifBlank { null }),
            source = MetadataOrigin.EMBEDDED,
        )
    }

    private fun readLimited(stream: InputStream, limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_SIZE)
        var total = 0
        while (total < limit) {
            val read = stream.read(buffer, 0, minOf(buffer.size, limit - total))
            if (read <= 0) break
            out.write(buffer, 0, read)
            total += read
        }
        return out.toByteArray()
    }

    private fun titleFromFileName(fileName: String): String =
        fileName.substringBeforeLast('.').trim().ifEmpty { fileName }

    private data class ArchiveScan(
        val comicInfo: ByteArray?,
        val coverName: String?,
        val pageCount: Int,
    )

    private companion object {
        const val ID = "cbz"
        const val SPRINT = 6
        const val BUFFER_SIZE = 16 * 1_024
        const val MAX_COMIC_INFO_BYTES = 1 * 1_024 * 1_024
        const val MAX_COVER_BYTES = 8 * 1_024 * 1_024
        const val WINDOWS_1251 = "windows-1251"
        const val YEAR_DIGITS = 4
        val EXTENSIONS = setOf("cbz")
        val MIME_TYPES = setOf("application/vnd.comicbook+zip", "application/x-cbz")
        val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "avif")

        fun isComicInfo(name: String): Boolean = name.substringAfterLast('/').equals("ComicInfo.xml", true)

        fun isImage(name: String): Boolean {
            val extension = name.substringAfterLast('.', "").lowercase()
            return extension in IMAGE_EXTENSIONS
        }

        fun mimeOf(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            "avif" -> "image/avif"
            else -> "application/octet-stream"
        }

        /**
         * Естественное сравнение имён страниц: цифровые группы сравниваются как числа.
         *
         * `page2` идёт раньше `page10` — обычная лексикографическая сортировка поставила бы
         * десятую страницу первой, и обложкой стала бы не первая страница.
         */
        fun naturalLess(left: String, right: String): Boolean {
            var i = 0
            var j = 0
            while (i < left.length && j < right.length) {
                val leftChar = left[i]
                val rightChar = right[j]
                if (leftChar.isDigit() && rightChar.isDigit()) {
                    val leftStart = i
                    val rightStart = j
                    while (i < left.length && left[i].isDigit()) i += 1
                    while (j < right.length && right[j].isDigit()) j += 1
                    val leftNumber = left.substring(leftStart, i).trimStart('0')
                    val rightNumber = right.substring(rightStart, j).trimStart('0')
                    val byLength = leftNumber.length.compareTo(rightNumber.length)
                    if (byLength != 0) return byLength < 0
                    val byValue = leftNumber.compareTo(rightNumber)
                    if (byValue != 0) return byValue < 0
                    continue
                }
                val byChar = leftChar.lowercaseChar().compareTo(rightChar.lowercaseChar())
                if (byChar != 0) return byChar < 0
                i += 1
                j += 1
            }
            return (left.length - i) < (right.length - j)
        }
    }
}
