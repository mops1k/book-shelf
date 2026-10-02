package io.github.bookshelf.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import io.github.bookshelf.core.database.entity.AuthorEntity
import io.github.bookshelf.core.database.entity.BookAuthorCrossRef
import io.github.bookshelf.core.database.relation.AuthorFacet
import io.github.bookshelf.core.database.relation.AuthorSeriesFacet
import io.github.bookshelf.core.database.relation.AuthorSetFacet
import io.github.bookshelf.core.database.relation.FormatFacet
import io.github.bookshelf.core.database.relation.ReadingStatusFacet
import io.github.bookshelf.core.database.relation.SeriesFacet
import io.github.bookshelf.core.database.relation.SourceFacet
import io.github.bookshelf.core.database.relation.TagFacet
import kotlinx.coroutines.flow.Flow
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import io.github.bookshelf.core.database.entity.BookEntity
import io.github.bookshelf.core.database.entity.BookTagCrossRef
import io.github.bookshelf.core.database.entity.SourceEntity
import io.github.bookshelf.core.database.entity.TagEntity

/**
 * Фасеты фильтров: авторы, серии, форматы, статусы чтения («Архитектура» §3.5).
 *
 * **Каждый фасет считается по текущему источнику и активным фильтрам** — выбранный автор не
 * должен показывать серии, которых у него нет. Сейчас запросы считают всю библиотеку;
 * параметры источника и активных фильтров добавляются в Sprint 4.
 */
@Dao
interface FacetDao {

    /**
     * Авторы с числом книг.
     *
     * Считается по связям `book_authors`/`authors` — авторское измерение живёт в них. Строка
     * `books.authorsText` для фасета не годится: она хранит всех соавторов одной строкой, поэтому
     * соавтор, записанный вторым, не получал в фильтре своей строки и по нему нельзя было выбрать
     * книги (дефект Александра, 29.09.2026).
     */
    @RawQuery(observedEntities = [BookEntity::class, BookAuthorCrossRef::class, AuthorEntity::class])
    fun observeAuthors(query: SupportSQLiteQuery): Flow<List<AuthorFacet>>

    /**
     * Категории обзора авторов — ТОЧНЫЕ наборы авторов книги с числом книг.
     *
     * Отдельный фасет, а не [observeAuthors]: фильтр по автору отвечает на вопрос «книги этого
     * человека» (в любой позиции и с любыми соавторами), а категория обзора — «книги ровно этого
     * состава авторов». Одно подменяет другое только в одном случае: когда книга написана одним
     * автором — тогда набор и автор совпадают, поэтому соавтор со своими книгами показывается
     * отдельной категорией.
     */
    @RawQuery(observedEntities = [BookEntity::class, BookAuthorCrossRef::class, AuthorEntity::class])
    fun observeAuthorSets(query: SupportSQLiteQuery): Flow<List<AuthorSetFacet>>

    /** Серии с числом книг; считается по текущему источнику и активным фильтрам. */
    // STUB(Sprint 4): добавить фильтр по sourceId и активным фасетам.
    @RawQuery(observedEntities = [BookEntity::class])
    fun observeSeries(query: SupportSQLiteQuery): Flow<List<SeriesFacet>>

    /**
     * Серии одного автора с числом книг; строка с `NULL` в имени — его книги вне серий.
     *
     * Отбор идёт по связям: автор находится в любой позиции и с любыми соавторами. Сравнение со
     * строкой `books.authorsText` не находило ничего у автора, записанного вторым.
     *
     * @param genreCode код жанра: `null` — все серии автора, иначе только серии с книгами этого
     *   жанра. Нужно обзору по жанрам: человек выбрал «Космическая фантастика», и на уровне автора
     *   он должен видеть его серии из этого жанра, а не чужие, где книг этого жанра нет.
     */
    @Query(
        """
        SELECT seriesName AS seriesName, COUNT(*) AS bookCount
        FROM books
        WHERE books.id IN (
            SELECT book_authors.bookId FROM book_authors
            INNER JOIN authors ON authors.id = book_authors.authorId
            WHERE authors.name = :authorName
        )
          AND (
            :genreCode IS NULL
            OR EXISTS (
                SELECT 1 FROM book_tags
                INNER JOIN tags ON tags.id = book_tags.tagId
                WHERE book_tags.bookId = books.id AND tags.name = :genreCode
            )
          )
        GROUP BY seriesName
        ORDER BY seriesName COLLATE NOCASE ASC
        """,
    )
    fun observeSeriesOfAuthor(
        authorName: String,
        genreCode: String?,
    ): Flow<List<AuthorSeriesFacet>>

    /**
     * Серии набора авторов — серии книг категории обзора.
     *
     * Запрос собирает `FacetSql.seriesOfAuthorSet` (в `:core:data`): набор задаётся именами, и
     * условие «ровно этот состав авторов» совпадает с условием фильтра списка книг, поэтому
     * числа в категории и в её содержимом не разъезжаются.
     */
    @RawQuery(observedEntities = [BookEntity::class, BookAuthorCrossRef::class, AuthorEntity::class])
    fun observeSeriesOfAuthorSet(query: SupportSQLiteQuery): Flow<List<AuthorSeriesFacet>>

    /** Жанры/теги книги с числом книг; считаются по связям `book_tags`/`tags`. */
    @RawQuery(observedEntities = [BookEntity::class, BookTagCrossRef::class, TagEntity::class])
    fun observeTags(query: SupportSQLiteQuery): Flow<List<TagFacet>>

    /** Источники (папки) с числом книг; считается по активным фильтрам, но без своего фильтра. */
    @RawQuery(observedEntities = [BookEntity::class, SourceEntity::class])
    fun observeSources(query: SupportSQLiteQuery): Flow<List<SourceFacet>>

    /** Форматы (расширения) с числом книг; считается по текущему источнику и активным фильтрам. */
    // STUB(Sprint 4): добавить фильтр по sourceId и активным фасетам.
    @RawQuery(observedEntities = [BookEntity::class])
    fun observeFormats(query: SupportSQLiteQuery): Flow<List<FormatFacet>>

    /** Значения статуса чтения с числом книг; считается по текущему источнику и активным фильтрам. */
    // STUB(Sprint 4): добавить фильтр по sourceId и активным фасетам.
    @RawQuery(observedEntities = [BookEntity::class])
    fun observeReadingStatuses(query: SupportSQLiteQuery): Flow<List<ReadingStatusFacet>>
}
