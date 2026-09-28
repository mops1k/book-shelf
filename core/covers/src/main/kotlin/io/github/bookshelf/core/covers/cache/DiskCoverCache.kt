package io.github.bookshelf.core.covers.cache

import io.github.bookshelf.core.common.coroutine.AppDispatchers
import io.github.bookshelf.core.common.di.CoverCacheDir
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.common.error.appRunCatching
import io.github.bookshelf.core.covers.CoverCache
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

/**
 * Дисковый кэш обложек: каталог `filesDir/covers`, файлы `*.webp` (docs/architecture.md §5).
 *
 * ВНИМАНИЕ к синтаксису: в KDoc нельзя писать путь с `*.webp` после слэша — Kotlin допускает
 * вложенные блочные комментарии, и такая последовательность открывает незакрытый комментарий.
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
            val file = CoverPaths.fileFor(cacheDir, bookId, kind)
            if (!file.isFile) {
                return@withContext AppResult.success(null)
            }
            // Файл мог остаться с прошлой установки или быть повреждён: чтение должно падать
            // на конкретную книгу, а не на весь экран.
            appRunCatching { file.readBytes(); file }
        }

    override suspend fun store(
        bookId: String,
        kind: CoverCache.CoverKind,
        bytes: ByteArray,
    ): AppResult<File> = withContext(dispatchers.io) {
        if (!CoverPaths.isSafeBookId(bookId)) {
            return@withContext AppResult.failure(
                AppError.InvalidArgument(what = "bookId", message = "Недопустимый идентификатор книги: $bookId"),
            )
        }
        appRunCatching {
            val target = CoverPaths.fileFor(cacheDir, bookId, kind)
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val temp = CoverPaths.tempFileFor(target)
            // Атомарность: пишем во временный файл и переименовываем. Прямая запись оставила бы
            // после убийства процесса обрезанный WebP, который навсегда перестал бы декодироваться.
            temp.writeBytes(bytes)
            if (!temp.renameTo(target)) {
                temp.delete()
                error("Не удалось переименовать ${temp.name} в ${target.name}")
            }
            target
        }
    }

    override suspend fun invalidate(bookId: String): Unit = withContext(dispatchers.io) {
        CoverCache.CoverKind.entries.forEach { kind ->
            CoverPaths.fileFor(cacheDir, bookId, kind).delete()
        }
        Unit
    }

    override suspend fun clear(): Unit = withContext(dispatchers.io) {
        cacheDir.listFiles()?.forEach { it.delete() }
        Unit
    }

    override suspend fun sizeBytes(): Long = withContext(dispatchers.io) {
        cacheDir.listFiles()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L
    }

    override fun fileFor(bookId: String, kind: CoverCache.CoverKind): File =
        CoverPaths.fileFor(cacheDir, bookId, kind)

    override suspend fun evictToLimit(limitBytes: Long): Long = withContext(dispatchers.io) {
        val files = cacheDir.listFiles()?.filter { it.isFile }?.sortedBy { it.lastModified() }
            ?: return@withContext 0L
        var total = files.sumOf { it.length() }
        var freed = 0L
        for (file in files) {
            val length = file.length()
            // Последний файл не удаляем: пустой кэш после вытеснения ничего не даёт, а лимит
            // пользователь всё равно увидит в настройках. Условие объединено с лимитом,
            // чтобы в цикле остался ровно один переход.
            val fitsLimit = total - length >= limitBytes
            if (total > limitBytes && fitsLimit && file.delete()) {
                total -= length
                freed += length
            }
        }
        freed
    }

    override suspend fun clearAll(): Unit = clear()
}
