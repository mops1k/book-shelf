package io.github.bookshelf.core.storage.files

/**
 * Быстрый ключ изменений книги.
 *
 * Алгоритм: `SHA-256(size + lastModified + первые 64 КБ файла)` в hex. Полный контентный хеш
 * (`contentHash`) считается лениво и только для поиска дубликатов, поэтому здесь его нет
 * (docs/architecture.md §3.1).
 *
 * Хеш позволяет инкрементальному скану не перепарсивать неизменённые файлы: при совпадении
 * `size` и `mtime` чтение 64 КБ не нужно вовсе, хеш используется как второй уровень проверки.
 */
interface QuickHasher {

    /** `null`, если файл недоступен или поток не открылся. */
    fun quickHash(documentUri: String, sizeBytes: Long, lastModified: Long): String?
}

/**
 * Реализация на `MessageDigest`; появляется в Sprint 2 вместе с инкрементальным сканом.
 */
class DefaultQuickHasher : QuickHasher {

    override fun quickHash(documentUri: String, sizeBytes: Long, lastModified: Long): String? {
        // STUB(Sprint 2): SHA-256(size + lastModified + первые 64 КБ) через BookContentOpener.
        TODO("STUB(Sprint 2): SHA-256 от size, lastModified и первых 64 КБ файла в hex")
    }
}
