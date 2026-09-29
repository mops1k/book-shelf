package io.github.bookshelf.feature.library

import io.github.bookshelf.core.data.model.AuthorSeries
import io.github.bookshelf.core.data.model.AuthorSet
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
    tags: List<FacetItem> = emptyList(),
    private val authorSeries: Map<String, AuthorSeries> = emptyMap(),
) : FilterRepository {

    /** Авторы и жанры меняются прямо в тесте: экран должен увидеть новые значения. */
    val authorFacets = MutableStateFlow(authors)

    val tagFacets = MutableStateFlow(tags)

    private val seriesFacets = MutableStateFlow(series)

    /** Запросы серий автора вместе с кодом жанра: `null` — без ограничения по жанру. */
    val authorSeriesQueries = mutableListOf<Pair<String, String?>>()

    /** Запросы, с которыми полка обращалась за фасетами: проверяем, что фильтр уходит в БД. */
    val observedQueries = mutableListOf<BookQuery>()

    override fun observeFacets(query: BookQuery): Flow<FacetSet> {
        observedQueries += query
        return authorFacets.map { authorsValue ->
            FacetSet(authors = authorsValue, series = seriesFacets.value, tags = tagFacets.value)
        }
    }

    /** Категории-наборы обзора: сюда ходит обзор «Авторы»; в этих проверках наборы не нужны. */
    override fun observeAuthorSets(query: BookQuery): Flow<List<AuthorSet>> = flowOf(emptyList())

    /** Серии набора авторов: уровень категории обзора; здесь не используется. */
    override fun observeAuthorSetSeries(
        authorNames: List<String>,
        genreCode: String?,
    ): Flow<AuthorSeries> = flowOf(AuthorSeries())

    override fun observeAuthorSeries(authorName: String, genreCode: String?): Flow<AuthorSeries> {
        authorSeriesQueries += authorName to genreCode
        return flowOf(authorSeries[authorName] ?: AuthorSeries())
    }
}
