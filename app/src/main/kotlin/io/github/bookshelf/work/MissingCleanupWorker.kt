package io.github.bookshelf.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import io.github.bookshelf.core.database.dao.BookDao
import io.github.bookshelf.core.database.dao.SourceDao
import kotlinx.coroutines.flow.first
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Удаление записей о пропавших файлах старше N дней (настройка, по умолчанию 30).
 *
 * Важно: удаляется только запись о книге, файл не трогается — приложение никогда не удаляет
 * пользовательские файлы.
 *
 * STUB(Sprint 9): тело пустое.
 */
@HiltWorker
class MissingCleanupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val bookDao: BookDao,
    private val sourceDao: SourceDao,
) : CoroutineWorker(appContext, params) {

    /**
     * Пометить книги, чей файл пропал с диска.
     *
     * Скан помечает пропавшие сразу, но источник мог отключиться между сканами: тогда книга
     * осталась бы в полки навсегда. Здесь проходим по всем источникам.
     *
     * **Книги помечаются, а не удаляются** — история чтения и полки дороже дискового места.
     */
    override suspend fun doWork(): Result = runCatching {
        val now = System.currentTimeMillis()
        // Источники берутся из БД, а не подставляются строкой: пустой sourceId пометил бы
        // пропавшими книги ВСЕХ источников, и полка молча опустела бы.
        sourceDao.observeAll().first().filter { it.enabled }.forEach { source ->
            bookDao.idsBySource(source.id)
                .chunked(BATCH)
                .forEach { bookDao.markMissing(it, now) }
        }
        Result.success()
    }.getOrElse { Result.retry() }

    private companion object {
        /** Порция по 500: SQLite ограничивает число параметров в IN. */
        const val BATCH = 500
    }
}
