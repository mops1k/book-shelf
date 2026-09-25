package io.github.bookshelf.core.metadata.parser

/**
 * Парсер метаданных одного или нескольких родственных форматов.
 *
 * **Парсер обязан ловить `Throwable` (кроме `CancellationException`) и никогда не бросать
 * наружу.** Отмена корутины — единственное исключение: её пробрасываем, иначе скан нельзя
 * остановить. Любая ошибка — это `FAILED`/`PARTIAL` у книги, а не падение приложения
 * (docs/architecture.md §1, §4.4; docs/formats.md §2).
 *
 * Реализации живут в `parser.impl`, регистрируются в DI (`Set<BookMetadataParser>`)
 * и выбираются [io.github.bookshelf.core.metadata.registry.ParserRegistry].
 */
interface BookMetadataParser {

    /** Идентификатор формата: `"fb2"`, `"epub"`, `"mobi"`, … Стабилен, попадает в диагностику. */
    val id: String

    /** Спринт, в котором парсер получает реализацию (docs/roadmap.md). */
    val sprint: Int

    /**
     * Поддерживает ли парсер файл.
     *
     * Проверка дешёвая и не читает содержимое: расширение и MIME. «Магия» байт проверяется
     * реестром до вызова парсера.
     */
    fun supports(extension: String, mimeType: String?): Boolean

    /** Разбирает книгу; исключения наружу не выходят (см. KDoc интерфейса). */
    suspend fun parse(input: BookInput): ParseResult
}
