package io.github.bookshelf.core.opds.search

import java.net.URLEncoder

/**
 * Подстановка запроса в шаблон OpenSearch («OPDS» §3.2).
 *
 * Каталог объявляет поиск ссылкой `rel="search"`, в которой вместо запроса стоит маркер
 * `{searchTerms}` — например у Флибусты `/opds/search?searchTerm={searchTerms}`. Ссылку нельзя
 * открыть как есть: сервер ответит на запрос с литералом `{searchTerms}` и не найдёт ничего.
 *
 * Живёт в `:core:opds`, потому что это правило OPDS, а не экрана: фича каталогов не имеет права
 * знать ни про маркер, ни про кодирование.
 */
object OpdsSearchUrl {

    /** Маркер OpenSearch. По нему же парсер выбирает рабочую ссылку среди двух `rel="search"`. */
    const val TEMPLATE_MARKER: String = "{searchTerms}"

    /**
     * Готовый адрес поиска или `null`, если искать по этому шаблону нельзя.
     *
     * `null` возвращается в двух случаях, и оба — не ошибка разбора, а состояние каталога:
     * пустой запрос искать нечего, а шаблон без маркера (так выглядит дескриптор OpenSearch)
     * подставлять некуда. Вызывающий код на `null` переходит к поиску по загруженному фиду.
     */
    fun build(template: String, query: String): String? {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return null
        if (!template.contains(TEMPLATE_MARKER)) return null
        return template.replace(TEMPLATE_MARKER, encode(trimmed))
    }

    /**
     * Кодирование значения запроса.
     *
     * `URLEncoder` кодирует пробел как `+`: в параметре запроса это верно, а в пути — нет, поэтому
     * пробел приводится к `%20`, который одинаково понимают оба места. Плюс и амперсанд в самом
     * запросе кодируются (`%2B`, `%26`) — иначе они развалили бы адрес на лишние параметры.
     */
    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}
