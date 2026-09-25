package io.github.bookshelf.core.metadata.parser

import io.github.bookshelf.core.metadata.model.BookMetadata
import io.github.bookshelf.core.metadata.model.CoverBytes

/**
 * Результат разбора книги.
 *
 * Соответствует `parseState` в таблице `books`: [Success] → `OK`, [Partial] → `PARTIAL`,
 * [Failed] → `FAILED`, [Unsupported] → `UNSUPPORTED`. Книга остаётся в библиотеке в любом
 * случае — с заголовком из имени файла, если метаданные извлечь не удалось
 * (docs/architecture.md §1, §4.4).
 */
sealed interface ParseResult {

    /** Метаданные извлечены полностью; [cover] может быть `null`, если обложки в файле нет. */
    data class Success(val metadata: BookMetadata, val cover: CoverBytes?) : ParseResult

    /** Метаданные извлечены частично: [reason] показывается в карточке по «подробнее». */
    data class Partial(val metadata: BookMetadata, val reason: String) : ParseResult

    /** Разбор не удался; [reason] — текст для диагностики, а не для падения. */
    data class Failed(val reason: String) : ParseResult

    /** Формат осознанно не поддерживается (например, DJVU или CBR вне flavor `full`). */
    data object Unsupported : ParseResult
}
