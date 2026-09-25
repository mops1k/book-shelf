package io.github.bookshelf.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import io.github.bookshelf.core.database.relation.AuthorFacet
import io.github.bookshelf.core.database.relation.FormatFacet
import io.github.bookshelf.core.database.relation.ReadingStatusFacet
import io.github.bookshelf.core.database.relation.SeriesFacet
import kotlinx.coroutines.flow.Flow

/**
 * Фасеты фильтров: авторы, серии, форматы, статусы чтения (docs/architecture.md §3.5).
 *
 * **Каждый фасет считается по текущему источнику и активным фильтрам** — выбранный автор не
 * должен показывать серии, которых у него нет. Сейчас запросы считают всю библиотеку;
 * параметры источника и активных фильтров добавляются в Sprint 4.
 */
@Dao
interface FacetDao {

    /** Авторы с числом книг; считается по текущему источнику и активным фильтрам. */
    // STUB(Sprint 4): добавить фильтр по sourceId и активным фасетам (docs/architecture.md §3.5).
    @Query(
        """
        SELECT authors.id AS authorId,
               authors.name AS name,
               authors.sortName AS sortName,
               COUNT(book_authors.bookId) AS bookCount
        FROM authors
        INNER JOIN book_authors ON book_authors.authorId = authors.id
        GROUP BY authors.id, authors.name, authors.sortName
        ORDER BY authors.sortName COLLATE NOCASE ASC
        """,
    )
    fun observeAuthors(): Flow<List<AuthorFacet>>

    /** Серии с числом книг; считается по текущему источнику и активным фильтрам. */
    // STUB(Sprint 4): добавить фильтр по sourceId и активным фасетам.
    @Query(
        """
        SELECT seriesName AS seriesName, COUNT(*) AS bookCount
        FROM books
        WHERE seriesName IS NOT NULL AND seriesName <> ''
        GROUP BY seriesName
        ORDER BY seriesName COLLATE NOCASE ASC
        """,
    )
    fun observeSeries(): Flow<List<SeriesFacet>>

    /** Форматы (расширения) с числом книг; считается по текущему источнику и активным фильтрам. */
    // STUB(Sprint 4): добавить фильтр по sourceId и активным фасетам.
    @Query(
        """
        SELECT extension AS extension, COUNT(*) AS bookCount
        FROM books
        GROUP BY extension
        ORDER BY extension COLLATE NOCASE ASC
        """,
    )
    fun observeFormats(): Flow<List<FormatFacet>>

    /** Значения статуса чтения с числом книг; считается по текущему источнику и активным фильтрам. */
    // STUB(Sprint 4): добавить фильтр по sourceId и активным фасетам.
    @Query(
        """
        SELECT readingStatus AS readingStatus, COUNT(*) AS bookCount
        FROM books
        GROUP BY readingStatus
        """,
    )
    fun observeReadingStatuses(): Flow<List<ReadingStatusFacet>>
}
