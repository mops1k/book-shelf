package io.github.bookshelf.core.opds.parser

import android.util.Xml
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.opds.model.OPDS_ACQUISITION_REL_PREFIX
import io.github.bookshelf.core.opds.model.OPDS_IMAGE_REL_PREFIX
import io.github.bookshelf.core.opds.model.OpdsEntry
import io.github.bookshelf.core.opds.model.OpdsFeed
import io.github.bookshelf.core.opds.model.OpdsLink
import java.io.StringReader
import javax.inject.Inject
import javax.inject.Singleton
import org.xmlpull.v1.XmlPullParser

/**
 * Парсер OPDS 1.2 (Atom XML) (docs/opds.md §4.1).
 *
 * Разбор потоковый, через `XmlPullParser`, и опирается на особенности `KXmlParser`, каждая из
 * которых стоила реального падения:
 *
 * * `getText()` возвращает **`null`, а не `""`** на событиях без текста — прямой `parser.text`
 *   роняет разбор всего фида через NPE;
 * * текст доступен **только на событии `TEXT`**: к моменту `END_TAG` его уже нет, поэтому
 *   значение накапливается в буфере и забирается на закрывающем теге;
 * * `FEATURE_VALIDATION` не поддерживается и бросает на каждом фиде — не вызываем;
 * * обрезанный XML принимается молча, поэтому глубину вложенности считаем сами.
 *
 * Ссылки становятся абсолютными: в реальных фидах, включая Flibusta, они **корневые**
 * (`/opds/new`) и в исходном виде бесполезны.
 */
@Singleton
class Opds1XmlParser @Inject constructor() : OpdsFeedParser {

    override suspend fun parse(body: String, baseUrl: String): AppResult<OpdsFeed> = runCatching {
        require(body.isNotBlank()) { "Пустое тело фида" }
        require(body.contains(FEED_MARKER) || body.contains(ENTRY_MARKER)) { "Это не Atom-фид" }

        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
            setInput(StringReader(body))
        }

        val state = FeedState(baseUrl)
        var depth = 0
        var buffer: StringBuilder? = null
        var event = parser.eventType

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.TEXT -> buffer?.append(parser.text.orEmpty())

                XmlPullParser.START_TAG -> {
                    depth++
                    buffer = StringBuilder()
                    when (parser.name) {
                        "entry" -> state.startEntry()
                        "author" -> state.author = StringBuilder()
                        "link" -> state.addLink(parser, baseUrl)
                        // category у Flibusta — жанр («Космическая фантастика»), а серия
                        // приходит отдельной ссылкой. Считать одно другим нельзя: в карточке
                        // пользователь увидел бы вместо серии жанр.
                        "category" -> state.entry?.genres?.add(
                            parser.getAttributeValue(null, "term").orEmpty(),
                        )
                    }
                }

