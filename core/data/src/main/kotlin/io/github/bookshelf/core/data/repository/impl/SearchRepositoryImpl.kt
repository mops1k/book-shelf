package io.github.bookshelf.core.data.repository.impl

import io.github.bookshelf.core.data.model.Book
import io.github.bookshelf.core.data.repository.SearchRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Заглушка [SearchRepository].
 *
 * Что должна делать (Sprint 4): поиск по `books_fts` (`@Fts4`, `unicode61 remove_diacritics 2`) с
 * префиксным запросом для автодополнения (`term*`). Запрос пользователя обязательно
 * экранируется перед подстановкой в MATCH — иначе спецсимволы FTS ломают запрос.
 */
@Singleton
class SearchRepositoryImpl @Inject constructor() : SearchRepository {

    override fun search(term: String, limit: Int): Flow<List<Book>> = flowOf(emptyList())

    override fun autocomplete(prefix: String, limit: Int): Flow<List<String>> = flowOf(emptyList())
}
