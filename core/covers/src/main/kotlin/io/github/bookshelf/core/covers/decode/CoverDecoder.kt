package io.github.bookshelf.core.covers.decode

import android.graphics.Bitmap
import io.github.bookshelf.core.common.error.AppResult

/**
 * Декодирование обложек (docs/architecture.md §4.4, §5).
 *
 * Конвейер, который обязана реализовать имплементация:
 * 1. `BitmapFactory.Options.inJustDecodeBounds = true` — узнать размеры, не выделяя память;
 * 2. подобрать `inSampleSize` под [maxSidePx] (степень двойки, иначе качество падает зря);
 * 3. декодировать в `RGB_565` — вчетверо меньше памяти, чем `ARGB_8888`, что критично на e-ink;
 * 4. downscale до [maxSidePx] по длинной стороне и, если нужно, инверсия при декодировании;
 * 5. для тайла сетки — уменьшить до [tilePx] из того же bitmap, не перечитывая файл.
 *
 * Инверсия ([decode] с `inverted = true`) выполняется здесь, а не записью второго файла: настройка
 * «Инвертировать обложки» не должна плодить варианты на диске (docs/architecture.md §5).
 *
 * Кодирование в WebP q[WEBP_QUALITY] — забота вызывающего (парсера/воркера), потому что декодер
 * не знает, будет ли результат сохранён или показан.
 */
interface CoverDecoder {

    /** Полная обложка: длинная сторона не больше [maxSidePx]. */
    fun decode(
        bytes: ByteArray,
        maxSidePx: Int = DEFAULT_MAX_SIDE_PX,
        inverted: Boolean = false,
    ): AppResult<Bitmap>

    /** Тайл для сетки: длинная сторона не больше [tilePx]. */
    fun decodeTile(
        bytes: ByteArray,
        tilePx: Int = DEFAULT_TILE_PX,
    ): AppResult<Bitmap>

    companion object {
        /** Длинная сторона полной обложки (docs/architecture.md §6: «downscale до 600 px»). */
        const val DEFAULT_MAX_SIDE_PX = 600

        /** Длинная сторона тайла сетки (docs/architecture.md §4.4: «тайл 200 px»). */
        const val DEFAULT_TILE_PX = 200

        /** Качество WebP при сохранении в кэш. */
        const val WEBP_QUALITY = 80
    }
}
