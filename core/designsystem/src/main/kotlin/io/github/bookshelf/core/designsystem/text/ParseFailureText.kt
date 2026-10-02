package io.github.bookshelf.core.designsystem.text

import io.github.bookshelf.core.common.error.ParseFailure
import io.github.bookshelf.core.designsystem.R

/**
 * Текст отказа разбора по коду.
 *
 * Рядом с [AppError.text] и по той же причине (docs/tdd.md §7–8): причина отказа хранится в базе
 * кодом, переводится на экране, а подробный текст парсера остаётся диагностикой. Одна функция на
 * все экраны (карточка книги и отчёт сканирования) — чтобы формулировки не разошлись.
 */
fun ParseFailure.text(): AppText = when (this) {
    ParseFailure.FORMAT_UNSUPPORTED -> AppText(R.string.parse_failure_format_unsupported)
    ParseFailure.CORRUPT_FILE -> AppText(R.string.parse_failure_corrupt_file)
    ParseFailure.EMPTY_FILE -> AppText(R.string.parse_failure_empty_file)
    ParseFailure.ARCHIVE_LIMIT -> AppText(R.string.parse_failure_archive_limit)
    ParseFailure.UNKNOWN -> AppText(R.string.parse_failure_unknown)
}
