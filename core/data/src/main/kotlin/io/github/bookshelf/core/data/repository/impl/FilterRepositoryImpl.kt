package io.github.bookshelf.core.data.repository.impl

import io.github.bookshelf.core.common.coroutine.AppDispatchers
import io.github.bookshelf.core.data.model.AuthorSeries
import io.github.bookshelf.core.data.model.BookQuery
import io.github.bookshelf.core.data.model.FacetItem
import io.github.bookshelf.core.data.model.FacetSet
import io.github.bookshelf.core.data.repository.FilterRepository
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
        facetDao.observeAuthors(),
        facetDao.observeSeries(),
        facetDao.observeFormats(),
        facetDao.observeReadingStatuses(),
        facetDao.observeTags(),
    ) { authors: List<AuthorFacet>, series: List<SeriesFacet>, formats: List<FormatFacet>,
        statuses: List<ReadingStatusFacet>, tags: List<TagFacet> ->
        val statusNames = query.statuses.map { it.name }.toSet()
        FacetSet(
            authors = authors
                .filter { query.authorNames.isEmpty() || it.name in query.authorNames }
                .map { FacetItem(id = it.name, label = it.name, count = it.bookCount) },
            series = series
                .filter { query.series.isEmpty() || it.seriesName in query.series }
                .map { FacetItem(id = it.seriesName, label = it.seriesName, count = it.bookCount) },
            formats = formats
                .filter { query.formats.isEmpty() || it.extension in query.formats }
                .map { FacetItem(id = it.extension, label = it.extension, count = it.bookCount) },
            // Статус приходит enum'ом слоя БД, а фильтр хранит доменный: сопоставляем по имени,
            // иначе «статусы фильтра не совпали» будет выглядеть как пустой экран.
            statuses = statuses
                .filter { query.statuses.isEmpty() || it.readingStatus.name in statusNames }
                .map { FacetItem(id = it.readingStatus.name, label = it.readingStatus.name, count = it.bookCount) },
            // Жанры/теги: раньше здесь стояла заглушка, и жанры были не видны ни в фильтрах,
            // ни в умных полках, хотя парсеры их читали.
            tags = tags
                .filter { query.tags.isEmpty() || it.name in query.tags }
                .map { FacetItem(id = it.name, label = it.name, count = it.bookCount) },
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
