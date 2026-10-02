package io.github.bookshelf.core.metadata.writer

import io.github.bookshelf.core.metadata.encoding.DefaultCharsetDetector
import java.nio.charset.Charset

/**
 * Правка метаданных, которую можно записать в файл книги.
 *
 * `null` означает «пользователь это поле не трогал»: значение остаётся таким, как в файле. Так
 * несколько частичных сохранений не затирают друг друга (тот же приём, что у `MetadataPatch`).
 */
data class MetadataEdit(
    val title: String? = null,
    val authors: List<String>? = null,
    val seriesName: String? = null,
    val seriesIndex: Double? = null,
    val description: String? = null,
    /**
     * Убрать номер серии, оставив название.
     *
     * Отдельный флаг, потому что `null` у [seriesIndex] означает «поле не трогали»; пустая строка
     * в [seriesName] или [description] означает «убрать значение целиком».
     */
    val clearSeriesIndex: Boolean = false,
) {

    /** Правка ничего не меняет: писать файл не нужно. */
    val isEmpty: Boolean
        get() = title == null && authors == null && seriesName == null &&
            seriesIndex == null && description == null && !clearSeriesIndex
}

/**
 * Итог правки файла.
 *
 * [NotSupported] — не ошибка записи, а «этот файл так править нельзя»: например, в FB2 нет блока
 * `title-info`. Причина показывается человеку, а файл остаётся нетронутым.
 */
sealed interface MetadataWriteResult {

    data class Rewritten(val bytes: ByteArray) : MetadataWriteResult

    data class NotSupported(val reason: String) : MetadataWriteResult
}

/**
 * Запись правки метаданных в FB2 (docs/formats.md §3).
 *
 * Правится **только** блок `<title-info>` внутри `<description>`, и внутри него — только те
 * элементы, которых коснулась правка. Тело книги, обложка в base64, `publish-info` и чужие элементы
 * описания (жанры, переводчики, `coverpage`) остаются байт в байт: правка метаданных не имеет права
 * терять данные книги.
 *
 * Правка идёт по тексту, а не пересборкой XML сериализатором. Причина найдена экспериментом:
 * сериализатор прячет всю кириллицу в `&#1057;…` (он не знает, поддерживает ли кодировка файла эти
 * символы), и описание книги раздувалось в разы. Замена конкретных элементов оставляет остальной
 * файл ровно таким, каким он был.
 *
 * Кодировка берётся из файла ([DefaultCharsetDetector]) и сохраняется: книга в windows-1251,
 * записанная как UTF-8, превратилась бы в мусор для других читалок.
 */
class Fb2MetadataWriter {

    fun rewrite(bytes: ByteArray, edit: MetadataEdit): MetadataWriteResult {
        if (edit.isEmpty) return MetadataWriteResult.Rewritten(bytes)
        val prefix = bytes.copyOfRange(0, minOf(bytes.size, CHARSET_PREFIX_SIZE))
        val charset = runCatching { Charset.forName(DefaultCharsetDetector().detect(prefix, null)) }
            .getOrElse { Charsets.UTF_8 }
        val text = String(bytes, charset).removePrefix(BOM)
        val block = TITLE_INFO.find(text)
            ?: return MetadataWriteResult.NotSupported(
                "В файле нет блока title-info — правку записывать некуда",
            )
        val rewritten = rewriteTitleInfo(block.value, edit)
        return MetadataWriteResult.Rewritten(
            text.replaceRange(block.range, rewritten).toByteArray(charset),
        )
    }

