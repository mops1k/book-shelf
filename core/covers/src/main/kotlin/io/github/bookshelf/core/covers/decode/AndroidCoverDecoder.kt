package io.github.bookshelf.core.covers.decode

import android.graphics.Bitmap
import io.github.bookshelf.core.common.error.AppResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android-реализация декодера обложек на `BitmapFactory`.
 *
 * Вынесена в отдельный класс (а не в [CoverDecoder]) потому, что декодер — публичный контракт
 * `:core:covers`, а его реализация подменяется в тестах и не должна тянуть `BitmapFactory` в
 * подпись интерфейса.
 *
 * TODO("STUB(Sprint 3)"): реализовать конвейер `inJustDecodeBounds → inSampleSize → RGB_565 →
 *  downscale → инверсия` (см. KDoc [CoverDecoder]) и возвращать `AppResult.Failure(AppError.Parse)`
 *  на битых байтах — исключение наружу не пробрасывается (docs/architecture.md §1).
 */
@Singleton
class AndroidCoverDecoder @Inject constructor() : CoverDecoder {

    override fun decode(bytes: ByteArray, maxSidePx: Int, inverted: Boolean): AppResult<Bitmap> {
        TODO("STUB(Sprint 3): BitmapFactory + inSampleSize + RGB_565 + downscale + инверсия")
    }

    override fun decodeTile(bytes: ByteArray, tilePx: Int): AppResult<Bitmap> {
        TODO("STUB(Sprint 3): уменьшение уже декодированной обложки до tilePx без повторного чтения файла")
    }
}
