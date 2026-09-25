package io.github.bookshelf.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import io.github.bookshelf.core.database.entity.BookEntity
import io.github.bookshelf.core.database.relation.BookWithRelations
import kotlinx.coroutines.flow.Flow

/**
 * Доступ к центральной таблице `books`.
 *
 * Запросы намеренно простые: фильтры, сортировки и пагинация по активным фильтрам появятся
 * в Sprint 4 поверх этих методов (docs/roadmap.md).
 */
@Dao
interface BookDao {

    /** Добавить книгу, найденную сканированием. */
    @Insert
    suspend fun insert(book: BookEntity)

    /** Пакетная вставка/обновление: скан обрабатывает книги пачками. */
    @Upsert
    suspend fun upsertAll(books: List<BookEntity>)

    /** Обновить книгу целиком (после разбора метаданных или ручного редактирования). */
    @Update
    suspend fun update(book: BookEntity)

    /** Удалить запись; вызывается только явно, а не при исчезновении файла (`isMissing`). */
    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteById(id: String)

    /** Наблюдать книгу по id — карточка книги. */
    @Query("SELECT * FROM books WHERE id = :id")
    fun observeById(id: String): Flow<BookEntity?>

    /** Прочитать книгу по id. */
    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getById(id: String): BookEntity?

    /**
     * Найти книгу по `documentUri` — основной шаг инкрементального сканирования
     * (docs/architecture.md §4.3). `documentUri` уникален, поэтому результат один.
     */
    @Query("SELECT * FROM books WHERE documentUri = :documentUri LIMIT 1")
    suspend fun findByDocumentUri(documentUri: String): BookEntity?

    /**
     * Отметить книги как «виденные» при сканировании.
     *
     * ВНИМАНИЕ: пустой список передавать нельзя — Room подставит `IN ()`, а SQLite не принимает
     * пустой `IN` (синтаксическая ошибка). Вызывающий обязан проверить `ids.isNotEmpty()`.
     */
    @Query("UPDATE books SET lastSeenAt = :seenAt WHERE id IN (:ids)")
    suspend fun touchLastSeen(ids: List<String>, seenAt: Long)

    /**
     * Пометить книги исчезнувшими, не удаляя запись: история чтения и коллекции сохраняются.
     *
     * ВНИМАНИЕ: пустой список передавать нельзя — `IN ()` недопустим в SQLite, см. [touchLastSeen].
     */
    @Query("UPDATE books SET isMissing = 1, missingSince = :since WHERE id IN (:ids)")
    suspend fun markMissing(ids: List<String>, since: Long)

    /** Число книг источника — для строки источника в настройках. */
    @Query("SELECT COUNT(*) FROM books WHERE sourceId = :sourceId")
    suspend fun countBySource(sourceId: String): Int

    /**
     * Страница книг для полки.
     *
     * Сейчас метод всегда отдаёт всю библиотеку в порядке `sortTitle`.
     */
    // STUB(Sprint 4): добавить параметры источника, активных фильтров и выбранной сортировки.
    @Query("SELECT * FROM books ORDER BY sortTitle COLLATE NOCASE ASC LIMIT :limit OFFSET :offset")
    fun observePage(limit: Int, offset: Int): Flow<List<BookEntity>>

    /** Быстрый хеш для сравнения «файл изменился»; `null` — хеш ещё не считан. */
    @Query("SELECT quickHash FROM books WHERE id = :id")
    suspend fun getQuickHash(id: String): String?

    /** Книга вместе с авторами, коллекциями и тегами — экран карточки книги. */
    @Transaction
    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getWithRelations(id: String): BookWithRelations?
}
