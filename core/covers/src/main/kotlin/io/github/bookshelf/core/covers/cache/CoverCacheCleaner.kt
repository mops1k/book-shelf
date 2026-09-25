package io.github.bookshelf.core.covers.cache

/**
 * Очистка кэша обложек (docs/architecture.md §5).
 *
 * Отдельный контракт от [io.github.bookshelf.core.covers.CoverCache] нужен потому, что очисткой
 * пользуются фоновые работы (`CoverCleanupWorker`) и настройки, а им не нужен доступ к чтению и
 * записи обложек.
 */
interface CoverCacheCleaner {

    /**
     * Вытеснить обложки по LRU, пока занятый объём не станет не больше [limitBytes].
     *
     * @return сколько байт реально освобождено.
     */
    suspend fun evictToLimit(limitBytes: Long): Long

    /** Полностью очистить кэш (действие «Очистить кэш обложек» в настройках). */
    suspend fun clearAll()
}
