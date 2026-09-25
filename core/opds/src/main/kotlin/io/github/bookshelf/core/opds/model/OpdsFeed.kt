package io.github.bookshelf.core.opds.model

import kotlinx.serialization.Serializable

/**
 * Разобранный OPDS-фид.
 *
 * Модель одинакова для OPDS 1.2 (Atom) и OPDS 2.0 (JSON), поэтому парсеры приводят оба формата
 * к этому типу, а UI не знает о версии каталога.
 *
 * @param title заголовок фида (`feed/title`).
 * @param id `feed/id`; для OPDS 2.0 — `metadata.title`/self-ссылка.
 * @param updated дата обновления фида в ISO-8601.
 * @param links все ссылки фида (пагинация, поиск, `start`/`up`).
 * @param entries записи фида; для навигационного фида это разделы.
 * @param nextPageUrl готовая абсолютная ссылка `rel="next"`; null — следующей страницы нет.
 * @param searchUrl шаблон OpenSearch с `{searchTerms}`; null — поиск не поддерживается.
 * @param isNavigation true для `kind=navigation` (разделы), false для `kind=acquisition` (книги).
 */
@Serializable
data class OpdsFeed(
    val title: String,
    val id: String,
    val updated: String,
    val links: List<OpdsLink> = emptyList(),
    val entries: List<OpdsEntry> = emptyList(),
    val nextPageUrl: String? = null,
    val searchUrl: String? = null,
    val isNavigation: Boolean = false,
)
