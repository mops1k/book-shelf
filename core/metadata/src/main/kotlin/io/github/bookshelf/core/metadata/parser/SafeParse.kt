package io.github.bookshelf.core.metadata.parser

import kotlin.coroutines.cancellation.CancellationException

/**
 * Разбор книги, из которого исключения не выходят наружу.
 *
 * Контракт [BookMetadataParser] требует, чтобы парсер сам ловил ошибки, но полагаться на одну
 * дисциплину нельзя: разбор идёт в фоновом воркере, и брошенное исключение оставляет книгу без
 * метаданных, а работу — упавшей. Здесь единственная точка, где нарушение контракта превращается
 * в [ParseResult.Failed] с причиной.
 *
 * Отмена корутины пробрасывается: иначе остановку скана нельзя было бы прервать.
 *
 * `Throwable` здесь ловится намеренно (и это отмечено подавлением правила): заглушка формата
 * бросает `NotImplementedError`, то есть `Error`, а не `Exception`.
 */
@Suppress("TooGenericExceptionCaught")
suspend fun parseSafely(parser: BookMetadataParser?, input: BookInput): ParseResult {
    if (parser == null) return ParseResult.Unsupported
    return try {
        parser.parse(input)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        // Ловится Throwable, а не Exception: заглушка формата бросает NotImplementedError (Error),
        // и именно этот случай ломал разбор книг MOBI/DOCX/ODT/CBZ/CBR/RTF.
        val detail = error.message?.takeIf { it.isNotBlank() } ?: error::class.java.simpleName
        ParseResult.Failed("Разбор ${parser.id} не удался: $detail")
    }
}
