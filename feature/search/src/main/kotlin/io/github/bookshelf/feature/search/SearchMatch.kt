package io.github.bookshelf.feature.search

import io.github.bookshelf.core.data.model.Book


/**
 * Где нашлось совпадение: подпись поля как ресурс.
 *
 * FTS не сообщает, в каком поле совпал токен, а показать это полезно: в результатах видно, почему
 * книга попала в список. Сравнение по вхождению подстроки, а не по токенам FTS, — намеренно
 * консервативное: если ни одно поле не подтверждает совпадение (FTS нашёл по стеммингу,
 * например), подпись не выдумывается, а остаётся пустой.
 *
 * Возвращается ресурс, а не текст: слова живут в `res/values` (ru) и `values-en`, а выбор поля
 * остаётся чистой функцией и проверяется без Compose.
 */
fun Book.matchedFieldRes(term: String): Int? {
    val needle = term.trim()
    if (needle.isEmpty()) return null
    return when {
        title.contains(needle, ignoreCase = true) -> R.string.search_field_title
        authorsText.contains(needle, ignoreCase = true) -> R.string.search_field_author
        seriesName?.contains(needle, ignoreCase = true) == true -> R.string.search_field_series
        relativePath.contains(needle, ignoreCase = true) -> R.string.search_field_file
        description?.contains(needle, ignoreCase = true) == true -> R.string.search_field_description
        else -> null
    }
}

