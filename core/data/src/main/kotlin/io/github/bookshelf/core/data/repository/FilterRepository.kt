package io.github.bookshelf.core.data.repository

import io.github.bookshelf.core.data.model.AuthorSeries
import io.github.bookshelf.core.data.model.AuthorSet
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

    /**
     * Категории обзора авторов — ТОЧНЫЕ наборы авторов книги.
     *
     * Отдельно от [observeFacets]: тот список отвечает на вопрос «книги этого человека» (фильтр
     * по автору), а обзор показывает категории «книги ровно такого состава авторов». Совпадают они
     * только у книги с одним автором — поэтому соавтор со своими книгами виден ещё и отдельно.
     */
    fun observeAuthorSets(query: BookQuery): Flow<List<AuthorSet>>

    /**
     * Серии автора для обзорного режима полки: серии с числом книг и счётчик книг вне серий.
     *
     * @param authorName имя автора; отбор идёт по связям `book_authors`/`authors`, поэтому автор
     *   находится в любой позиции и с любыми соавторами.
     */
    /**
     * Серии автора, при необходимости — только из одного жанра.
     *
     * @param genreCode код жанра или `null` для «все серии автора»: обзор по жанрам показывает
     *   автора внутри выбранного жанра, и его серии должны соответствовать этому жанру.
     */
    fun observeAuthorSeries(authorName: String, genreCode: String? = null): Flow<AuthorSeries>

    /**
     * Серии набора авторов — серии книг категории обзора.
     *
     * @param authorNames имена набора по алфавиту (из [AuthorSet.names]);
     * @param genreCode код жанра или `null` — все серии набора.
     */
    fun observeAuthorSetSeries(authorNames: List<String>, genreCode: String? = null): Flow<AuthorSeries>
}
