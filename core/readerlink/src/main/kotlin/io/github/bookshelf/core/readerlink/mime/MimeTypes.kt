package io.github.bookshelf.core.readerlink.mime

/**
 * Соответствие «расширение файла ↔ MIME-тип».
 *
 * Осознанные решения:
 * * для форматов без зарегистрированного стандартного MIME-типа (FB2) используется
 *   [FALLBACK] = `application/octet-stream`. Придумывать «красивый» тип нельзя: получатель
 *   (внешняя читалка) сопоставляет его со своим манифестом, а неизвестный тип может привести
 *   к отказу открыть файл. FBReader дополнительно принимает нестандартный
 *   `application/x-fictionbook+xml` — он перечислен в профиле читалки, а не здесь;
 * * `mobi` и `azw3` (и устаревший `prc`) отдают один и тот же
 *   `application/x-mobipocket-ebook`;
 * * [extensionsFor] возвращает расширения в порядке объявления карты, поэтому результат
 *   детерминирован и пригоден для тестов и UI.
 */
object MimeTypes {

    /** MIME-тип для форматов, у которых нет стандартного: осознанный выбор, а не ошибка. */
    const val FALLBACK: String = "application/octet-stream"

    /** Расширение → MIME. `linkedMapOf` сохраняет порядок объявления для [extensionsFor]. */
    private val MIME_BY_EXTENSION: Map<String, String> = linkedMapOf(
        "fb2" to FALLBACK,
        "fb2.zip" to FALLBACK,
        "epub" to "application/epub+zip",
        "pdf" to "application/pdf",
        "txt" to "text/plain",
        "mobi" to "application/x-mobipocket-ebook",
        "azw3" to "application/x-mobipocket-ebook",
        "prc" to "application/x-mobipocket-ebook",
        "cbz" to "application/vnd.comicbook+zip",
        "cbr" to "application/vnd.comicbook-rar",
        "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "odt" to "application/vnd.oasis.opendocument.text",
        "rtf" to "application/rtf",
        "djvu" to "image/vnd.djvu",
    )

    private val EXTENSIONS_BY_MIME: Map<String, List<String>> =
        MIME_BY_EXTENSION.entries.groupBy(keySelector = { it.value }, valueTransform = { it.key })

    /**
     * MIME-тип по расширению. Регистр и ведущая точка не важны.
     * Неизвестное расширение даёт [FALLBACK].
     */
    fun mimeOf(extension: String): String = MIME_BY_EXTENSION[normalize(extension)] ?: FALLBACK

    /** Расширения для MIME-типа в порядке объявления; неизвестный тип даёт пустой список. */
    fun extensionsFor(mime: String): List<String> =
        EXTENSIONS_BY_MIME[mime.trim().lowercase()] ?: emptyList()

    /** Известно ли расширение (в отличие от [mimeOf] не подменяет ответ значением по умолчанию). */
    fun isKnown(extension: String): Boolean = MIME_BY_EXTENSION.containsKey(normalize(extension))

    /**
     * Все известные MIME — одним списком.
     *
     * Нужно discovery, чтобы спросить `PackageManager` про все форматы за один обход: по одному
     * запросу на формат на экране настроек даёт заметную задержку на e-ink.
     */
    fun knownMimeTypes(): List<String> = MIME_BY_EXTENSION.values.distinct()

    private fun normalize(extension: String): String =
        extension.trim().removePrefix(".").lowercase()
}
