package io.github.bookshelf.core.data.repository.impl

import io.github.bookshelf.core.data.model.BookQuery
import io.github.bookshelf.core.data.model.FacetSet
import io.github.bookshelf.core.data.repository.FilterRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Заглушка [FilterRepository].
 *
 * Что должна делать (Sprint 4, ADR-008): отдельные `COUNT(*)`-запросы к БД по текущему источнику и
 * активным фильтрам — авторы (`book_authors`), серии (`books.seriesName`), форматы
 * (`books.extension`), статусы (`books.readingStatus`), теги (`book_tags`). Никакой фильтрации
 * в памяти: на 10 000 книг она не укладывается в бюджет «фасеты < 200 мс».
 */
@Singleton
class FilterRepositoryImpl @Inject constructor() : FilterRepository {

    override fun observeFacets(query: BookQuery): Flow<FacetSet> = flowOf(FacetSet())
}
