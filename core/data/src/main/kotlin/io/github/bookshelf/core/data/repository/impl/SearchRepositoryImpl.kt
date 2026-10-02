package io.github.bookshelf.core.data.repository.impl

import io.github.bookshelf.core.data.author.parseAuthors

import io.github.bookshelf.core.common.coroutine.AppDispatchers
import io.github.bookshelf.core.data.mapper.toDomain
import io.github.bookshelf.core.data.model.Book
import io.github.bookshelf.core.data.repository.SearchRepository
import io.github.bookshelf.core.data.search.ftsQueryOf
import io.github.bookshelf.core.database.dao.SearchDao
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Полнотекстовый поиск по библиотеке («Архитектура» §3.2).
 *
 * Запрос уходит в FTS4 очищенным: движок не знает SQL и понимает другой синтаксис — кавычка,
 * скобка или звёздочка меняют смысл запроса либо роняют его с ошибкой. Очистка и превращение
 * слов в запрос живут в [ftsQueryOf] (там же объяснено, почему слова соединяются пробелом,
 * а не оператором `AND`).
 *
 * **Пустой или нечитаемый ввод не превращается в `MATCH ''`**, который вернул бы всю
 * библиотеку: пустой поиск — это пустой результат.
 */
@Singleton
class SearchRepositoryImpl @Inject constructor(
    private val searchDao: SearchDao,
    private val dispatchers: AppDispatchers,
) : SearchRepository {

    override fun search(term: String, limit: Int): Flow<List<Book>> {
        val query = ftsQueryOf(term) ?: return flowOf(emptyList())
        return searchDao.search(query, limit)
            .map { entities -> entities.map { it.toDomain(authors = parseAuthors(it.authorsText)) } }
            .flowOn(dispatchers.io)
    }

    override fun autocomplete(prefix: String, limit: Int): Flow<List<String>> {
        val query = ftsQueryOf(prefix) ?: return flowOf(emptyList())
        // Последнее слово получает звёздочку: без неё FTS ищет токен целиком и не подскажет
        // «Вой» по запросу «Вой».
        val last = query.substringAfterLast(' ')
        val withStar = if (last == query) "$query*" else "${query.substringBeforeLast(' ')} $last*"
        return searchDao.suggestTitles(withStar, limit).flowOn(dispatchers.io)
    }
}
