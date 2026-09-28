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
    @Query(
        """
        SELECT authorsText AS name, COUNT(*) AS bookCount
        FROM books
        WHERE authorsText <> ''
        GROUP BY authorsText
        ORDER BY authorsText COLLATE NOCASE ASC
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
     * Жанры/теги с числом книг: самые крупные сверху.
     *
     * Жанры пишет `BookTagWriter` при разборе метаданных, поэтому фасет отражает реальную
     * библиотеку; до этого `FilterRepositoryImpl` отдавал `tags = emptyList()`.
     */
    @Query(
        """
        SELECT tags.name AS name, COUNT(book_tags.bookId) AS bookCount
        FROM tags
        INNER JOIN book_tags ON book_tags.tagId = tags.id
        GROUP BY tags.id, tags.name
        ORDER BY bookCount DESC, tags.name COLLATE NOCASE ASC
        """,
    )
    fun observeTags(): Flow<List<TagFacet>>

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
