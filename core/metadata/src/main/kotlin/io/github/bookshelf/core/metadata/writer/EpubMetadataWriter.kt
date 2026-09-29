package io.github.bookshelf.core.metadata.writer

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Запись правки метаданных в EPUB (docs/formats.md §4).
 *
 * Меняется только OPF: остальные записи архива переносятся как есть (текст книги, обложка, стили),
 * а `mimetype` остаётся первой записью и **без сжатия** — этого требует спецификация EPUB, и без
 * этого читалки архив отвергают.
 *
 * Серия пишется в двух видах сразу: `calibre:series`/`calibre:series_index` (их читают Calibre,
 * KOReader и наш парсер) и EPUB3 `belongs-to-collection`/`group-position` (стандарт). Один вид
 * означал бы, что часть читалок серию не увидит.
 *
 * Кодировка OPF — UTF-8: так требует спецификация EPUB, в отличие от FB2 с его windows-1251.
 */
class EpubMetadataWriter {

    fun rewrite(bytes: ByteArray, edit: MetadataEdit): MetadataWriteResult {
        if (edit.isEmpty) return MetadataWriteResult.Rewritten(bytes)
        val spooled = spool(bytes)
        try {
            val archive = runCatching { ZipFile(spooled) }.getOrNull()
                ?: return MetadataWriteResult.NotSupported("Файл не является ZIP-архивом EPUB")
            return archive.use { zip ->
                val opfPath = opfPath(zip)
                    ?: return MetadataWriteResult.NotSupported("В EPUB нет META-INF/container.xml с OPF")
                val opfEntry = zip.getEntry(opfPath)
                    ?: return MetadataWriteResult.NotSupported("OPF из container.xml не найден: $opfPath")
                val opf = zip.getInputStream(opfEntry).use { it.readBytes() }
                    .toString(StandardCharsets.UTF_8)
                val rewritten = rewriteOpf(opf, edit)
                MetadataWriteResult.Rewritten(rebuild(zip, opfPath, rewritten.toByteArray(StandardCharsets.UTF_8)))
            }
        } finally {
            spooled.delete()
        }
    }

    /** Путь к OPF из `META-INF/container.xml`; `null`, если файла нет или путь не указан. */
    private fun opfPath(zip: ZipFile): String? {
        val container = zip.entries().asSequence().firstOrNull {
            it.name.equals(CONTAINER_PATH, ignoreCase = true)
        } ?: return null
        val text = zip.getInputStream(container).use { it.readBytes() }.toString(StandardCharsets.UTF_8)
        return FULL_PATH.find(text)?.groupValues?.get(1)
    }

    /**
     * Пересобрать `<metadata>`: заменить только те элементы, которых касается правка.
     *
     * Как и в FB2, правка идёт по тексту: сериализатор прячет кириллицу в числовые ссылки и раздувает
     * файл, а здесь всё, чего правка не касалась, остаётся ровно таким, каким было.
     */
    private fun rewriteOpf(opf: String, edit: MetadataEdit): String {
        var result = opf
        edit.title?.let { title ->
            result = replaceText(result, DC_TITLE, escapeText(title))
        }
        edit.authors?.let { authors ->
            val first = CREATOR.find(result)
            // Атрибуты первого автора (`opf:file-as`, `id`) переносятся: их пишут читалки и Calibre.
            val attributes = first
                ?.let { match -> OPENING_TAG.find(match.value)?.value.orEmpty() }
                .orEmpty()
                .removePrefix("<dc:creator")
                .removeSuffix(">")
            val xml = authors.joinToString(separator = "") { author ->
                "<dc:creator$attributes>${escapeText(author)}</dc:creator>"
            }
            result = if (first != null) {
                var replaced = false
                CREATOR.replace(result) { _ ->
                    if (replaced) {
                        ""
                    } else {
                        replaced = true
                        xml
                    }
                }
            } else {
                insertBeforeMetadataEnd(result, xml)
            }
        }
        edit.description?.let { description ->
            result = if (DC_DESCRIPTION.containsMatchIn(result)) {
                replaceText(result, DC_DESCRIPTION, escapeText(description))
            } else {
                insertBeforeMetadataEnd(result, "<dc:description>${escapeText(description)}</dc:description>")
            }
        }
        if (edit.seriesName != null || edit.seriesIndex != null) {
            val existingName = SERIES_NAME_CONTENT.find(result)?.groupValues?.get(1)
            val name = edit.seriesName ?: existingName
            val number = edit.seriesIndex?.let(::formatIndex) ?: SERIES_INDEX_CONTENT.find(result)
                ?.groupValues?.get(1)
            result = SERIES_META.replace(result, "")
            val xml = buildString {
                name?.takeIf { it.isNotBlank() }?.let { value ->
                    append("<meta name=\"calibre:series\" content=\"${escapeAttribute(value)}\"/>")
                }
                number?.takeIf { it.isNotBlank() }?.let { value ->
                    append("<meta name=\"calibre:series_index\" content=\"${escapeAttribute(value)}\"/>")
                }
                if (name?.isNotBlank() == true) {
                    append("<meta property=\"belongs-to-collection\" id=\"$COLLECTION_ID\">")
                    append(escapeText(name))
                    append("</meta>")
                    number?.takeIf { it.isNotBlank() }?.let { value ->
                        append("<meta refines=\"#$COLLECTION_ID\" property=\"group-position\">")
                        append(escapeText(value))
                        append("</meta>")
                    }
                }
            }
            result = insertBeforeMetadataEnd(result, xml)
        }
        return result
    }

