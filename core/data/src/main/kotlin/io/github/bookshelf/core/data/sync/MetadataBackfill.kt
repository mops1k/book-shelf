package io.github.bookshelf.core.data.sync

import io.github.bookshelf.core.database.dao.BookDao
import io.github.bookshelf.core.datastore.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Разовый бэкфилл жанров («Архитектура» §4.4).
 *
 * Зачем: жанры поддержаны позже, чем появился разбор метаданных. У библиотек, разобранных раньше,
 * `parseState = OK`, поэтому обычный разбор такие книги пропускает, и жанры в `tags`/`book_tags`
 * не попадают — умная полка «по жанру» осталась бы пустой на существующей библиотеке.
 *
 * Запускается один раз, отметка хранится в настройках (`library.metadata_backfill_done`): книги,
 * у которых жанров нет вовсе (например, PDF без метаданных), навсегда остаются в выборке
 * `idsWithoutTags`, и без отметки мы разбирали бы их при каждом запуске приложения.
 */
@Singleton
class MetadataBackfill @Inject constructor(
    private val bookDao: BookDao,
    private val scheduler: MetadataScheduler,
    private val settingsRepository: SettingsRepository,
) {

    /** Поставить принудительный разбор книгам без жанров; повторные вызовы ничего не делают. */
    suspend fun runOnce() {
        if (settingsRepository.current().library.metadataBackfillDone) return

        val ids = bookDao.idsWithoutTags()
        ids.forEach { bookId -> scheduler.enqueueMetadata(bookId, force = true) }

        settingsRepository.update { settings ->
            settings.copy(library = settings.library.copy(metadataBackfillDone = true))
        }
    }
}
