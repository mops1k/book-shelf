package io.github.bookshelf.core.data.sync

import android.net.Uri

/**
 * Адрес книги, каким его называет читалка, приведённый к нашему виду.
 *
 * Зачем: читалка отдаёт для книги **в архиве** адрес `zip-entry://`, где наш `content://` закодирован
 * ещё раз, а следом дописан файл внутри архива:
 *
 * ```text
 * zip-entry://content%253A%252F%252F…%252Fprimary%25253ABooks%252F…%255Blitres%255D.fb2.zip/kniga.fb2
 * ```
 *
 * Наш адрес хранится иначе (схема не закодирована, а разделители внутри идентификатора документа —
 * `%2F`), поэтому простое сравнение строк не находило книгу: в журнале было `BookUnknown`, и прогресс
 * чтения терялся (проверено на устройстве 30.09.2026). Для книги без архива читалка отдаёт ровно наш
 * адрес, и такие пути эта функция не трогает — иначе декодирование испортило бы их собственные `%XX`.
 */
object ReaderBookPath {

    /** Схема, которой читалка называет файл внутри архива. */
    private const val ZIP_ENTRY_PREFIX = "zip-entry://"

    /** Разделитель между деревом и документом в адресе SAF. */
    private const val DOCUMENT_SEGMENT = "/document/"

    /** Расширение архива: по нему отрезается имя файла внутри. */
    private const val ARCHIVE_EXTENSION = ".zip"

    /**
     * Сколько раз раскодировать.
     *
     * Два, и не «до упора»: адрес архива кодируется читалкой один раз поверх нашей собственной
     * однократной кодировки, а лишнее декодирование испортило бы имя файла, в котором есть `%`
     * (например `100%.fb2` — в нашем адресе это `%25`).
     */
    private const val DECODE_PASSES = 2

    /**
     * Привести адрес читалки к нашему `books.documentUri`.
     *
     * Путь без обёртки `zip-entry://` возвращается как есть; путь, в котором нет `/document/`,
     * тоже — вызывающий просто не найдёт по нему книгу, а падать из-за чужой строки незачем.
     */
    fun toDocumentUri(readerPath: String): String {
        if (!readerPath.startsWith(ZIP_ENTRY_PREFIX)) return readerPath
        var decoded = readerPath.removePrefix(ZIP_ENTRY_PREFIX)
        repeat(DECODE_PASSES) { decoded = Uri.decode(decoded) }
        val archive = archiveOf(decoded)
        val separator = archive.indexOf(DOCUMENT_SEGMENT)
        // Не наш случай: непонятную строку не преобразуем вовсе, чтобы не выдать за адрес книги
        // что-то своё — вызывающий по ней просто не найдёт книгу.
        if (separator < 0) return readerPath
        val head = archive.substring(0, separator + DOCUMENT_SEGMENT.length)
        val documentId = archive.substring(separator + DOCUMENT_SEGMENT.length)
        // Разделители внутри идентификатора документа в нашем адресе закодированы: `primary%3ABooks%2F…`.
        // Уже раскодированные `%XX` не трогаем — только `/`.
        return head + documentId.replace("/", "%2F")
    }

    /**
     * Отрезать имя файла внутри архива.
     *
     * Берём последнее `.zip`: имя файла внутри архива — это `.fb2`, и `.zip` в нём не встречается,
     * поэтому последнее вхождение принадлежит архиву.
     */
    private fun archiveOf(decoded: String): String {
        val end = decoded.lastIndexOf(ARCHIVE_EXTENSION)
        return if (end < 0) decoded else decoded.substring(0, end + ARCHIVE_EXTENSION.length)
    }
}
