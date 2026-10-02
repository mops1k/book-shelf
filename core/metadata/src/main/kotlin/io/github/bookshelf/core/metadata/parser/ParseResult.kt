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

    /**
     * Метаданные извлечены частично: [reason] показывается в карточке по «подробнее».
     *
     * [cover] заполняется, когда страницу или картинку удалось прочитать, а часть метаданных — нет
     * (нет `ComicInfo.xml`, неизвестный namespace OPF). Раньше обложка в этом случае терялась:
     * у книги с предупреждением не было ни обложки, ни шанса её получить без повторного разбора.
     */
    data class Partial(
        val metadata: BookMetadata,
        val reason: String,
        val cover: CoverBytes? = null,
    ) : ParseResult

    /**
     * Разбор не удался.
     *
     * @param reason текст для диагностики (в интерфейс не выводится, docs/tdd.md §7);
     * @param kind вид отказа для человека — код, который карточка переводит словами.
     */
    data class Failed(
        val reason: String,
        val kind: ParseFailureKind = ParseFailureKind.CORRUPT_FILE,
    ) : ParseResult

    /** Формат осознанно не поддерживается (например, DJVU или CBR вне flavor `full`). */
    data object Unsupported : ParseResult
}
