package io.github.bookshelf.feature.library

import io.github.bookshelf.core.data.model.AuthorSeries
import io.github.bookshelf.core.data.model.BookQuery
import io.github.bookshelf.core.data.model.FacetItem
import io.github.bookshelf.core.data.model.FacetSet
import io.github.bookshelf.core.data.repository.FilterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Подставной репозиторий фасетов для проверок обзора полки.
 *
 * Значения задаются прямо полями: тесты обзора проверяют переходы уровня и подстановку фильтра,
 * а не SQL — за него отвечают тесты `:core:database` (FacetDaoTest).
 */
internal class FakeFilterRepository(
    authors: List<FacetItem> = emptyList(),
    series: List<FacetItem> = emptyList(),
    private val authorSeries: Map<String, AuthorSeries> = emptyMap(),
) : FilterRepository {

    private val authorFacets = MutableStateFlow(authors)
    private val seriesFacets = MutableStateFlow(series)

    /** Запросы, с которыми полка обращалась за фасетами: проверяем, что фильтр уходит в БД. */
    val observedQueries = mutableListOf<BookQuery>()

    override fun observeFacets(query: BookQuery): Flow<FacetSet> {
        observedQueries += query
        return authorFacets.map { authorsValue ->
            FacetSet(authors = authorsValue, series = seriesFacets.value)
        }
    }

    override fun observeAuthorSeries(authorName: String): Flow<AuthorSeries> =
        flowOf(authorSeries[authorName] ?: AuthorSeries())
}
