package io.github.bookshelf.core.covers.cache

import android.graphics.Bitmap
import android.util.LruCache
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Кэш обложек в памяти (docs/architecture.md §5).
 *
 * Что делает: `LruCache<String, Bitmap>` размером `1/8 maxMemory`, но **не более 32 МБ** — на
 * e-ink с 1–2 ГБ RAM и без swap переполнение памяти означает немедленное убийство процесса.
 * Ключ кэша — путь файла плюс флаг инверсии: инверсия применяется при декодировании и не создаёт
 * второй файл на диске.
 *
 * Размер записи считается в КБ через `sizeOf` (ширина × высота × 2 байта для `RGB_565`, /1024),
 * а не по числу элементов: обложки одного размера только в теории.
 *
 * Константы публичны: по ним настраивается `LruCache` и их же проверяет тест бюджета памяти.
 */
@Singleton
class MemoryCoverCache @Inject constructor() {

    /**
     * Ёмкость в КБ: доля доступной памяти, но не больше жёсткого потолка.
     *
     * В JVM и в unit-тестах `Runtime.maxMemory()` иногда возвращает очень большое значение,
     * поэтому без потолка кэш на тестах вырос бы до гигабайтов.
     */
    private val maxSizeKb: Int = minOf(
        (Runtime.getRuntime().maxMemory() / MAX_MEMORY_DIVISOR / BYTES_PER_KB).toInt(),
        (MAX_CACHE_BYTES / BYTES_PER_KB).toInt(),
    )

    private val cache = object : LruCache<String, Bitmap>(maxSizeKb) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.byteCount / BYTES_PER_KB).toInt().coerceAtLeast(1)
    }

    /** Возвращает обложку из памяти или `null`, если её там нет. */
    fun get(key: String): Bitmap? = cache.get(key)

    /** Кладёт обложку в память; размер записи считается в [sizeOf]. */
    fun put(key: String, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }

    /** Очищает кэш — «Очистить кэш обложек» и реакция на низкую память. */
    fun clear() {
        cache.evictAll()
    }

    /** Текущий размер кэша в КБ. */
    fun sizeKb(): Int = cache.size()

    /** Ёмкость кэша в КБ. */
    fun maxSizeKb(): Int = maxSizeKb

    /** Доля `maxMemory`, отдаваемая под кэш обложек. */
    companion object {
        const val MAX_MEMORY_DIVISOR = 8

        /** Жёсткий потолок кэша: 32 МБ (docs/architecture.md §5). */
        const val MAX_CACHE_BYTES = 32L * 1024L * 1024L

        private const val BYTES_PER_KB = 1024L
    }
}
