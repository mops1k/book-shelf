package io.github.bookshelf.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.github.bookshelf.core.database.entity.ReadingProgressEntity
import kotlinx.coroutines.flow.Flow

/**
 * Прогресс чтения.
 *
 * Заполняется только если читалка отдаёт позицию (AIDL FBReader) либо пользователь отметил
 * вручную; отсутствие прогресса — нормальное состояние («Читалки»).
 */
@Dao
interface ReadingProgressDao {

    /** Записать или обновить прогресс книги. */
    @Upsert
    suspend fun upsert(progress: ReadingProgressEntity)

    /** Наблюдать прогресс книги — полка и карточка. */
    @Query("SELECT * FROM reading_progress WHERE bookId = :bookId")
    fun observeByBook(bookId: String): Flow<ReadingProgressEntity?>

    /** Прочитать прогресс книги. */
    @Query("SELECT * FROM reading_progress WHERE bookId = :bookId")
    suspend fun getByBook(bookId: String): ReadingProgressEntity?

    /**
     * Прогресс сразу по списку книг — одна выдача полки.
     *
     * Полка показывает окно книг, и запрос на каждую книгу превращался в сотни обращений к базе
     * на одно открытие экрана (найдено 05.10.2026: 1001 запрос на выдачу в 500 книг). Пустой
     * список вызывающий не передаёт: `IN ()` — синтаксическая ошибка SQLite.
     */
    @Query("SELECT * FROM reading_progress WHERE bookId IN (:bookIds)")
    suspend fun getProgressForBooks(bookIds: List<String>): List<ReadingProgressEntity>

    /**
     * Прогресс всей библиотеки одним запросом — выгрузка библиотеки в файл.
     *
     * Книг с прогрессом обычно меньше, чем книг вообще, поэтому строк в ответе может быть мало;
     * отсутствие строки означает «прогресса нет», а не «прогресс нулевой».
     */
    @Query("SELECT * FROM reading_progress")
    suspend fun getAll(): List<ReadingProgressEntity>

    /** Сбросить прогресс (например, при «Отметить непрочитанным»). */
    @Query("DELETE FROM reading_progress WHERE bookId = :bookId")
    suspend fun deleteByBook(bookId: String)
}
