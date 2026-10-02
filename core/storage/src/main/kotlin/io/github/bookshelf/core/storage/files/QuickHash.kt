package io.github.bookshelf.core.storage.files

import java.security.MessageDigest

/**
 * Быстрый ключ изменений книги.
 *
 * Алгоритм: `SHA-256(size + lastModified + первые 64 КБ файла)` в hex. Полный контентный хеш
 * (`contentHash`) считается лениво и только для поиска дубликатов, поэтому здесь его нет
 * («Архитектура» §3.1).
 *
 * Хеш позволяет инкрементальному скану не перепарсивать неизменённые файлы: при совпадении
 * `size` и `mtime` чтение 64 КБ не нужно вовсе, хеш используется как второй уровень проверки.
 */
interface QuickHasher {

    /** `null`, если файл недоступен или поток не открылся. */
    fun quickHash(documentUri: String, sizeBytes: Long, lastModified: Long): String?
}

/**
 * Реализация на `MessageDigest`.
 *
 * Метаданные кодируются разделителем, чтобы значения `1:23` и `12:3` не давали одинаковый вход;
 * это технический ключ, а не криптографическая подпись содержимого.
 */
class DefaultQuickHasher(
    private val contentOpener: BookContentOpener,
) : QuickHasher {

    override fun quickHash(documentUri: String, sizeBytes: Long, lastModified: Long): String? {
        val input = contentOpener.openStream(documentUri) ?: return null
        return input.use { stream ->
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update("$sizeBytes:$lastModified:".toByteArray(Charsets.UTF_8))
            val buffer = ByteArray(MAX_PREFIX_BYTES)
            var offset = 0
            while (offset < buffer.size) {
                val read = stream.read(buffer, offset, buffer.size - offset)
                if (read < 0) break
                if (read == 0) continue
                offset += read
            }
            digest.update(buffer, 0, offset)
            digest.digest().toHexString()
        }
    }

    private fun ByteArray.toHexString(): String = joinToString(separator = "") { byte ->
        val value = byte.toInt() and BYTE_MASK
        HEX_DIGITS[value ushr NIBBLE_SHIFT].toString() + HEX_DIGITS[value and LOW_NIBBLE_MASK]
    }

    private companion object {

        const val MAX_PREFIX_BYTES = 64 * 1024
        const val HEX_DIGITS = "0123456789abcdef"

        /** Маска байта без знака: для байтов ≥ 0x80 `toInt()` даёт отрицательные значения. */
        const val BYTE_MASK = 0xFF

        /** Сдвиг, отделяющий старшую тетраду байта. */
        const val NIBBLE_SHIFT = 4

        /** Маска младшей тетрады байта. */
        const val LOW_NIBBLE_MASK = 0x0F
    }
}