    /**
     * Пересобрать блок `title-info`, заменив только элементы, которых касается правка.
     *
     * Элемента, которого в книге не было, дописывается перед закрывающим тегом: порядок детей
     * внутри `title-info` схема FB2 не задаёт.
     */
    private fun rewriteTitleInfo(fragment: String, edit: MetadataEdit): String {
        var result = fragment
        edit.title?.let { title ->
            result = replaceOrInsert(
                result,
                BOOK_TITLE_ELEMENT,
                "<book-title>${escapeText(title)}</book-title>",
            )
        }
        edit.authors?.let { authors ->
            result = replaceAuthors(result, authors)
        }
        edit.description?.let { description ->
            result = replaceAnnotation(result, description)
        }
        // Пустая строка в `seriesName` — это «серию убрать»: значение не null, поэтому ветка одна.
        val seriesTouched = edit.seriesName != null || edit.seriesIndex != null || edit.clearSeriesIndex
        if (seriesTouched) {
            result = replaceSequence(result, edit)
        }
        return result
    }

    /**
     * Убрать все элементы `sequence`, в том числе вложенные.
     *
     * Сначала уходят вложенные (они самозакрывающиеся), потом внешние: поэтому проход повторяется,
     * пока текст меняется — иначе после удаления внутреннего остался бы висячий `</sequence>`.
     */
    private fun removeSequences(fragment: String): String {
        var result = fragment
        while (true) {
            val next = SEQUENCE_ELEMENT.replace(result, "")
            if (next == result) return result
            result = next
        }
    }

    /** Заменить первый элемент; если его в книге нет — дописать перед `</title-info>`. */
    /**
     * Описание в FB2: пустая строка убирает `<annotation>`, непустая заменяет или добавляется.
     *
     * Вынесено из `rewriteTitleInfo` отдельной функцией ради порога `CyclomaticComplexMethod`.
     */
    private fun replaceAnnotation(fragment: String, description: String): String =
        if (description.isEmpty()) {
            ANNOTATION_ELEMENT.replace(fragment, "")
        } else {
            replaceOrInsert(
                fragment,
                ANNOTATION_ELEMENT,
                "<annotation><p>${escapeText(description)}</p></annotation>",
            )
        }

    /**
     * Серия в FB2: пустая строка убирает все `<sequence>`, иначе элемент заменяется или добавляется.
     *
     * Номер без названия смысла не имеет — «индекс без серии» не пишется.
     *
     * Вынесено из `rewriteTitleInfo` отдельной функцией ради порога `CyclomaticComplexMethod`.
     */
    private fun replaceSequence(fragment: String, edit: MetadataEdit): String {
        if (edit.seriesName == EMPTY_SERIES) return removeSequences(fragment)
        val existing = SEQUENCE_ELEMENT.find(fragment)
        val name = edit.seriesName ?: existing?.let { attributeOf(it.value, NAME) }
        val number = when {
            edit.clearSeriesIndex -> null
            else -> edit.seriesIndex?.let(::formatIndex)
                ?: existing?.let { attributeOf(it.value, NUMBER) }
        }
        val xml = buildString {
            append("<sequence")
            name?.takeIf { it.isNotBlank() }?.let { append(" name=\"${escapeAttribute(it)}\"") }
            number?.takeIf { it.isNotBlank() }?.let { append(" number=\"${escapeAttribute(it)}\"") }
            append("/>")
        }
        return if (existing != null) {
            fragment.replaceRange(existing.range, xml)
        } else {
            insertBeforeEnd(fragment, xml)
        }
    }

    private fun replaceOrInsert(fragment: String, element: Regex, xml: String): String =
        if (element.containsMatchIn(fragment)) {
            fragment.replaceFirst(element, xml)
        } else {
            insertBeforeEnd(fragment, xml)
        }

    private fun insertBeforeEnd(fragment: String, xml: String): String {
        val end = fragment.lastIndexOf(CLOSING_TAG)
        return if (end < 0) {
            fragment + xml
        } else {
            fragment.substring(0, end) + xml + fragment.substring(end)
        }
    }

