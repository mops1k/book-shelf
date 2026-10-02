package io.github.bookshelf.core.readerlink.discovery

import io.github.bookshelf.core.readerlink.mime.MimeTypes
import io.github.bookshelf.core.readerlink.profile.FbReaderPreset

/**
 * Насколько найденное приложение похоже на читалку: порядок в списке и признак «это читалка».
 *
 * Меньше — выше: известные читалки идут первыми, за ними те, кто объявил книжные форматы,
 * и только потом всё остальное.
 */
enum class ReaderRank {

    /** Пакет из списка известных читалок: приложение проверено на живом устройстве. */
    KNOWN,

    /** Неизвестный пакет, но он объявил хотя бы один книжный формат (`epub`, `fb2`, `mobi`, …). */
    BOOK_FORMAT,

    /**
     * Не читалка: отвечает только на общие типы (`pdf`, текстовые) или прямо в чёрном списке.
     *
     * Такие приложения в списке выбора НЕ показываются (требование Александра, 01.10.2026):
     * браузер, «Фото», словарь и редактор документов книгу открыть могут, но читалками не являются.
     */
    OTHER,
}

/**
 * Ранг приложения по заявленным MIME-типам.
 *
 * ЧТО БЫЛО НЕ ТАК. Discovery отбрасывал всё, чего нет в [FbReaderPreset.KNOWN_READER_PACKAGES]:
 * человек с установленным XReader видел только FBReader («как будто он зашит в коде»). Зашитый
 * список появился как лекарство от другой беды — в списке оказывались браузеры и файловые
 * менеджеры, которые тоже отвечают на запрос по книжному типу. Правильное лекарство — не
 * отбрасывать, а РАЗЛИЧАТЬ: приложение, объявившее `epub`/`fb2`/`mobi`, читалкой быть обязано,
 * а открывающее только `pdf` или `text/plain` — не обязано, и уходит в конец списка.
 */
fun readerRankOf(packageName: String, mimeTypes: Collection<String>): ReaderRank = when {
    // Чёрный список проверяется ПЕРВЫМ: словарь или браузер может объявить epub честно,
    // но читалкой от этого не становится.
    NonReaderApps.isKnownNonReader(packageName) -> ReaderRank.OTHER
    packageName in FbReaderPreset.KNOWN_READER_PACKAGES -> ReaderRank.KNOWN
    mimeTypes.any { mime -> mime.lowercase() in BOOK_FORMAT_MIME_TYPES } -> ReaderRank.BOOK_FORMAT
    else -> ReaderRank.OTHER
}

/** Книжные MIME-типы: то, что объявляет именно читалка, а не браузер или галерея. */
private val BOOK_FORMAT_MIME_TYPES: Set<String> = setOf(
    // `application/octet-stream` здесь НЕТ и быть не должно: этот тип получает всё неизвестное,
    // и любой файловый менеджер стал бы «читалкой». FB2 мы отправляем читалке как octet-stream
    // (см. `MimeTypes`), но узнаём читалку по стандартному `application/fb2`.
    // Документы (docx, odt, rtf) тоже не доказательство: их открывает и офисный редактор.
    "application/fb2",
    "application/x-fb2",
    "application/fb2+zip",
    "application/x-fictionbook",
    "application/x-fictionbook+xml",
    "application/epub+zip",
    "application/epub",
    "application/x-mobipocket-ebook",
    "application/vnd.amazon.ebook",
    "application/vnd.amazon.mobi8-ebook",
    "application/vnd.comicbook+zip",
    "application/vnd.comicbook-rar",
    "application/x-cbz",
    "application/x-cbr",
    "image/vnd.djvu",
    "application/x-djvu",
    "application/djvu",
)
