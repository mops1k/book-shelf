package io.github.bookshelf.core.data.repository

import io.github.bookshelf.core.data.model.BookQuery
import io.github.bookshelf.core.data.model.FacetSet
import kotlinx.coroutines.flow.Flow

/**
 * Фасеты фильтров (docs/architecture.md §3.5, ADR-008).
 *
 * Фасеты считаются **запросом к БД** по текущему источнику и активным фильтрам, а не в памяти:
 * выбранный автор должен показывать только его серии, а на 10 000 книг пересчёт в памяти
 * не укладывается в бюджет «фасеты < 200 мс».
 */
interface FilterRepository {

    /** Наблюдать фасеты для текущего [query]; новое значение — при смене фильтров или данных. */
    fun observeFacets(query: BookQuery): Flow<FacetSet>
}
