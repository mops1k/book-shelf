package io.github.bookshelf.core.opds.parser

import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.opds.model.OpdsFeed
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Парсер OPDS 1.2 (Atom XML) — обязательный формат проекта (docs/opds.md §1).
 *
 * Структура, которую он обязан разобрать:
 *
 * ```xml
 * <feed>
 *   <id/><title/><updated/>
 *   <link rel="self" href="…"/>
 *   <link rel="next" href="…"/>                    <!-- пагинация -->
 *   <link rel="search" type="application/opensearchdescription+xml" href="…"/>
 *   <entry>
 *     <id/><title/><updated/><summary/>
 *     <author><name/></author>
 *     <dc:language/><dc:identifier/><dc:publisher/>
 *     <category term="…"/>
 *     <link rel="http://opds-spec.org/acquisition/open-access" href="…" type="application/epub+zip"/>
 *     <link rel="http://opds-spec.org/image/thumbnail" href="…"/>
 *     <link rel="alternate" href="…"/>
 *   </entry>
 * </feed>
 * ```
 *
 * Реализация — `XmlPullParser` с отключёнными DTD/внешними сущностями, лимитами на размер,
 * глубину и число записей (docs/opds.md §4.1, §5).
 */
@Singleton
class Opds1XmlParser @Inject constructor() : OpdsFeedParser {

    // STUB(Sprint 8): XmlPullParser + нормализация в OpdsFeed.

    override suspend fun parse(body: String, baseUrl: String): AppResult<OpdsFeed> = TODO(
        "STUB(Sprint 8): разобрать Atom XML через XmlPullParser без DTD и внешних сущностей",
    )
}
