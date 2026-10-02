package io.github.bookshelf.core.covers

import io.github.bookshelf.core.common.error.AppResult
import java.io.File

/**
 * Кэш обложек («Архитектура» §5).
 *
 * Двухуровневый: файлы WebP на диске (источник истины) и `LruCache` в памяти (быстрый доступ при
 * пагинации). Контракт намеренно ничего не знает про Coil и `Bitmap` — им пользуется слой
 * декодирования ([io.github.bookshelf.core.covers.decode.CoverDecoder]).
 *
 * Формат файлов: `filesDir/covers/{bookId}.webp` — полная обложка, `.../{bookId}_t.webp` — тайл
 * сетки («Архитектура» §5). Имена и проверка `bookId` — в
 * [io.github.bookshelf.core.covers.cache.CoverPaths].
 *
 * Инвалидация выполняется при изменении файла книги (`quickHash`); ручное редактирование метаданных
 * обложку не трогает.
 */
interface CoverCache {

    /** Прочитать файл обложки. `AppResult.Success(null)` — обложки ещё нет (это не ошибка). */
    suspend fun read(bookId: String, kind: CoverKind): AppResult<File?>

    /**
     * Сохранить готовые байты WebP.
     *
     * Реализация обязана писать атомарно (`.tmp` + `rename`), чтобы падение в момент записи не
     * оставило битый файл, который потом не декодируется.
     */
    suspend fun store(bookId: String, kind: CoverKind, bytes: ByteArray): AppResult<File>

    /** Удалить обе разновидности обложки книги (полную и тайл). */
    suspend fun invalidate(bookId: String)

    /** Полностью очистить кэш (настройка «Очистить кэш обложек»). */
    suspend fun clear()

    /** Занятый объём в байтах — для строки «Занято кэшем» в настройках. */
    suspend fun sizeBytes(): Long

    /**
     * Путь, по которому лежит (или будет лежать) файл.
     *
     * Не выполняет ввод-вывод — нужен для передачи пути в Coil и для атомарной записи.
     */
    fun fileFor(bookId: String, kind: CoverKind): File

    /** Разновидность обложки: полная для карточки книги и тайл для сетки. */
    enum class CoverKind { FULL, TILE }
}
