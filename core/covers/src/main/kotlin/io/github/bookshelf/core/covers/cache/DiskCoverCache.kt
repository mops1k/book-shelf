package io.github.bookshelf.core.covers.cache

import io.github.bookshelf.core.common.coroutine.AppDispatchers
import io.github.bookshelf.core.common.di.CoverCacheDir
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.covers.CoverCache
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

/**
 * Дисковый кэш обложек: `filesDir/covers/*.webp` (docs/architecture.md §5).
 *
 * Что должен делать (Sprint 3):
 * * **атомарная запись**: писать в `{file}.tmp` и делать `rename` — иначе после падения процесса
 *   на диске остаётся обрезанный WebP, который не декодируется;
 * * **лимит диска 200 МБ** (настройка `coverCacheLimitMb`, docs/eink-ux.md §2) с вытеснением по LRU;
 * * **LRU по `lastAccessedAt`**: отдельная лёгкая таблица (или `mtime` файлов, если таблицы нет) —
 *   `mtime` доступов на некоторых ФС грубый, поэтому предпочтителен явный учёт;
 * * `invalidate` удаляет и полную обложку, и тайл; `clear` — весь каталог;
 * * `sizeBytes` суммирует размер файлов.
 *
 * Реализация [CoverCacheCleaner] здесь же: вытеснение и очистка — та же ответственность и тот же
 * каталог, отдельный класс только дублировал бы обход файлов.
 *
 * @param cacheDir каталог `filesDir/covers`; создаётся в `:app` (`@CoverCacheDir`).
 */
@Singleton
class DiskCoverCache @Inject constructor(
    @CoverCacheDir private val cacheDir: File,
    private val dispatchers: AppDispatchers,
) : CoverCache, CoverCacheCleaner {

    override suspend fun read(bookId: String, kind: CoverCache.CoverKind): AppResult<File?> =
        withContext(dispatchers.io) {
            TODO("STUB(Sprint 3): проверить файл и вернуть success(file) либо success(null)")
        }

    override suspend fun store(
        bookId: String,
        kind: CoverCache.CoverKind,
        bytes: ByteArray,
    ): AppResult<File> = withContext(dispatchers.io) {
        TODO("STUB(Sprint 3): запись в .tmp + rename, обновление lastAccessedAt")
    }

    override suspend fun invalidate(bookId: String): Unit = withContext(dispatchers.io) {
        TODO("STUB(Sprint 3): удалить {bookId}.webp и {bookId}_t.webp")
    }

    override suspend fun clear(): Unit = withContext(dispatchers.io) {
        TODO("STUB(Sprint 3): удалить содержимое каталога кэша")
    }

    override suspend fun sizeBytes(): Long = withContext(dispatchers.io) {
        TODO("STUB(Sprint 3): сумма размеров файлов каталога")
    }

    override fun fileFor(bookId: String, kind: CoverCache.CoverKind): File =
        CoverPaths.fileFor(cacheDir, bookId, kind)

    override suspend fun evictToLimit(limitBytes: Long): Long = withContext(dispatchers.io) {
        TODO("STUB(Sprint 3): удалять давно не использованные обложки, пока sizeBytes() > limitBytes")
    }

    override suspend fun clearAll(): Unit = withContext(dispatchers.io) {
        TODO("STUB(Sprint 3): полная очистка кэша обложек")
    }
}
