package io.github.bookshelf.core.opds.parser

import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.common.error.appRunCatching
import io.github.bookshelf.core.opds.model.OpdsEntry
import io.github.bookshelf.core.opds.model.OpdsFeed
import io.github.bookshelf.core.opds.model.OpdsLink
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Парсер OPDS 2.0 (JSON).
 *
 * OPDS 2.0 описывает каталог как JSON: `metadata` (заголовок фида), `links` (в том числе
 * `rel=next` и шаблон поиска), `navigation` (разделы) и `publications` (книги). Публикации могут
 * лежать и внутри `groups` — так отдаёт подборки, например, каталог Feedbooks: корневой фид несёт
 * разделы и группы с книгами одновременно.
 *
 * Приводится к общей модели [OpdsFeed], поэтому UI не знает о версии каталога. Особенности формата,
 * из-за которых легко ошибиться:
 *
 * * **шаблон поиска записан как `{?query}`** (RFC 6570), а наш поиск подставляет `{searchTerms}` —
 *   шаблон нормализуется, иначе поиск по каталогу молча не работал бы;
 * * **описание — HTML**: в карточке показывается текст без разметки ([plainText]);
 * * **ссылки бывают относительными**: разрешаются относительно адреса фида;
 * * **навигационная ссылка имеет тип `application/opds+json`** — это фид, а не книга
 *   (`application/opds-publication+json` — книга), и раздел помечается привычным нам `rel=subsection`.
 *
 * Фикстуры — настоящие фиды Feedbooks (правило проекта: фикстура не пишется «по своему пониманию
 * формата»).
 */
@Singleton
class Opds2JsonParser @Inject constructor() : OpdsFeedParser {

    private val json = Json {
        // Каталоги добавляют свои поля (`schema:workExample`, `accessibility`, `facets`) — падать
        // из-за незнакомого ключа нельзя: фид чужой, и он вправе расширяться.
        ignoreUnknownKeys = true
        isLenient = true
    }

    override suspend fun parse(body: String, baseUrl: String): AppResult<OpdsFeed> = appRunCatching {
        json.decodeFromString(Opds2Document.serializer(), body).toFeed(baseUrl)
    }
}

@Serializable
private data class Opds2Document(
    val metadata: Opds2FeedMetadata? = null,
    val links: List<Opds2Link> = emptyList(),
    val navigation: List<Opds2Navigation> = emptyList(),
    val publications: List<Opds2Publication> = emptyList(),
    val groups: List<Opds2Group> = emptyList(),
)

@Serializable
private data class Opds2FeedMetadata(
    val title: String? = null,
    val modified: String? = null,
)

@Serializable
private data class Opds2Link(
    val rel: String? = null,
    val href: String = "",
    val type: String? = null,
    val title: String? = null,
    val length: Long? = null,
)

@Serializable
private data class Opds2Navigation(
    val title: String? = null,
    val href: String = "",
    val type: String? = null,
)

@Serializable
private data class Opds2Group(
    val publications: List<Opds2Publication> = emptyList(),
)

@Serializable
private data class Opds2Publication(
    val metadata: Opds2PublicationMetadata = Opds2PublicationMetadata(),
    val links: List<Opds2Link> = emptyList(),
    val images: List<Opds2Image> = emptyList(),
)

@Serializable
private data class Opds2PublicationMetadata(
    val title: String = "",
    val author: List<Opds2Author> = emptyList(),
    val language: String? = null,
    val identifier: String? = null,
    val published: String? = null,
    val modified: String? = null,
    val description: String? = null,
    val subject: List<Opds2Subject> = emptyList(),
)

/**
 * Жанр публикации.
 *
 * В OPDS 2.0 это объект (`name`, `code`, `scheme`, ссылки), а не строка: взяв только строки,
 * парсер падал на разборе — жанры у Feedbooks приходят объектами.
 */
@Serializable
private data class Opds2Subject(
    val name: String? = null,
)

@Serializable
private data class Opds2Author(
    val name: String? = null,
    val links: List<Opds2Link> = emptyList(),
)

@Serializable
private data class Opds2Image(
    val href: String = "",
)

/** Ссылка фида: относительный адрес разрешается относительно адреса самого фида. */
private fun Opds2Link.toModel(baseUrl: String): OpdsLink = OpdsLink(
    rel = rel.orEmpty(),
    href = absoluteHref(href, baseUrl),
    type = type,
    title = title,
    length = length,
)

