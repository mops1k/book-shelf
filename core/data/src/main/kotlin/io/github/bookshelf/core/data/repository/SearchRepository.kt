package io.github.bookshelf.core.data.repository

import io.github.bookshelf.core.data.model.Book
import kotlinx.coroutines.flow.Flow

/**
 * Поиск по библиотеке («Архитектура» §3.2).
 *
 * Использует `books_fts` (`@Fts4`, токенизатор `unicode61 remove_diacritics 2`): корректно ищет по
 * кириллице и снимает диакритику с латиницы. Автодополнение — префиксный запрос (`term*`).
 * Ранжирование по FTS4 слабее, чем по FTS5, но FTS5 не гарантирован на Android 7 (ADR-002).
 */
interface SearchRepository {

    /** Полнотекстовый поиск; [limit] ограничивает число результатов для быстрого показа. */
    fun search(term: String, limit: Int): Flow<List<Book>>

    /** Подсказки по префиксу (для строки поиска). */
    fun autocomplete(prefix: String, limit: Int): Flow<List<String>>
}
