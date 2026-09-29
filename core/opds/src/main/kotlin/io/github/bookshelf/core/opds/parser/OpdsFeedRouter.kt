package io.github.bookshelf.core.opds.parser

import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.opds.model.OpdsFeed
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Выбор парсера по телу ответа: OPDS 1.2 (Atom XML) или OPDS 2.0 (JSON).
 *
 * Человеку всё равно, какая у каталога версия: оба вида лежат в одном списке и открываются
 * одинаково. Раньше в DI был привязан только XML-парсер, поэтому JSON-каталог (например, Feedbooks)
 * не открывался вовсе — XML-парсер отдавал ошибку разбора на JSON-теле.
 *
 * Формат определяется по первому значимому символу тела, а не по `Content-Type`: клиент отдаёт
 * только тело, а `{` и `<` различают форматы надёжно. Незнакомое тело — понятная ошибка с
 * причиной, а не «не удалось разобрать неизвестно что».
 */
@Singleton
class OpdsFeedRouter @Inject constructor(
    private val atom: Opds1XmlParser,
    private val opds2: Opds2JsonParser,
) : OpdsFeedParser {

    override suspend fun parse(body: String, baseUrl: String): AppResult<OpdsFeed> {
        // BOM и пробелы встречаются у прокси: без `trimStart` выбор формата зависел бы от них.
        val head = body.trimStart('\uFEFF', ' ', '\t', '\n', '\r')
        return when {
            head.startsWith("{") -> opds2.parse(body, baseUrl)
            head.startsWith("<") -> atom.parse(body, baseUrl)
            else -> AppResult.failure(
                AppError.Parse(
                    format = "opds",
                    message = "Ответ не похож на OPDS-фид: ожидался XML или JSON",
                ),
            )
        }
    }
}
