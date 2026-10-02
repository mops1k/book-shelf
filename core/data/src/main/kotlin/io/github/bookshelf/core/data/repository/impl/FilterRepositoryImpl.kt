package io.github.bookshelf.core.data.repository.impl

import io.github.bookshelf.core.common.coroutine.AppDispatchers
import io.github.bookshelf.core.data.author.authorSetLabel
import io.github.bookshelf.core.data.author.authorSetNames
import io.github.bookshelf.core.data.mapper.FacetSql
import io.github.bookshelf.core.data.model.AuthorSeries
import io.github.bookshelf.core.data.model.AuthorSet
import io.github.bookshelf.core.data.model.BookQuery
import io.github.bookshelf.core.data.model.FacetItem
import io.github.bookshelf.core.data.model.FacetSet
import io.github.bookshelf.core.data.model.ReadingStatus
import io.github.bookshelf.core.data.repository.FilterRepository
import io.github.bookshelf.core.database.dao.FacetDao
import io.github.bookshelf.core.database.relation.AuthorFacet
import io.github.bookshelf.core.database.relation.AuthorSeriesFacet
import io.github.bookshelf.core.database.relation.AuthorSetFacet
import io.github.bookshelf.core.database.relation.FormatFacet
import io.github.bookshelf.core.database.relation.ReadingStatusFacet
import io.github.bookshelf.core.database.relation.SeriesFacet
import io.github.bookshelf.core.database.relation.TagFacet
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Фасеты полки (docs/architecture.md §3.5, ADR-008).
 *
 * **Считаются запросами к БД, а не в памяти.** Причина прямая: на 10 000 книг фильтрация в
 * памяти не масштабируется, а выбранный автор должен показывать только те серии, которые у него
 * действительно есть. Пользователь узнаёт об этом по пустому экрану, а не по счётчику.
 *
 * **Активные фильтры применяются к фасетам.** Иначе выбранный автор показывал бы ноль книг у
 * каждой своей же серии. Список должен отвечать на вопрос «что будет, если щёлкнуть», а не
 * «что выбрано сейчас».
 *
 * **Пустое множество фильтра означает «не ограничивать»**, а не «нечего не показывать»: без
 * этого проверки ниже отсекли бы все значения сразу и экран фильтров был бы вечно пуст.
 */
@Singleton
class FilterRepositoryImpl @Inject constructor(
    private val facetDao: FacetDao,
    private val dispatchers: AppDispatchers,
) : FilterRepository {

    override fun observeFacets(query: BookQuery): Flow<FacetSet> = combine(
        // Фасеты считаются тем же предикатом, что и список книг, и без собственного фильтра:
        // выбранный автор сужает серии и жанры, но список авторов остаётся полным, чтобы можно
        // было выбрать второго (docs/architecture.md §3.5, ADR-008).
        facetDao.observeAuthors(FacetSql.authors(query)),
        facetDao.observeSeries(FacetSql.series(query)),
        facetDao.observeFormats(FacetSql.formats(query)),
        facetDao.observeReadingStatuses(FacetSql.statuses(query)),
        facetDao.observeTags(FacetSql.tags(query)),
    ) { authors: List<AuthorFacet>, series: List<SeriesFacet>, formats: List<FormatFacet>,
        statuses: List<ReadingStatusFacet>, tags: List<TagFacet> ->
        FacetSet(
            authors = authors
                .map { FacetItem(id = it.name, labelCode = it.name, count = it.bookCount) },
            series = series
                .map { FacetItem(id = it.seriesName, labelCode = it.seriesName, count = it.bookCount) },
            formats = formats
                .map { FacetItem(id = it.extension, labelCode = it.extension, count = it.bookCount) },
            statuses = statuses
                // id остаётся именем статуса (по нему строится запрос), подпись — словами:
                // чип «UNREAD» читался бы как код, а не как состояние чтения.
                .map { facet ->
                    FacetItem(
                        // Подпись статуса — имя значения: слова выбирает экран (docs/tdd.md §8).
                        id = facet.readingStatus.name,
                        labelCode = facet.readingStatus.name,
                        count = facet.bookCount,
                    )
                },
            // Жанры/теги: раньше здесь стояла заглушка, и жанры были не видны ни в фильтрах,
            // ни в умных полках, хотя парсеры их читали.
            tags = tags
                // id остаётся кодом из метаданных (по нему фильтрует SQL), подпись — название:
                // пользователю нельзя показывать `sf_space` вместо «Космической фантастики».
                .map { FacetItem(id = it.name, labelCode = it.name, count = it.bookCount) },
        )
    }
        // Источники (папки) собираются отдельным `combine`: у `combine` пять типизированных
        // аргументов, а измерений стало шесть. Подпись — имя папки; пустое имя означает, что
        // слова выберет экран («Папка»), как и везде после отказа писать текст в базу.
        .combine(facetDao.observeSources(FacetSql.sources(query))) { set, sources ->
            set.copy(
                sources = sources.map {
                    FacetItem(id = it.sourceId, labelCode = it.sourceName, count = it.bookCount)
                },
            )
        }
        .flowOn(dispatchers.metadata)

    override fun observeAuthorSetSeries(
        authorNames: List<String>,
        genreCode: String?,
    ): Flow<AuthorSeries> =
        facetDao.observeSeriesOfAuthorSet(FacetSql.seriesOfAuthorSet(authorNames, genreCode))
            .map { rows -> rows.toAuthorSeries() }
            .flowOn(dispatchers.metadata)

    override fun observeAuthorSets(query: BookQuery): Flow<List<AuthorSet>> =
        facetDao.observeAuthorSets(FacetSql.authorSets(query))
            .map { rows: List<AuthorSetFacet> ->
                rows.map { row ->
                    // Ключ набора приходит из SQL (имена по алфавиту через «|»). Фильтру нужны
                    // имена, а не ключ: отбор идёт по составу авторов, поэтому порядок сортировки
                    // в SQL на то, какие книги попадут в категорию, не влияет.
                    val names = authorSetNames(row.setKey)
                    AuthorSet(names = names, label = authorSetLabel(names), count = row.bookCount)
                }
            }
            .flowOn(dispatchers.metadata)

    override fun observeAuthorSeries(authorName: String, genreCode: String?): Flow<AuthorSeries> =
        facetDao.observeSeriesOfAuthor(authorName, genreCode)
            .map { rows -> rows.toAuthorSeries() }
            .flowOn(dispatchers.metadata)
}

/**
 * Строки серий из БД в доменную модель.
 *
 * `NULL` в имени означает книги автора вне серий: в список серий они не попадают, а считаются
 * отдельным числом — иначе из обзора по авторам такие книги было бы не найти.
 */
private fun List<AuthorSeriesFacet>.toAuthorSeries(): AuthorSeries = AuthorSeries(
    series = mapNotNull { row ->
        val name = row.seriesName?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        FacetItem(id = name, labelCode = name, count = row.bookCount)
    },
    withoutSeries = firstOrNull { row -> row.seriesName.isNullOrBlank() }?.bookCount ?: 0,
)
