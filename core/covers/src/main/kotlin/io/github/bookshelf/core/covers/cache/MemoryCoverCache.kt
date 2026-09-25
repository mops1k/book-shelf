package io.github.bookshelf.core.covers.cache

import android.graphics.Bitmap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Кэш обложек в памяти (docs/architecture.md §5).
 *
 * Что должен делать (Sprint 3): `LruCache<String, Bitmap>` размером `1/8 maxMemory`, но **не более
 * 32 МБ** — на e-ink с 1–2 ГБ RAM и без swap переполнение памяти означает немедленное убийство
 * процесса. Ключ кэша — путь файла плюс флаг инверсии: инверсия применяется при декодировании и
 * не создаёт второй файл на диске.
 *
 * Размер записи считается в КБ через `sizeOf` (ширина × высота × 2 байта для `RGB_565`, /1024),
 * а не по числу элементов: обложки одного размера только в теории.
 *
 * Константы публичны: по ним настраивается `LruCache` и их же проверяет тест бюджета памяти.
 */
@Singleton
class MemoryCoverCache @Inject constructor() {

    /** Доля `maxMemory`, отдаваемая под кэш обложек. */
    companion object {
        const val MAX_MEMORY_DIVISOR = 8

        /** Жёсткий потолок кэша: 32 МБ (docs/architecture.md §5). */
        const val MAX_CACHE_BYTES = 32L * 1024L * 1024L
    }

    /** Вернуть обложку из памяти или `null`, если её там нет. */
    fun get(key: String): Bitmap? = TODO("STUB(Sprint 3): LruCache.get(key)")

    /** Положить обложку в память (размер в КБ вычисляется в `sizeOf`). */
    fun put(key: String, bitmap: Bitmap) {
        TODO("STUB(Sprint 3): LruCache.put(key, bitmap)")
    }

    /** Очистить кэш — вызывается при «Очистить кэш обложек» и при низкой памяти. */
    fun clear() {
        TODO("STUB(Sprint 3): LruCache.evictAll()")
    }
}