                XmlPullParser.END_TAG -> {
                    depth--
                    val value = buffer?.toString().orEmpty().trim()
                    buffer = null
                    when (parser.name) {
                        "entry" -> state.finishEntry()
                        "author" -> state.finishAuthor()
                        "name" -> state.author?.append(value)
                        "title" -> state.put("title", value)
                        "id" -> state.put("id", value)
                        "updated" -> state.put("updated", value)
                        // content у Flibusta — HTML внутри текста: без очистки аннотация показала бы разметку.
                        "summary" -> state.put("summary", value)
                        "content" -> state.put("summary", value.plainText())
                        "language" -> state.put("language", value)
                        "identifier" -> state.put("identifier", value)
                        "publisher" -> state.put("publisher", value)
                    }
                }
            }
            event = parser.next()
        }

        // KXmlParser не валидирует обрезанный ответ: без этой проверки каталог молча показал бы
        // пустоту вместо внятной ошибки.
        require(depth == 0) { "Фид оборван: не все теги закрыты" }
        state.build()
    }.fold(
        onSuccess = { AppResult.success(it) },
        onFailure = { error ->
            AppResult.failure(
                AppError.Parse(format = FORMAT, message = error.message ?: "Фид не разобран", cause = error),
            )
        },
    )

    /** Накопитель полей фида: держит только нужное, а не исходный XML. */
    private class FeedState(private val baseUrl: String) {
        var title: String = ""
        var id: String = ""
        var updated: String = ""
        var nextPageUrl: String? = null
        var searchUrl: String? = null
        var author: StringBuilder? = null
        var entry: EntryBuilder? = null
        private val links = mutableListOf<OpdsLink>()
        private val entries = mutableListOf<OpdsEntry>()

        fun startEntry() {
            require(entries.size < MAX_ENTRIES) { "Слишком много записей в фиде" }
            entry = EntryBuilder()
        }

        fun finishEntry() {
            entry?.let { entries += it.build() }
            entry = null
        }

        /** Имя автора лежит во вложенном `<name>`, а не в тексте самого `<author>`. */
        fun finishAuthor() {
            author?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { entry?.authors?.add(it) }
            author = null
        }

        fun addLink(parser: XmlPullParser, base: String) {
            val link = OpdsLink(
                rel = parser.getAttributeValue(null, "rel").orEmpty(),
                href = parser.getAttributeValue(null, "href").orEmpty().resolveAgainst(base),
                type = parser.getAttributeValue(null, "type"),
                title = parser.getAttributeValue(null, "title"),
            )
            val current = entry
            if (current != null) {
                current.links += link
                // Серия приходит ссылкой на раздел серий, а не полем category.
                if (SERIES_MARKER in link.href) current.seriesTitle = link.title ?: current.seriesTitle
                return
            }
            links += link
            if (nextPageUrl == null && link.rel == REL_NEXT) nextPageUrl = link.href
            // Ссылок rel="search" две: дескриптор OpenSearch и рабочий шаблон Atom с
            // {searchTerms}. Берём шаблон — по дескриптору запрос не пошлётся.
            if (searchUrl == null && TEMPLATE_MARKER in link.href) searchUrl = link.href
        }

        /** Пишет поле в текущую запись, а если её нет — в корень фида. */
        fun put(field: String, value: String) {
            if (value.isEmpty()) return
            val current = entry
            if (current != null) {
                current.set(field, value)
                return
            }
            when (field) {
                "title" -> if (title.isEmpty()) title = value
                "id" -> if (id.isEmpty()) id = value
                "updated" -> if (updated.isEmpty()) updated = value
            }
        }

        fun build(): OpdsFeed = OpdsFeed(
            title = title.ifEmpty { "Каталог" },
            id = id.ifEmpty { baseUrl },
            updated = updated,
            links = links,
            entries = entries,
            nextPageUrl = nextPageUrl,
            searchUrl = searchUrl,
            // У Flibusta маркера kind=navigation в теле нет: навигация определяется тем, что у
            // записей нет ссылок приобретения. Ошибить это — показать разделы как книги.
            isNavigation = entries.isNotEmpty() && entries.all { item ->
                item.links.none { it.rel.contains(OPDS_ACQUISITION_REL_PREFIX, ignoreCase = true) }
            },
        )
    }

    private class EntryBuilder {
        var id: String? = null
        var title: String = ""
        var updated: String? = null
        var summary: String? = null
        var language: String? = null
        var identifier: String? = null
        val authors = mutableListOf<String>()
        val links = mutableListOf<OpdsLink>()
        /** Серия приходит ссылкой на /opds/sequencebooks/…, а не полем category. */
        var seriesTitle: String? = null
        val genres = mutableListOf<String>()

        fun set(field: String, value: String) {
            when (field) {
                "title" -> title = value
                "id" -> id = value
                "updated" -> updated = value
                "summary" -> if (summary.isNullOrBlank()) summary = value
                "language" -> language = value
                "identifier" -> identifier = value
            }
        }

        fun build(): OpdsEntry = OpdsEntry(
            // id у Flibusta вида tag:root:new — это не URL, поэтому годится лишь как ключ.
            id = id?.takeIf { it.isNotBlank() } ?: title,
            title = title,
            authors = authors,
            summary = summary?.takeIf { it.isNotBlank() },
            updated = updated,
            links = links,
            coverUrl = links.firstOrNull { it.rel.startsWith(OPDS_IMAGE_REL_PREFIX) }?.href,
            language = language,
            seriesName = seriesTitle?.takeIf { it.isNotBlank() },
            dcIdentifier = identifier,
        )
    }

    private companion object {
        const val FORMAT = "opds1"
        const val REL_NEXT = "next"

        /** Порядка 2000 записей — предел, выше которого список всё равно не показывают. */
        const val MAX_ENTRIES = 2000

        /** Признаки Atom-фида: без них отвечаем «это не фид», а не «пустой каталог». */
        const val FEED_MARKER = "<feed"
        const val ENTRY_MARKER = "<entry"
        const val TEMPLATE_MARKER = "{searchTerms}"

        /** Ссылка на раздел серий: по ней серия и берётся, а не из category. */
        const val SERIES_MARKER = "/opds/sequencebooks/"
    }
}

/**
 * Разрешение ссылки относительно адреса фида.
 *
 * Три отдельные ловушки, поэтому склейка ручная:
 * * корень (`/covers/1.jpg`) разрешается от origin, а не от каталога фида;
 * * относительный путь — от каталога фида; `URI.resolve` считает последний сегмент базы именем
 *   файла и добавляет лишний уровень;
 * * `?` и `#` не кодируются: `java.net.URI(scheme, authority, path, …)` превращает `?page=2`
 *   в `%3Fpage=2`, и пагинация перестаёт работать.
 */
internal fun String.resolveAgainst(baseUrl: String): String {
    if (isBlank()) return this
    if (startsWith("http://") || startsWith("https://")) return this

    val base = runCatching { java.net.URI(baseUrl) }.getOrNull() ?: return this
    val path = base.path.orEmpty()
    val directory = path.substringBeforeLast('/', path)
    val relative = if (startsWith("/")) this else "$directory/$this"
    return "${base.scheme}://${base.authority}$relative"
}

/**
 * Текст без HTML-разметки.
 *
 * `content` у Flibusta приходит экранированным HTML: без очистки аннотация в карточке показала
 * бы пользователю `<p class="book">`. Сущности не разбираем — в описании книги их не бывает, а
 * опечатка в разметке не должна съедать описание целиком.
 */
internal fun String.plainText(): String =
    replace(Regex("<[^>]*>"), " ")
        .replace("&nbsp;", " ")
        .replace("&quot;", "\"")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace(Regex("\\s+"), " ")
        .trim()
