package io.github.bookshelf.core.data.sync

import io.github.bookshelf.core.database.dao.BookDao
import io.github.bookshelf.core.database.dao.CollectionDao
import io.github.bookshelf.core.database.dao.ReadingProgressDao
import io.github.bookshelf.core.database.entity.CollectionBookCrossRef
import io.github.bookshelf.core.database.model.ReadingStatus
import javax.inject.Inject

/**
 * Перенос данных человека с файла-копии на книгу, которую она повторяет.
 *
 * Зачем: копия, созданная читалкой, какое-то время была отдельной книгой на полке — с ней связаны
 * прогресс чтения, статус, избранное, оценка и полки (дефект 01.10.2026). Когда копия перестаёт быть
 * книгой, эти данные нельзя выбрасывать: они принадлежат не файлу, а чтению человека.
 *
 * Правило переноса — «заполнить пустое, не перетирая своё»:
 * * прогресс берётся у копии только тогда, когда он свежее (у оригинала нет строки или его запись
 *   старее);
 * * статус берётся, только если книга ещё «не прочитано»: сбрасывать «прочитано» на «читаю» нельзя,
 *   человек мог открыть законченную книгу, чтобы перечитать главу;
 * * избранное и оценка переносятся, если у книги их не было;
 * * дата открытия — самая поздняя из двух, число открытий складывается;
 * * книга попадает во все полки, где была копия (сами связи копии убирает тот, кто её удаляет).
 *
 * Что **не** переносится: закладки, ручные правки метаданных, теги и привязка читалки. Это относится
 * к файлу, а не к чтению: файлы байт-в-байт одинаковы, поэтому у настоящей книги такие же данные, а у
 * копии они могли появиться только случайно (например, карточку копии открыли по ошибке).
 */
class BookUserDataMover @Inject constructor(
    private val bookDao: BookDao,
    private val progressDao: ReadingProgressDao,
    private val collectionDao: CollectionDao,
) {

    /** Перенести данные с [fromBookId] на [toBookId]; перенос книги на себя — не ошибка, а no-op. */
    suspend fun move(fromBookId: String, toBookId: String) {
        if (fromBookId == toBookId) return
        val from = bookDao.getById(fromBookId) ?: return
        val to = bookDao.getById(toBookId) ?: return

        bookDao.update(
            to.copy(
                readingStatus = if (to.readingStatus == ReadingStatus.UNREAD) {
                    from.readingStatus
                } else {
                    to.readingStatus
                },
                favorite = to.favorite || from.favorite,
                rating = to.rating ?: from.rating,
                lastOpenedAt = newest(to.lastOpenedAt, from.lastOpenedAt),
                openCount = to.openCount + from.openCount,
            ),
        )

        moveProgress(fromBookId = fromBookId, toBookId = toBookId)
        addToCollectionsOf(fromBookId = fromBookId, toBookId = toBookId)
    }

    /** Прогресс переезжает, только если он свежее: устаревшим процентом перетирать нельзя. */
    private suspend fun moveProgress(fromBookId: String, toBookId: String) {
        val from = progressDao.getByBook(fromBookId) ?: return
        val to = progressDao.getByBook(toBookId)
        if (to != null && to.updatedAt >= from.updatedAt) return
        progressDao.upsert(from.copy(bookId = toBookId))
    }

    /** Книга попадает в те же полки, где была копия; повторное добавление безвредно. */
    private suspend fun addToCollectionsOf(fromBookId: String, toBookId: String) {
        val collections = collectionDao.collectionIdsOfBook(fromBookId)
        if (collections.isEmpty()) return
        collectionDao.insertBooks(
            collections.map { collectionId ->
                CollectionBookCrossRef(collectionId = collectionId, bookId = toBookId)
            },
        )
    }

    private fun newest(first: Long?, second: Long?): Long? = when {
        first == null -> second
        second == null -> first
        else -> maxOf(first, second)
    }
}
