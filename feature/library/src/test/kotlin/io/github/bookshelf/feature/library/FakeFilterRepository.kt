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
    authorSets: List<AuthorSet> = emptyList(),
    private val authorSetSeries: Map<String, AuthorSeries> = emptyMap(),
) : FilterRepository {

    /** Авторы и жанры меняются прямо в тесте: экран должен увидеть новые значения. */
    val authorFacets = MutableStateFlow(authors)

    val tagFacets = MutableStateFlow(tags)

    private val seriesFacets = MutableStateFlow(series)

    /** Запросы серий автора вместе с кодом жанра: `null` — без ограничения по жанру. */
    val authorSeriesQueries = mutableListOf<Pair<String, String?>>()

    /**
     * Запросы категорий-наборов: обзор «Авторы» и уровень жанра ходят сюда, а не за фасетом
     * авторов — категория обзора это набор, а не человек.
     */
    val authorSetQueries = mutableListOf<BookQuery>()

    /** Запросы серий набора вместе с кодом жанра. */
    val authorSetSeriesQueries = mutableListOf<Pair<List<String>, String?>>()

    /** Категории-наборы авторов: меняются прямо в тесте. */
    val authorSetFacets = MutableStateFlow(authorSets)

    override fun observeAuthorSets(query: BookQuery): Flow<List<AuthorSet>> {
        authorSetQueries += query
        return authorSetFacets
    }

    override fun observeAuthorSetSeries(
        authorNames: List<String>,
        genreCode: String?,
    ): Flow<AuthorSeries> {
        authorSetSeriesQueries += authorNames to genreCode
        return flowOf(authorSetSeries[authorNames.joinToString("|")] ?: AuthorSeries())
    }

    /** Запросы, с которыми полка обращалась за фасетами: проверяем, что фильтр уходит в БД. */
    val observedQueries = mutableListOf<BookQuery>()

    override fun observeFacets(query: BookQuery): Flow<FacetSet> {
        observedQueries += query
        return authorFacets.map { authorsValue ->
            FacetSet(authors = authorsValue, series = seriesFacets.value, tags = tagFacets.value)
        }
    }

    override fun observeAuthorSeries(authorName: String, genreCode: String?): Flow<AuthorSeries> {
        authorSeriesQueries += authorName to genreCode
        return flowOf(authorSeries[authorName] ?: AuthorSeries())
    }
}
