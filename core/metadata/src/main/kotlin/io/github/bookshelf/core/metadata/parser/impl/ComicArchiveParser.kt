package io.github.bookshelf.core.metadata.parser.impl

import android.util.Xml
import io.github.bookshelf.core.metadata.encoding.CharsetDetector
import io.github.bookshelf.core.metadata.encoding.DefaultCharsetDetector
import io.github.bookshelf.core.metadata.model.BookMetadata
import io.github.bookshelf.core.metadata.model.CoverBytes
import io.github.bookshelf.core.metadata.model.MetadataOrigin
import io.github.bookshelf.core.metadata.parser.ParseFailureKind
import io.github.bookshelf.core.metadata.parser.ParseResult
import io.github.bookshelf.core.metadata.rar.RarNotSeekableException
import io.github.bookshelf.core.metadata.zip.ZipLimitExceeded
import io.github.bookshelf.core.metadata.zip.decodeXmlText
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.coroutines.cancellation.CancellationException
import org.xmlpull.v1.XmlPullParser

/**
 * Одна запись архива комикса.
 *
 * Содержимое открывается ФУНКЦИЕЙ, а не приходит готовым потоком: у RAR распаковка записи
 * запускается при открытии потока (junrar делает это в фоновом потоке), и открывать её для
 * записей, которые никто не читает, нельзя — распаковки конкурируют за один канал, и запись
 * приходит пустой. Поток открывается не более одного раза.
 */
internal class ComicArchiveEntry(
    val name: String,
    val sizeBytes: Long,
    private val content: () -> InputStream,
) {
    fun open(): InputStream = content()
}

/** Проход по архиву комикса: записи в порядке архива. */
internal fun interface ComicArchiveEntries {

    fun forEach(action: (ComicArchiveEntry) -> Unit)
}

/** Запись архива есть, но прочитать её не удалось: это отказ, а не «метаданных нет». */
private class UnreadableArchiveEntry(message: String) : Exception(message)

/**
 * Общая часть разбора комиксов: CBZ и CBR («Форматы» §7).
 *
 * Читает поля `ComicInfo.xml` схемы Anansi v2.0 (`Series`, `Number`, `Title`, `Volume`, `Summary`,
 * `Writer`, `Publisher`, `Year`, `LanguageISO`, `PageCount`, `Genre`), делает обложкой первую
 * страницу в **естественном** порядке (`page2` раньше `page10`), учитывает BOM и windows-1251,
 * а также размещение файла в подпапке и обратный слэш в именах записей.
 *
 * Архив читается дважды: первый проход ищет `ComicInfo.xml` и имя первой страницы, второй — байты
 * этой страницы. Так не нужна ни временная копия, ни весь архив в памяти.
 *
 * @param format имя формата для текстов причин: `CBZ` или `CBR`.
 */
