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

    /** Моби-семейство: `mobi`, `azw3` и `prc` объявляются одними и теми же типами. */
    private val MOBI_DECLARATIONS: Set<String> = setOf(
        "application/x-mobipocket-ebook",
        "application/vnd.amazon.ebook",
        "application/vnd.amazon.mobi8-ebook",
    )

    /** Форматы с несколькими объявленными именами: читалка вправе назвать любое. */
    private val ALTERNATIVE_MIME_NAMES: Map<String, Set<String>> = mapOf(
        "epub" to setOf("application/epub+zip", "application/epub"),
        "cbz" to setOf("application/vnd.comicbook+zip", "application/x-cbz"),
        "cbr" to setOf("application/vnd.comicbook-rar", "application/x-cbr"),
        "mobi" to MOBI_DECLARATIONS,
        "azw3" to MOBI_DECLARATIONS,
        "prc" to MOBI_DECLARATIONS,
        "djvu" to setOf("image/vnd.djvu", "application/x-djvu", "application/djvu"),
    )

    /** Расширения FB2: у них несколько «имён» MIME, и читалки объявляют любое из них. */
    private val FB2_EXTENSIONS: Set<String> = setOf("fb2", "fb2.zip")

    /** Имена MIME, которыми читалка объявляет поддержку FB2. */
    private val FB2_DECLARATIONS: Set<String> = setOf(
        "application/fb2",
        "application/x-fb2",
        "application/fb2+zip",
        "application/x-fictionbook",
        "application/x-fictionbook+xml",
    )

    /** Стандартные типы FB2: по ним ищем читалку, но читалке их не отправляем. */
    private val PROBE_ONLY_MIME_TYPES: List<String> = listOf(
        "application/fb2",
        "application/fictionbook+xml",
        "application/x-fb2",
        "application/x-fictionbook",
        "application/x-fictionbook+xml",
    )

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

    /** Известные расширения в порядке объявления: по ним экран показывает выбор читалки для формата. */
    fun knownExtensions(): List<String> = MIME_BY_EXTENSION.keys.toList()

    /**
     * Типы, которыми СПРАШИВАЕМ систему о читалках.
     *
     * Шире, чем [knownMimeTypes]: сюда добавлены стандартные типы FB2, которые мы не отправляем
     * читалке (для совместимости отправляем `octet-stream`), но по которым читалка себя объявляет.
     * Без них читалка, умеющая только FB2, не находилась бы вовсе.
     */
    fun probeMimeTypes(): List<String> = knownMimeTypes() + PROBE_ONLY_MIME_TYPES

    /**
     * Какие объявления читалки считаются поддержкой формата.
     *
     * Для FB2 это ВСЕ известные имена, а не только [FALLBACK]: читалки объявляют стандартное
     * `application/fb2` (так делает FBReader на устройстве), а отправляем мы octet-stream —
     * и наоборот, octet-stream поддержкой считать нельзя, его принимает любое приложение.
     */
    fun declaredMimeTypesFor(extension: String): Set<String> {
        val normalized = normalize(extension)
        if (normalized in FB2_EXTENSIONS) return FB2_DECLARATIONS
        // У форматов бывает несколько «имён»: FBReader объявляет CBZ как `application/x-cbz`,
        // а XReader — как `application/vnd.comicbook+zip`. Читалка умеет формат в обоих случаях.
        ALTERNATIVE_MIME_NAMES[normalized]?.let { return it }
        return setOfNotNull(MIME_BY_EXTENSION[normalized])
    }

    private fun normalize(extension: String): String =
        extension.trim().removePrefix(".").lowercase()
}
