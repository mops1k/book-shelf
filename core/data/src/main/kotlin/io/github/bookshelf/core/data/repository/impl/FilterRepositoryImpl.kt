package io.github.bookshelf.core.data.repository.impl

import io.github.bookshelf.core.common.coroutine.AppDispatchers
import io.github.bookshelf.core.data.mapper.FacetSql
import io.github.bookshelf.core.data.model.AuthorSeries
import io.github.bookshelf.core.data.model.BookQuery
import io.github.bookshelf.core.data.model.FacetItem
import io.github.bookshelf.core.data.model.FacetSet
import io.github.bookshelf.core.data.model.ReadingStatus
import io.github.bookshelf.core.data.model.label
import io.github.bookshelf.core.data.repository.FilterRepository
import io.github.bookshelf.core.data.tag.genreDisplayName
import io.github.bookshelf.core.database.dao.FacetDao
import io.github.bookshelf.core.database.relation.AuthorFacet
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
                .map { FacetItem(id = it.name, label = it.name, count = it.bookCount) },
            series = series
                .map { FacetItem(id = it.seriesName, label = it.seriesName, count = it.bookCount) },
            formats = formats
                .map { FacetItem(id = it.extension, label = it.extension, count = it.bookCount) },
            statuses = statuses
                // id остаётся именем статуса (по нему строится запрос), подпись — словами:
                // чип «UNREAD» читался бы как код, а не как состояние чтения.
                .map { facet ->
                    FacetItem(
                        id = facet.readingStatus.name,
                        label = statusLabel(facet.readingStatus.name),
                        count = facet.bookCount,
                    )
                },
            // Жанры/теги: раньше здесь стояла заглушка, и жанры были не видны ни в фильтрах,
            // ни в умных полках, хотя парсеры их читали.
            tags = tags
                // id остаётся кодом из метаданных (по нему фильтрует SQL), подпись — название:
                // пользователю нельзя показывать `sf_space` вместо «Космической фантастики».
                .map { FacetItem(id = it.name, label = genreDisplayName(it.name), count = it.bookCount) },
        )
    }.flowOn(dispatchers.metadata)

    override fun observeAuthorSeries(authorName: String): Flow<AuthorSeries> =
        facetDao.observeSeriesOfAuthor(authorName)
            .map { rows ->
                AuthorSeries(
                    // Серии автора — по алфавиту; NULL-имя в запросе означает «вне серий» и в
                    // список серий не попадает, а превращается в отдельный счётчик.
                    series = rows.mapNotNull { row ->
                        val name = row.seriesName?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                        FacetItem(id = name, label = name, count = row.bookCount)
                    },
                    withoutSeries = rows
                        .firstOrNull { it.seriesName.isNullOrBlank() }
                        ?.bookCount
                        ?: 0,
                )
            }
            .flowOn(dispatchers.metadata)
}

/** Подпись статуса чтения по имени из БД; неизвестное имя остаётся как есть. */
internal fun statusLabel(name: String): String =
    ReadingStatus.entries.firstOrNull { status -> status.name == name }?.label ?: name