internal class ComicArchiveParser(
    private val format: String,
    private val charsetDetector: CharsetDetector = DefaultCharsetDetector(),
) {

    /**
     * @param fileName имя файла книги — источник заголовка, когда метаданных в архиве нет;
     * @param open свежий проход по архиву: проходов два, и каждый начинается сначала.
     */
    fun parse(fileName: String, open: () -> ComicArchiveEntries): ParseResult = try {
        val scan = scan(open())
        val metadata = scan.comicInfo?.let { readComicInfo(it) }
        val cover = scan.coverName?.let { name -> readCover(open(), name) }
        when {
            metadata != null -> ParseResult.Success(metadata, cover)

            // ComicInfo нет или он повреждён — это не ошибка: у большинства сборок комиксов
            // его просто не бывает. Заголовок берём из имени файла, а обложку всё равно отдаём.
            scan.coverName != null -> ParseResult.Partial(
                metadata = BookMetadata(
                    title = titleFromFileName(fileName),
                    pageCount = scan.pageCount.takeIf { it > 0 },
                    source = MetadataOrigin.FILENAME,
                ),
                reason = "$format: ComicInfo.xml не найден",
                cover = cover,
            )

            else -> ParseResult.Failed(
                reason = "$format: в архиве нет ни ComicInfo.xml, ни страниц",
                kind = ParseFailureKind.CORRUPT_FILE,
            )
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (limit: ZipLimitExceeded) {
        ParseResult.Failed(
            reason = "$format: ${limit.message}",
            kind = ParseFailureKind.ARCHIVE_LIMIT,
        )
    } catch (unreadable: UnreadableArchiveEntry) {
        ParseResult.Failed(
            reason = unreadable.message ?: "$format: запись архива не читается",
            kind = ParseFailureKind.CORRUPT_FILE,
        )
    } catch (_: RarNotSeekableException) {
        // Облачный провайдер отдаёт pipe, а RAR без произвольного чтения не распаковать.
        // Это не «битый файл»: книга остаётся в библиотеке с именем и без обложки.
        ParseResult.Partial(
            metadata = BookMetadata(
                title = titleFromFileName(fileName),
                source = MetadataOrigin.FILENAME,
            ),
            reason = "$format: файл недоступен для произвольного чтения",
        )
    } catch (error: Throwable) {
        ParseResult.Failed(
            reason = "$format: ${error.message ?: "не удалось прочитать архив"}",
            kind = ParseFailureKind.CORRUPT_FILE,
        )
    }

    /** Первый проход: `ComicInfo.xml`, имя первой страницы и число страниц. */
    private fun scan(entries: ComicArchiveEntries): ArchiveScan {
        val state = ScanState()
        entries.forEach { entry -> state.visit(entry) }
        return ArchiveScan(
            comicInfo = state.comicInfo,
            coverName = state.coverName,
            pageCount = state.pageCount,
        )
    }

    /** Второй проход: байты обложки; крупнее [MAX_COVER_BYTES] страница обрезается. */
    private fun readCover(entries: ComicArchiveEntries, coverName: String): CoverBytes? {
        var bytes: ByteArray? = null
        entries.forEach { entry ->
            if (bytes == null && entry.name == coverName) {
                bytes = readLimited(entry.open(), MAX_COVER_BYTES)
            }
        }
        return bytes?.takeIf { it.isNotEmpty() }?.let { data ->
            CoverBytes(bytes = data, mimeType = mimeOf(coverName), width = null, height = null)
        }
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

        fun visit(entry: ComicArchiveEntry) {
            if (isComicInfo(entry.name)) {
                val bytes = readLimited(entry.open(), MAX_COMIC_INFO_BYTES)
                // Запись объявлена непустой, но не прочиталась: молчать нельзя, иначе книга
                // получит «ComicInfo.xml не найден», хотя он в архиве есть.
                if (bytes.isEmpty() && entry.sizeBytes > 0) {
                    throw UnreadableArchiveEntry("$format: запись ComicInfo.xml не читается")
                }
                comicInfo = bytes
            } else if (isImage(entry.name)) {
                pageCount += 1
                val current = coverName
                // Обложка — первая страница в естественном порядке: имена страниц нумеруются
                // строками (`page2`, `page10`), и обычное сравнение поставило бы десятую раньше.
                if (current == null || naturalLess(entry.name, current)) coverName = entry.name
            }
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
        val out = ByteArrayOutputStream()
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

    internal companion object {
        const val BUFFER_SIZE = 16 * 1_024
        const val MAX_COMIC_INFO_BYTES = 1 * 1_024 * 1_024
        const val MAX_COVER_BYTES = 8 * 1_024 * 1_024
        private const val YEAR_DIGITS = 4

        val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "avif")

        /**
         * Имя записи без папки.
         *
         * Разделителем встречается и прямой, и обратный слэш: настоящие CBR собирают на Windows
         * (проверено на живом файле: «Amazing Man 022\Amazing Man 022 001.jpg»).
         */
        fun baseName(name: String): String = name.substringAfterLast('/').substringAfterLast('\\')

        fun isComicInfo(name: String): Boolean = baseName(name).equals("ComicInfo.xml", true)

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
