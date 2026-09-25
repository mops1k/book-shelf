package io.github.bookshelf.core.storage.scan

/**
 * Белый список расширений книг и правила исключений для скана.
 *
 * Логика чистая (без `android.*`), поэтому покрыта обычными JVM-тестами, а не Robolectric.
 */
object BookFileFilter {

    /** Форматы, которые библиотека показывает пользователю (docs/formats.md §1). */
    val SUPPORTED_EXTENSIONS: Set<String> = setOf(
        "fb2",
        "fb2.zip",
        "epub",
        "pdf",
        "txt",
        "mobi",
        "azw3",
        "prc",
        "cbz",
        "cbr",
        "docx",
        "odt",
        "rtf",
        "djvu",
    )

    /**
     * Форматы, доступные только во flavor `full`.
     *
     * CBR сейчас единственный: `junrar` распространяется под несвободной UnRar License,
     * поэтому `fdroid` и `play` собираются без него (ADR-007, docs/architecture.md §9).
     */
    val FULL_FLAVOR_ONLY_EXTENSIONS: Set<String> = setOf("cbr")

    /**
     * Форматы, которые мы сознательно оставляем без парсера.
     *
     * DJVU: Android/JVM-артефактов нет, нужна NDK-сборка DjVuLibre (docs/formats.md §1).
     * Книга видна в библиотеке с заголовком из имени файла и пометкой «формат не поддерживается».
     */
    val EXTENSIONS_WITHOUT_PARSER: Set<String> = setOf("djvu")

    /** Маски исключений по умолчанию: временные файлы и скрытые записи. */
    val DEFAULT_EXCLUDE_GLOBS: List<String> = listOf("*.tmp", ".*")

    private const val GLOB_ANY = '*'
    private const val GLOB_ONE = '?'
    private const val REGEX_ANY = ".*"
    private const val REGEX_ONE = "."
    private const val REGEX_START = "^"
    private const val REGEX_END = "$"

    private val COMPOUND_EXTENSIONS = listOf("fb2.zip")

    /** Поддерживается ли расширение (регистр не важен, ведущая точка допускается). */
    fun isSupported(extension: String): Boolean = normalizeExtension(extension) in SUPPORTED_EXTENSIONS

    /** Требует ли формат flavor `full` (сейчас — только CBR). */
    fun isSupportedOnlyInFullFlavor(extension: String): Boolean =
        normalizeExtension(extension) in FULL_FLAVOR_ONLY_EXTENSIONS

    /** Известен ли формат, но остаётся ли он без парсера (DJVU). */
    fun isSupportedWithoutParser(extension: String): Boolean =
        normalizeExtension(extension) in EXTENSIONS_WITHOUT_PARSER

    /**
     * Расширение имени файла с учётом составного `fb2.zip`.
     *
     * `DocumentEntry.extension` отдаёт только последний сегмент, а для FB2.ZIP важен именно
     * `fb2.zip`: архив опознаётся по первой записи `.fb2` или сигнатуре `<FictionBook`
     * (docs/formats.md §3).
     */
    fun detectExtension(fileName: String): String {
        val lower = fileName.lowercase()
        COMPOUND_EXTENSIONS.firstOrNull { lower.endsWith(".$it") }?.let { return it }
        val dot = lower.lastIndexOf('.')
        return if (dot <= 0) "" else lower.substring(dot + 1)
    }

    /** Подходит ли имя файла по расширению — базовый фильтр обхода. */
    fun isBookFileName(fileName: String): Boolean = isSupported(detectExtension(fileName))

    /**
     * Исключено ли имя масками [excludeGlobs].
     *
     * Поддерживаются `*` (любая последовательность, в том числе пустая) и `?` (один символ);
     * маска `.*` — соглашение об исключении скрытых записей. Сравнение без учёта регистра.
     */
    fun isExcluded(fileName: String, excludeGlobs: List<String> = DEFAULT_EXCLUDE_GLOBS): Boolean =
        excludeGlobs.any { globToRegex(it).matches(fileName) }

    private fun normalizeExtension(extension: String): String =
        extension.trim().lowercase().removePrefix(".")

    private fun globToRegex(glob: String): Regex {
        val pattern = buildString {
            append(REGEX_START)
            glob.forEach { symbol ->
                when (symbol) {
                    GLOB_ANY -> append(REGEX_ANY)
                    GLOB_ONE -> append(REGEX_ONE)
                    else -> append(Regex.escape(symbol.toString()))
                }
            }
            append(REGEX_END)
        }
        return Regex(pattern, RegexOption.IGNORE_CASE)
    }
}
