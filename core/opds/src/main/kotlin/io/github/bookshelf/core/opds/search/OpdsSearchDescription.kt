package io.github.bookshelf.core.opds.search

import android.util.Xml
import java.io.StringReader
import org.xmlpull.v1.XmlPullParser

/**
 * Дескриптор OpenSearch: `rel="search"` типа `application/opensearchdescription+xml`.
 *
 * Часть каталогов объявляет поиск именно так, а рабочий шаблон держит внутри дескриптора
 * («OPDS» §1). Так устроен Project Gutenberg: в корневом фиде только ссылка на
 * `osd-books.xml`, а `{searchTerms}` появляется лишь после его загрузки. Без разбора дескриптора
 * поиск на таком каталоге не работал бы вовсе.
 *
 * Разбор через `XmlPullParser`, а не регуляркой: в дескрипторе несколько `<Url>` с разными типами
 * (`text/html`, `application/atom+xml`, `application/x-suggestions+json`) и в произвольном порядке,
 * а закомментированные примеры в нём содержат сущность `&amp;`.
 */
object OpdsSearchDescription {

    /** Тип `<Url>`, который отдаёт именно OPDS-фид: его и надо открывать. */
    private const val ATOM_TYPE = "application/atom+xml"

    /**
     * Шаблон Atom-поиска из дескриптора; `null` — пригодного шаблона в нём нет.
     *
     * Приоритет отдаётся `<Url type="application/atom+xml">`: первым в дескрипторе Gutenberg идёт
     * `text/html`, и, взяв его, мы открыли бы HTML-страницу поиска вместо фида — разбор упал бы,
     * а пользователь увидел бы ошибку на каталоге, который поиск поддерживает.
     */
    fun templateOf(body: String): String? = runCatching {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
            setInput(StringReader(body))
        }
        var atomTemplate: String? = null
        var anyTemplate: String? = null
        var event = parser.eventType

        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == URL_TAG) {
                val template = parser.getAttributeValue(null, "template").orEmpty()
                val type = parser.getAttributeValue(null, "type").orEmpty()
                if (OpdsSearchUrl.TEMPLATE_MARKER in template) {
                    if (type == ATOM_TYPE && atomTemplate == null) atomTemplate = template
                    if (anyTemplate == null) anyTemplate = template
                }
            }
            event = parser.next()
        }
        atomTemplate ?: anyTemplate
    }.getOrNull()

    private const val URL_TAG = "Url"
}
