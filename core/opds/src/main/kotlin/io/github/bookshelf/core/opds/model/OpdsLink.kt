package io.github.bookshelf.core.opds.model

import kotlinx.serialization.Serializable

/** Префикс `rel` для ссылок приобретения; покрывает варианты `open-access`, `buy`, `borrow`, `sample`. */
const val OPDS_ACQUISITION_REL_PREFIX: String = "http://opds-spec.org/acquisition"

/** Префикс `rel` для обложек; покрывает `image` и `image/thumbnail`. */
const val OPDS_IMAGE_REL_PREFIX: String = "http://opds-spec.org/image"

/** `rel` навигационной записи (раздел каталога). */
const val OPDS_SUBSECTION_REL: String = "subsection"

/** Фрагмент `rel`, по которому распознаётся OpenSearch. */
const val OPENSEARCH_REL_FRAGMENT: String = "opensearch"

/** MIME описания OpenSearch. */
const val OPENSEARCH_DESCRIPTION_MIME: String = "application/opensearchdescription+xml"

/** Фрагмент `type` фида/записи навигации. */
const val OPDS_NAVIGATION_KIND_FRAGMENT: String = "kind=navigation"

/** Тип Atom-фида; с ним идёт `;profile=opds-catalog` — по одному только Atom не отличить. */
const val OPDS_ATOM_TYPE_PREFIX: String = "application/atom+xml"

/**
 * Служебные `rel`, которые не открывают раздел, а обслуживают навигацию по самому фиду.
 *
 * Даже при Atom-типе их нельзя считать содержимым каталога: `next` — следующая страница
 * того же фида, `up`/`start`/`self` — сам каталог, `search` — поиск.
 */
private val NON_CONTENT_RELS: Set<String> = setOf(
    "start", "self", "up", "next", "prev", "first", "last", "current", "search", "alternate", "author",
)

/**
 * Ссылка фида или записи OPDS (`atom:link`).
 *
 * @param rel семантика ссылки; сравнение ведётся по вхождению, потому что OPDS использует
 *   семейства значений (`.../acquisition/open-access`).
 * @param href адрес; может быть относительным — абсолютный строит парсер относительно `baseUrl`.
 * @param type MIME цели; null, если каталог его не указал.
 * @param title подпись ссылки для UI; null, если не задана.
 */
@Serializable
data class OpdsLink(
    val rel: String,
    val href: String,
    val type: String? = null,
    val title: String? = null,
)

/**
 * Ссылка на файл книги.
 *
 * `rel` содержит [OPDS_ACQUISITION_REL_PREFIX], поэтому подходят все варианты:
 * `acquisition`, `acquisition/open-access`, `acquisition/buy`, `acquisition/borrow`,
 * `acquisition/sample`.
 */
fun OpdsLink.isAcquisition(): Boolean =
    rel.contains(OPDS_ACQUISITION_REL_PREFIX, ignoreCase = true)

/** Ссылка на обложку: `rel="http://opds-spec.org/image"` или `.../image/thumbnail`. */
fun OpdsLink.isImage(): Boolean = rel.contains(OPDS_IMAGE_REL_PREFIX, ignoreCase = true)

/**
 * Ссылка на раздел каталога: `rel="subsection"` либо `type` с `kind=navigation`.
 * Такие ссылки открываются как папки, а не как книга.
 */
fun OpdsLink.isNavigation(): Boolean {
    if (rel.equals(OPDS_SUBSECTION_REL, ignoreCase = true)) return true
    if (type?.contains(OPDS_NAVIGATION_KIND_FRAGMENT, ignoreCase = true) == true) return true
    if (isAcquisition() || isImage() || isSearch()) return false
    // В OPDS 1.2 семантика может быть в `type`, когда `rel` не задан. Так объявляет, в
    // частности, Flibusta: разделы приходят ссылками
    // `<link href="/opds/genres" type="application/atom+xml;profile=opds-catalog" />` —
    // без `rel` вообще. Смотреть только на `rel` было причиной того, что адрес раздела
    // не находился и кнопка «Открыть» ничего не делала.
    val isFeed = type?.trim()?.startsWith(OPDS_ATOM_TYPE_PREFIX, ignoreCase = true) == true
    return isFeed && rel.lowercase() !in NON_CONTENT_RELS
}

/**
 * Ссылка на описание OpenSearch: `rel` содержит `opensearch` либо `type` равен
 * `application/opensearchdescription+xml`. Шаблон поиска содержит `{searchTerms}`.
 */
fun OpdsLink.isSearch(): Boolean =
    rel.contains(OPENSEARCH_REL_FRAGMENT, ignoreCase = true) ||
        type?.contains(OPENSEARCH_DESCRIPTION_MIME, ignoreCase = true) == true
