package io.github.bookshelf.core.opds.parser

import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.opds.model.OpdsFeed

/**
 * Разбор тела OPDS-фида в доменную модель.
 *
 * Требования к реализации (docs/opds.md §4.1, §5):
 * * **XmlPullParser, без DOM** — потоковый разбор не даёт «бомбе вложенности» съесть память;
 * * **DTD и внешние сущности отключены** (`FEATURE_PROCESS_DOCDECL = false`,
 *   `FEATURE_VALIDATION = false`) — защита от XXE и «billion laughs»;
 * * лимиты: максимальный размер тела, глубина вложенности, число записей на фид (порядка 2000);
 * * относительные `href` разрешаются относительно [baseUrl], абсолютные сохраняются как есть;
 * * кодировка берётся из XML-декларации и заголовка `Content-Type`;
 * * пагинация — `rel="next"` превращается в [OpdsFeed.nextPageUrl], OpenSearch — в
 *   [OpdsFeed.searchUrl]; тип фида определяется по `kind=navigation`/`kind=acquisition`.
 *
 * Ошибка разбора возвращается как `AppResult.Failure(AppError.Parse)`, а не исключением.
 */
interface OpdsFeedParser {

    /**
     * @param body тело ответа (XML для OPDS 1.2, JSON для OPDS 2.0).
     * @param baseUrl URL запроса — база для разрешения относительных ссылок.
     */
    suspend fun parse(body: String, baseUrl: String): AppResult<OpdsFeed>
}
