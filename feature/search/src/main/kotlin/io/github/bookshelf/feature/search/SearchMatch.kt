package io.github.bookshelf.feature.search

import io.github.bookshelf.core.data.model.Book

/** Подписи полей, где нашлось совпадение: словами, а не именами колонок. */
private const val FIELD_TITLE = "название"
private const val FIELD_AUTHOR = "автор"
private const val FIELD_SERIES = "серия"
private const val FIELD_FILE = "файл"
private const val FIELD_DESCRIPTION = "аннотация"

/**
 * Где нашлось совпадение: «название», «автор», «серия», «файл», «аннотация».
 *
 * FTS не сообщает, в каком поле совпал токен, а показать это полезно: в результатах видно, почему
 * книга попала в список. Сравнение по вхождению подстроки, а не по токенам FTS, — намеренно
 * консервативное: если ни одно поле не подтверждает совпадение (FTS нашёл по стеммингу,
 * например), подпись не выдумывается, а остаётся пустой.
 */
fun Book.matchedFieldLabel(term: String): String? {
    val needle = term.trim()
    if (needle.isEmpty()) return null
    return when {
        title.contains(needle, ignoreCase = true) -> FIELD_TITLE
        authorsText.contains(needle, ignoreCase = true) -> FIELD_AUTHOR
        seriesName?.contains(needle, ignoreCase = true) == true -> FIELD_SERIES
        relativePath.contains(needle, ignoreCase = true) -> FIELD_FILE
        description?.contains(needle, ignoreCase = true) == true -> FIELD_DESCRIPTION
        else -> null
    }
}

