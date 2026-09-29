package io.github.bookshelf.core.testing.fake

import io.github.bookshelf.core.data.model.AuthorSeries
import io.github.bookshelf.core.data.model.BookQuery
import io.github.bookshelf.core.data.model.FacetSet
import io.github.bookshelf.core.data.repository.FilterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Фейк фасетов для тестов экранов фильтров и правил полок.
 *
 * По правилу ADR-012 (свои типы не мокаются). Запоминает, какие запросы у него спросили: на этом
 * держатся проверки «фасеты пересчитаны по новому условию».
 *
 * @param facetsFlow что отдаёт [observeFacets].
 */
class FakeFilterRepository(
    var facetsFlow: Flow<FacetSet> = flowOf(FacetSet()),
) : FilterRepository {

    /** Запросы, с которыми обращались к [observeFacets], в порядке обращений. */
    val facetQueries: MutableList<BookQuery> = mutableListOf()

    /** Имена авторов, для которых просили серии, вместе с кодом жанра (`null` — без ограничения). */
    val authorSeriesQueries: MutableList<Pair<String, String?>> = mutableListOf()

    /** Серии автора: по умолчанию «серий нет». */
    var authorSeries: AuthorSeries = AuthorSeries()

    /** Поток серий автора, если тесту нужно менять их во времени. */
    var authorSeriesFlow: Flow<AuthorSeries>? = null

    override fun observeFacets(query: BookQuery): Flow<FacetSet> {
        facetQueries += query
        return facetsFlow
    }

    override fun observeAuthorSeries(authorName: String, genreCode: String?): Flow<AuthorSeries> {
        authorSeriesQueries += authorName to genreCode
        return authorSeriesFlow ?: flowOf(authorSeries)
    }
}
