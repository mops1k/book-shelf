package io.github.bookshelf.core.fb2opt

/**
 * Минификация разметки FB2 без потери данных.
 *
 * Что сжимается:
 * 1. комментарии `<!-- … -->` вне `CDATA` — в книгах это пометки конвертеров, читателю они не нужны;
 * 2. отступы и переводы строк между тегами — они не текст, а форматирование файла;
 * 3. пробелы внутри тегов (между именем и атрибутами) — до одного.
 *
 * Что НЕ трогается и почему:
 * * текст между тегами: пробел между двумя строчными тегами значим (`<p>Одно <emphasis>слово</emphasis>`),
 *   и его удаление склеило бы слова;
 * * `CDATA` — байт-в-байт: там может лежать разметка или код как текст;
 * * содержимое `<binary>` — картинки в base64, их обрабатывает отдельный шаг ([Fb2ImageOptimizer]).
 *
 * Работает с текстом, а не с байтами: сначала сжимается разметка, потом содержимое `<binary>`
 * заменяется оптимизированным. Для книг до нескольких мегабайт это приемлемо, а порядок шагов
 * важен — если сначала перекодировать картинки, минификатор пришлось бы учить их не задевать.
 */
class Fb2XmlMinifier {

    /** Сжать разметку [xml]. */
    fun minify(xml: String): String {
        if (xml.isEmpty()) return xml
        val out = StringBuilder(xml.length)
        var index = 0
        while (index < xml.length) {
            val char = xml[index]
            when {
                // Комментарий: выбрасываем целиком. Незакрытый — оставляем как есть: битый файл
                // чинить не наша задача, а потерять данные из-за него нельзя.
                xml.startsWith(COMMENT_OPEN, index) -> {
                    val end = xml.indexOf(COMMENT_CLOSE, index)
                    if (end < 0) {
                        out.append(xml, index, xml.length)
                        index = xml.length
                    } else {
                        index = end + COMMENT_CLOSE.length
                    }
                }

                // CDATA: байт-в-байт.
                xml.startsWith(CDATA_OPEN, index) -> {
                    val end = xml.indexOf(CDATA_CLOSE, index)
                    if (end < 0) {
                        out.append(xml, index, xml.length)
                        index = xml.length
                    } else {
                        out.append(xml, index, end + CDATA_CLOSE.length)
                        index = end + CDATA_CLOSE.length
                    }
                }

                char == '<' -> index = copyTag(xml, index, out)

                char.isWhitespace() -> {
                    val end = skipWhitespace(xml, index)
                    // Отступ МЕЖДУ тегами (`>` … `<`) — форматирование файла, его убираем. Но если
                    // хотя бы с одной стороны текст, пробел значим: `<p>Одно <emphasis>слово</emphasis>`
                    // без него склеилось бы в «Однослово».
                    val beforeIsTag = out.isNotEmpty() && out.last() == '>'
                    val afterIsTag = end < xml.length && xml[end] == '<'
                    val atEdge = out.isEmpty() || end == xml.length
                    if (!atEdge && !(beforeIsTag && afterIsTag)) out.append(' ')
                    index = end
                }

                else -> {
                    out.append(char)
                    index += 1
                }
            }
        }
        return out.toString()
    }

    /** Скопировать тег, сжав пробелы внутри него до одного. */
    private fun copyTag(xml: String, start: Int, out: StringBuilder): Int {
        val end = findTagEnd(xml, start)
        if (end < 0) {
            out.append(xml, start, xml.length)
            return xml.length
        }
        out.append('<')
        var index = start + 1
        var pendingSpace = false
        while (index < end) {
            val char = xml[index]
            if (char.isWhitespace()) {
                pendingSpace = true
            } else {
                if (pendingSpace && out.last() != '<') out.append(' ')
                pendingSpace = false
                out.append(char)
            }
            index += 1
        }
        out.append('>')
        return end + 1
    }

    /**
     * Конец тега.
     *
     * Кавычки учитываются: в значении атрибута может встретиться `>`, и обрывать тег по нему нельзя.
     */
    private fun findTagEnd(xml: String, start: Int): Int {
        var index = start + 1
        var quote: Char? = null
        while (index < xml.length) {
            val char = xml[index]
            when {
                quote != null -> if (char == quote) quote = null
                char == '"' || char == '\'' -> quote = char
                char == '>' -> return index
            }
            index += 1
        }
        return -1
    }

    private fun skipWhitespace(xml: String, start: Int): Int {
        var index = start
        while (index < xml.length && xml[index].isWhitespace()) index += 1
        return index
    }

    private companion object {
        const val COMMENT_OPEN = "<!--"
        const val COMMENT_CLOSE = "-->"
        const val CDATA_OPEN = "<![CDATA["
        const val CDATA_CLOSE = "]]>"
    }
}
