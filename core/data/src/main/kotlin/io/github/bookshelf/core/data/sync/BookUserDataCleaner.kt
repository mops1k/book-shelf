package io.github.bookshelf.core.data.sync

import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.common.error.appRunCatching
import io.github.bookshelf.core.common.time.AppClock
import io.github.bookshelf.core.database.dao.BookCleanupDao
import io.github.bookshelf.core.database.dao.CollectionDao
import io.github.bookshelf.core.database.dao.MetadataOverrideDao
import io.github.bookshelf.core.database.dao.ReadingProgressDao
import io.github.bookshelf.core.database.model.ReadingStatus
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Стирает всё, что читатель сказал о книге.
 *
 * Нужно в одном случае: файл книги по тому же пути **заменили**, и сохранение данных выключено —
 * значит, для человека это новая книга, а прежние оценка, избранное, статус чтения, прогресс, полки
 * и ручные правки метаданных относятся к другому файлу.
 *
 * Отдельным классом, а не методом репозитория: настроек здесь нет, только четыре таблицы и часы,
 * поэтому поведение проверяется на настоящей базе без десятка заглушек.
 */
@Singleton
class BookUserDataCleaner @Inject constructor(
    private val cleanupDao: BookCleanupDao,
    private val overrideDao: MetadataOverrideDao,
    private val readingProgressDao: ReadingProgressDao,
    private val collectionDao: CollectionDao,
    private val clock: AppClock,
) {

    /**
     * Стереть пользовательские данные книги.
     *
     * Порядок не важен, а полнота — да: забытая часть оставит «полустёртую» книгу, где оценка
     * относится к прежнему файлу, а полки — к новому.
     */
    suspend fun clear(bookId: String): AppResult<Unit> = appRunCatching {
        val now = clock.nowMillis()
        cleanupDao.resetUserData(id = bookId, status = ReadingStatus.UNREAD.name, addedAt = now)
        readingProgressDao.deleteByBook(bookId)
        collectionDao.removeBookFromAll(bookId)
        overrideDao.clear(bookId)
    }
}