    /** Заменить текст внутри элемента, сохранив его атрибуты (у `dc:title` бывает `id`). */
    private fun replaceText(fragment: String, element: Regex, text: String): String {
        val match = element.find(fragment) ?: return fragment
        val opening = OPENING_TAG.find(match.value)?.value ?: return fragment
        val closing = CLOSING_TAG.find(match.value)?.value ?: return fragment
        return fragment.replaceRange(match.range, opening + text + closing)
    }

    private fun insertBeforeMetadataEnd(fragment: String, xml: String): String {
        val end = fragment.lastIndexOf(METADATA_END)
        return if (end < 0) fragment + xml else fragment.substring(0, end) + xml + fragment.substring(end)
    }

    /**
     * Новый архив: `mimetype` первым и без сжатия, дальше все записи исходного с новым OPF.
     *
     * Содержимое чужих записей не пересобирается, а переносится: пересборка текста книги — это уже
     * не правка метаданных, а переписывание книги.
     */
    private fun rebuild(zip: ZipFile, opfPath: String, opf: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { target ->
            val mime = MIMETYPE_CONTENT.toByteArray(StandardCharsets.UTF_8)
            val crc = CRC32().apply { update(mime) }
            target.putNextEntry(
                ZipEntry(MIMETYPE).apply {
                    method = ZipEntry.STORED
                    size = mime.size.toLong()
                    compressedSize = mime.size.toLong()
                    this.crc = crc.value
                },
            )
            target.write(mime)
            target.closeEntry()
            zip.entries().asSequence()
                .filterNot { entry -> entry.isDirectory }
                .filterNot { entry -> entry.name == MIMETYPE }
                .forEach { entry ->
                    target.putNextEntry(ZipEntry(entry.name))
                    if (entry.name == opfPath) {
                        target.write(opf)
                    } else {
                        zip.getInputStream(entry).use { source -> source.copyTo(target) }
                    }
                    target.closeEntry()
                }
        }
        return output.toByteArray()
    }

    /** `ZipFile` работает с файлом, а вход — байты: копия нужна, чтобы читать архив по частям. */
    private fun spool(bytes: ByteArray): File {
        val file = File.createTempFile("bookshelf-epub-edit-", ".epub")
        FileOutputStream(file).use { it.write(bytes) }
        return file
    }

    private fun escapeText(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    private fun escapeAttribute(value: String): String = escapeText(value).replace("\"", "&quot;")

    private fun formatIndex(index: Double): String =
        if (index % 1.0 == 0.0) index.toLong().toString() else index.toString()

    private companion object {
        const val CONTAINER_PATH = "META-INF/container.xml"
        const val MIMETYPE = "mimetype"

        /** Содержимое `mimetype`: строка, по которой читалки узнают EPUB. */
        const val MIMETYPE_CONTENT = "application/epub+zip"
        const val METADATA_END = "</metadata>"
        const val COLLECTION_ID = "bookshelf-series"

        val FULL_PATH = Regex("full-path\\s*=\\s*\"([^\"]+)\"")
        val DC_TITLE = Regex("<dc:title(?:\\s[^>]*)?>.*?</dc:title>", RegexOption.DOT_MATCHES_ALL)
        val CREATOR = Regex("<dc:creator(?:\\s[^>]*)?>.*?</dc:creator>", RegexOption.DOT_MATCHES_ALL)
        val DC_DESCRIPTION = Regex(
            "<dc:description(?:\\s[^>]*)?>.*?</dc:description>",
            RegexOption.DOT_MATCHES_ALL,
        )
        val OPENING_TAG = Regex("<[a-zA-Z:]+(?:\\s[^>]*)?>")
        val CLOSING_TAG = Regex("</[a-zA-Z:]+>")
        val SERIES_NAME_CONTENT = Regex("name=\"calibre:series\"\\s+content=\"([^\"]*)\"")
        val SERIES_INDEX_CONTENT = Regex("name=\"calibre:series_index\"\\s+content=\"([^\"]*)\"")

        /** Уже записанные серии: свои и чужие — их надо убрать, чтобы не осталось двух разных. */
        val SERIES_META = Regex(
            "<meta[^>]*(?:calibre:series|belongs-to-collection|group-position)[^>]*/>" +
                "|<meta[^>]*(?:calibre:series|belongs-to-collection|group-position)[^>]*>.*?</meta>",
            RegexOption.DOT_MATCHES_ALL,
        )
    }
}
