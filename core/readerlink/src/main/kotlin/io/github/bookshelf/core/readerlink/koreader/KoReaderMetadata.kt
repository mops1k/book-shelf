package io.github.bookshelf.core.readerlink.koreader

/**
 * Разбор sidecar-файла KOReader.
 *
 * Файл — это Lua-таблица (`return { ... }`), но выполнять его нельзя: sidecar лежит на карте, и
 * записать туда может любое приложение с доступом к памяти. `dofile` на таком файле означал бы
 * выполнение чужого кода внутри нашего процесса, поэтому разбор текстовый: берём только строковые и
 * числовые литералы нужных полей, а всё остальное (вызовы, выражения) игнорируем.
 *
 * @param percentFinished прочитанная доля 0f..1f — как её хранит KOReader.
 * @param pages число виртуальных страниц (`doc_pages`), если KOReader его сохранил.
 * @param locator позиция (`last_xpointer`): строка вида `/body/DocFragment[5]/…/text().0`.
 * @param documentPath путь книги, как его видит KOReader (`doc_path`) — по нему книга опознаётся.
 */
data class KoReaderProgress(
    val percentFinished: Float,
    val pages: Int?,
    val locator: String?,
    val documentPath: String?,
)

/**
 * Текстовый разбор sidecar-файла KOReader.
 *
 * Никакого Lua: только присваивания вида `["поле"] = значение`, где значение — число или строка в
 * кавычках. Такой разбор нельзя «сломать» хитрым содержимым файла — худшее, что случится, это
 * «прогресса нет».
 */
object KoReaderMetadata {

    /** Разобрать содержимое sidecar; `null` — доли прочитанного в файле нет. */
    fun parse(text: String): KoReaderProgress? {
        val percent = number(text, PERCENT_FIELD) ?: return null
        return KoReaderProgress(
            percentFinished = percent.coerceIn(0.0, 1.0).toFloat(),
            pages = number(text, PAGES_FIELD)?.toInt(),
            locator = string(text, LOCATOR_FIELD),
            documentPath = string(text, DOCUMENT_PATH_FIELD),
        )
    }

    /**
     * Число из поля.
     *
     * Значение обязано быть числовым литералом целиком: запись `os.time()` или `1 + 1` числом не
     * считается, иначе разбор стал бы вычислением.
     */
    private fun number(text: String, field: String): Double? =
        fieldPattern(field).find(text)?.groupValues?.get(1)?.toDoubleOrNull()

    /** Строка из поля; экранирование в значениях KOReader не использует, но кавычку не пропустит. */
    private fun string(text: String, field: String): String? =
        Regex("""\["$field"]\s*=\s*"([^"\n]*)"""").find(text)?.groupValues?.get(1)

    private fun fieldPattern(field: String) =
        Regex("""\["$field"]\s*=\s*(-?\d+(?:\.\d+)?)""")

    private const val PERCENT_FIELD = "percent_finished"
    private const val PAGES_FIELD = "doc_pages"
    private const val LOCATOR_FIELD = "last_xpointer"
    private const val DOCUMENT_PATH_FIELD = "doc_path"
}
