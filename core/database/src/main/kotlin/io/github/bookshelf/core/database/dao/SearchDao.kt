package io.github.bookshelf.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import io.github.bookshelf.core.database.entity.BookEntity
import kotlinx.coroutines.flow.Flow

/**
 * Полнотекстовый поиск по FTS4 (docs/architecture.md §3.2).
 *
 * Таблица `books_fts` — внешнее содержимое `books`, связь идёт по `rowid`: у [BookEntity]
 * строковый первичный ключ, поэтому собственного `rowid`-столбца в сущности нет.
 *
 * Автодополнение — префиксный запрос: **вызывающий обязан сам добавить `*`** к последнему слову
 * (например, `"толст*"`), иначе FTS ищет точное совпадение токена. Ввод пользователя перед
 * подстановкой нужно очищать от служебных символов FTS (`"`, `*`, `(`, `)` и т. п.).
 */
@Dao
interface SearchDao {

    /** Найти книги по FTS-запросу (с префиксом `term*` для автодополнения). */
    @Query(
        """
        SELECT books.* FROM books
        INNER JOIN books_fts ON books.rowid = books_fts.rowid
        WHERE books_fts MATCH :ftsQuery
        ORDER BY books.sortTitle COLLATE NOCASE ASC
        LIMIT :limit
        """,
    )
    fun search(ftsQuery: String, limit: Int): Flow<List<BookEntity>>

    /** Подсказки автодополнения: только заголовки, префиксный запрос `term*`. */
    @Query(
        """
        SELECT books.title FROM books
        INNER JOIN books_fts ON books.rowid = books_fts.rowid
        WHERE books_fts MATCH :ftsQuery
        ORDER BY books.sortTitle COLLATE NOCASE ASC
        LIMIT :limit
        """,
    )
    fun suggestTitles(ftsQuery: String, limit: Int): Flow<List<String>>
}