    /**
     * Автор записывается теми же элементами, что читает парсер, и в том же порядке.
     *
     * Строка «Имя Фамилия» разбирается по словам: парсер склеивает поля в том же порядке, поэтому
     * «Фамилия Имя Отчество» вернётся из файла той же строкой. Отдельного разбора «где фамилия»
     * здесь нет намеренно: угадывать по окончаниям — значит терять данные на именах вроде «Ли Сян».
     */
    private fun authorXml(author: String): String {
        val parts = author.trim().split(WHITESPACE).filter { it.isNotEmpty() }
        val fields = when (parts.size) {
            0 -> emptyList()
            1 -> listOf(FIRST_NAME to parts[0])
            2 -> listOf(FIRST_NAME to parts[0], LAST_NAME to parts[1])
            else -> listOf(
                FIRST_NAME to parts.first(),
                MIDDLE_NAME to parts.drop(1).dropLast(1).joinToString(" "),
                LAST_NAME to parts.last(),
            )
        }
        return buildString {
            append("<author>")
            fields.forEach { (name, value) ->
                append("<$name>${escapeText(value)}</$name>")
            }
            append("</author>")
        }
    }

    private fun attributeOf(element: String, name: String): String? =
        Regex("$name\\s*=\\s*\"([^\"]*)\"").find(element)?.groupValues?.get(1)

    private fun escapeText(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    private fun escapeAttribute(value: String): String = escapeText(value).replace("\"", "&quot;")

    /** Номер серии без хвоста «.0»: «7» вместо «7.0» — так его пишут и люди, и читалки. */
    private fun formatIndex(index: Double): String =
        if (index % 1.0 == 0.0) index.toLong().toString() else index.toString()

    private companion object {
        const val CHARSET_PREFIX_SIZE = 4_096
        const val BOM = "\uFEFF"
        const val CLOSING_TAG = "</title-info>"
        const val FIRST_NAME = "first-name"
        const val MIDDLE_NAME = "middle-name"
        const val LAST_NAME = "last-name"
        const val NAME = "name"
        const val NUMBER = "number"
        val WHITESPACE = Regex("\\s+")

        /** Блок описания: правится целиком и только он. */
        val TITLE_INFO = Regex("<title-info(?:\\s[^>]*)?>.*?</title-info>", RegexOption.DOT_MATCHES_ALL)

        /** Авторы — прямые дети `title-info`: вложенных `author` внутри описания FB2 не бывает. */
        val AUTHOR = Regex("<author(?:\\s[^>]*)?>.*?</author>", RegexOption.DOT_MATCHES_ALL)

        val BOOK_TITLE_ELEMENT = Regex("<book-title(?:\\s[^>]*)?>.*?</book-title>", RegexOption.DOT_MATCHES_ALL)
        val ANNOTATION_ELEMENT = Regex("<annotation(?:\\s[^>]*)?>.*?</annotation>", RegexOption.DOT_MATCHES_ALL)
        val SEQUENCE_ELEMENT = Regex(
            "<sequence(?:\\s[^>]*)?/>|<sequence(?:\\s[^>]*)?>.*?</sequence>",
            RegexOption.DOT_MATCHES_ALL,
        )

        /** Пустая строка в правке означает «убрать значение». */
        const val EMPTY_SERIES = ""
    }

/**
 * Авторы в FB2: пустой список убирает элементы, иначе пишется один `<author>` на человека.
 *
 * Блоки прежних авторов убираются: иначе в файле остались бы и старые, и новые. Если авторов
 * в файле не было, новые вставляются перед концом `<title-info>`.
 *
 * Вынесено из `rewriteTitleInfo` отдельной функцией: та перевалила за предел длины detekt.
 */
private fun replaceAuthors(fragment: String, authors: List<String>): String {
    var result = fragment
        // Пустой список — «авторов убрать»: элементы удаляются, новые не пишутся.
        if (authors.isEmpty()) {
            result = AUTHOR.replace(result, "")
            return result
        }
        val xml = authors.joinToString(separator = "") { author -> authorXml(author) }
        result = if (AUTHOR.containsMatchIn(result)) {
            // Первый автор заменяется всем списком, остальные блоки убираются: иначе в файле
            // остались бы и прежние авторы, и новые.
            var first = true
            AUTHOR.replace(result) { _ ->
                if (first) {
                    first = false
                    xml
                } else {
                    ""
                }
            }
        } else {
            insertBeforeEnd(result, xml)
        }

    return result
}
}
