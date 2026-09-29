package io.github.bookshelf.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import io.github.bookshelf.core.database.relation.AuthorFacet
import io.github.bookshelf.core.database.relation.AuthorSeriesFacet
import io.github.bookshelf.core.database.relation.FormatFacet
import io.github.bookshelf.core.database.relation.ReadingStatusFacet
import io.github.bookshelf.core.database.relation.SeriesFacet
import io.github.bookshelf.core.database.relation.TagFacet
import kotlinx.coroutines.flow.Flow
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import io.github.bookshelf.core.database.entity.BookEntity
import io.github.bookshelf.core.database.entity.BookTagCrossRef
import io.github.bookshelf.core.database.entity.TagEntity

/**
 * Фасеты фильтров: авторы, серии, форматы, статусы чтения (docs/architecture.md §3.5).
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
     * Считается по денормализованному `books.authorsText`, а не по `authors`/`book_authors`:
     * эти таблицы приложение не заполняет (на устройстве 0 строк при 72 книгах), поэтому прежний
     * JOIN давал пустой список авторов и неработающий фильтр по автору. Книга с несколькими
     * авторами попадает в одну строку целиком — упрощение до задачи нормализации авторов.
     */
    @RawQuery(observedEntities = [BookEntity::class])
    fun observeAuthors(query: SupportSQLiteQuery): Flow<List<AuthorFacet>>

    /** Серии с числом книг; считается по текущему источнику и активным фильтрам. */
    // STUB(Sprint 4): добавить фильтр по sourceId и активным фасетам.
    @RawQuery(observedEntities = [BookEntity::class])
    fun observeSeries(query: SupportSQLiteQuery): Flow<List<SeriesFacet>>

    /**
     * Серии одного автора с числом книг; строка с `NULL` в имени — его книги вне серий.
     *
     * Отбор идёт по `books.authorsText` (ключ автора в домене) — нормализованные
     * `authors`/`book_authors` приложение не заполняет.
     */
    @Query(
        """
        SELECT seriesName AS seriesName, COUNT(*) AS bookCount
        FROM books
        WHERE authorsText = :authorName
        GROUP BY seriesName
        ORDER BY seriesName COLLATE NOCASE ASC
        """,
    )
    fun observeSeriesOfAuthor(authorName: String): Flow<List<AuthorSeriesFacet>>

    /**
     * Серии одного автора с числом книг; строка с `NULL` в имени — его книги вне серий.
     *
     * Отбор идёт по `books.authorsText` (ключ автора в домене) — нормализованные
     * `authors`/`book_authors` приложение не заполняет.
     */
    @RawQuery(observedEntities = [BookEntity::class, BookTagCrossRef::class, TagEntity::class])
    fun observeTags(query: SupportSQLiteQuery): Flow<List<TagFacet>>

    /** Форматы (расширения) с числом книг; считается по текущему источнику и активным фильтрам. */
    // STUB(Sprint 4): добавить фильтр по sourceId и активным фасетам.
    @RawQuery(observedEntities = [BookEntity::class])
    fun observeFormats(query: SupportSQLiteQuery): Flow<List<FormatFacet>>

    /** Значения статуса чтения с числом книг; считается по текущему источнику и активным фильтрам. */
    // STUB(Sprint 4): добавить фильтр по sourceId и активным фасетам.
    @RawQuery(observedEntities = [BookEntity::class])
    fun observeReadingStatuses(query: SupportSQLiteQuery): Flow<List<ReadingStatusFacet>>
}
