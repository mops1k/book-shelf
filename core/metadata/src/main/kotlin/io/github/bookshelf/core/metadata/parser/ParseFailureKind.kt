package io.github.bookshelf.core.metadata.parser

/**
 * Вид отказа разбора: код, а не текст (docs/tdd.md §8).
 *
 * Ядро метаданных не отдаёт готовую строку для человека: `ParseResult.Failed.reason` — это
 * диагностика для журнала, а вид отказа переживает перезапуск в `books.parseFailure` и
 * переводится на экране. Имена констант совпадают с `io.github.bookshelf.core.data.model.ParseFailure`:
 * между слоями они переносятся по `name`, как `ParseState`.
 */
enum class ParseFailureKind {

    /** Формат осознанно не поддержан (`ParseResult.Unsupported`). */
    FORMAT_UNSUPPORTED,

    /** Файл повреждён или не соответствует формату. */
    CORRUPT_FILE,

    /** Файл пуст. */
    EMPTY_FILE,

    /** Размер содержимого превысил допустимый — защита от «бомбы» в архиве. */
    ARCHIVE_LIMIT,
}
