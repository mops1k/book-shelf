package io.github.bookshelf.core.opds.parser

import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.opds.model.OpdsFeed
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Парсер OPDS 2.0 (JSON) — опция за флагом, Phase 4 / Sprint 12 (docs/opds.md §1).
 *
 * OPDS 2.0 описывает каталог как набор `publications`/`navigation` с `metadata` и `links`;
 * относительные ссылки разрешаются так же, как в OPDS 1.2. Отдельные `@Serializable`-модели
 * OPDS 2.0 добавляются только тогда, когда функция реально включается флагом: тащить их в
 * v1.0 смысла нет (docs/opds.md, docs/roadmap.md «OPDS 2.0 (JSON) — за флагом»).
 */
@Singleton
class Opds2JsonParser @Inject constructor() : OpdsFeedParser {

    // STUB(Sprint 12): kotlinx-serialization-модели OPDS 2.0 + маппинг в OpdsFeed.

    override suspend fun parse(body: String, baseUrl: String): AppResult<OpdsFeed> =
        TODO("STUB(Sprint 12): разобрать OPDS 2.0 JSON за флагом")
}