private fun Opds2Document.toFeed(baseUrl: String): OpdsFeed {
    val modelLinks = links.map { it.toModel(baseUrl) }
    val sections = navigation.map { it.toEntry(baseUrl) }
    // Книги из групп идут после разделов: в корневом фиде подборки показываются под ними.
    val books = groups.flatMap { group -> group.publications }.map { it.toEntry(baseUrl) } +
        publications.map { it.toEntry(baseUrl) }
    return OpdsFeed(
        title = metadata?.title.orEmpty(),
        id = modelLinks.firstOrNull { it.rel == SELF_REL }?.href ?: baseUrl,
        updated = metadata?.modified.orEmpty(),
        links = modelLinks,
        entries = sections + books,
        nextPageUrl = modelLinks.firstOrNull { it.rel == NEXT_REL }?.href,
        searchUrl = modelLinks.firstOrNull { it.rel == SEARCH_REL }?.href?.let(::searchTemplate),
        // Разделы есть — значит фид навигационный, даже если рядом лежат подборки книг.
        isNavigation = navigation.isNotEmpty(),
    )
}

/**
 * Раздел каталога.
 *
 * Помечается ссылкой `rel=subsection`: в OPDS 2.0 у навигационной записи `rel` не задан вовсе, а
 * признак живёт в типе ссылки (`application/opds+json`), и по нему раздел распознаёт
 * [io.github.bookshelf.core.opds.model.isNavigation].
 */
private fun Opds2Navigation.toEntry(baseUrl: String): OpdsEntry = OpdsEntry(
    id = absoluteHref(href, baseUrl),
    title = title.orEmpty(),
    links = listOf(
        OpdsLink(
            rel = SUBSECTION_REL,
            href = absoluteHref(href, baseUrl),
            type = type ?: OPDS_JSON_TYPE,
        ),
    ),
)

private fun Opds2Publication.toEntry(baseUrl: String): OpdsEntry = OpdsEntry(
    id = metadata.identifier ?: links.firstOrNull { it.rel == SELF_REL }?.href ?: metadata.title,
    title = metadata.title,
    authors = metadata.author.mapNotNull { author -> author.name?.takeIf { it.isNotBlank() } },
    summary = metadata.description?.plainText()?.takeIf { it.isNotBlank() },
    updated = metadata.modified,
    links = links.map { it.toModel(baseUrl) },
    coverUrl = images.firstOrNull()?.href?.let { absoluteHref(it, baseUrl) },
    language = metadata.language,
    dcIdentifier = metadata.identifier,
    // В карточке показывается год, а не полная дата: `2021-04-27T00:00:00+02:00` → `2021`.
    issued = metadata.published?.take(YEAR_LENGTH),
    genres = metadata.subject.mapNotNull { subject -> subject.name?.takeIf { it.isNotBlank() } },
    // Переход к книгам автора: в OPDS 2.0 адрес лежит в ссылке самого автора.
    authorUrl = metadata.author.firstOrNull()?.links?.firstOrNull()?.href
        ?.let { absoluteHref(it, baseUrl) },
)

/** Абсолютный адрес: относительный разрешается относительно адреса фида. */
private fun absoluteHref(href: String, baseUrl: String): String {
    if (href.isBlank()) return href
    return runCatching { java.net.URI(baseUrl).resolve(href).toString() }.getOrDefault(href)
}

/**
 * Шаблон поиска в нашем виде.
 *
 * OPDS 2.0 объявляет шаблон как `search.json{?query}` (RFC 6570), наш поиск подставляет
 * `{searchTerms}`. Без нормализации кнопка «Искать» уходила бы по адресу без запроса.
 */
internal fun searchTemplate(href: String): String {
    if (SEARCH_TERMS_MARKER in href) return href
    val formStyle = FORM_STYLE_TEMPLATE.find(href) ?: return href
    val (separator, parameter) = formStyle.destructured
    val query = if (separator == "?") {
        "?$parameter=$SEARCH_TERMS_MARKER"
    } else {
        "&$parameter=$SEARCH_TERMS_MARKER"
    }
    return FORM_STYLE_TEMPLATE.replace(href, query)
}

private const val SELF_REL = "self"
private const val NEXT_REL = "next"
private const val SEARCH_REL = "search"
private const val SUBSECTION_REL = "subsection"
private const val OPDS_JSON_TYPE = "application/opds+json"
private const val SEARCH_TERMS_MARKER = "{searchTerms}"
private const val YEAR_LENGTH = 4

/** `{?query}` или `{&query}` — форма записи параметров в шаблоне OPDS 2.0. */
private val FORM_STYLE_TEMPLATE = Regex("\\{([?&])([A-Za-z0-9_]+)\\}")
