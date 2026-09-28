package io.github.bookshelf.core.covers.decode

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.common.error.appRunCatching
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Декодер обложек на `BitmapFactory` (docs/architecture.md §5).
 *
 * Декодирование обложки — самое дорогое место прокрутки полки, поэтому конвейер трёхступенчатый:
 *
 * 1. **`inJustDecodeBounds` без выделения памяти** — узнаём размер изображения, ничего не читая.
 * 2. **`inSampleSize`** — целочисленное уменьшение ещё на этапе декодирования. Читать картинку
 *    2000×3000 и потом сжимать означает держать в памяти 24 МБ на каждую обложку; на e-ink с
 *    1–2 ГБ RAM это верный путь к `OutOfMemoryError`. Выборка степенью двойки ещё и быстрее.
 * 3. **`RGB_565`** — вдвое меньше памяти, чем `ARGB_8888`. Обложка — это изображение, а не текст:
 *    разница глазом не видна, а на сорока обложках сетки это десятки мегабайт.
 *
 * `invert` применяется цветовой матрицей, а не попиксельной обработкой: `Canvas` с `ColorFilter`
 * делает это в C и не держит промежуточный буфер.
 */
@Singleton
class AndroidCoverDecoder @Inject constructor() : CoverDecoder {

    override fun decode(bytes: ByteArray, maxSidePx: Int, inverted: Boolean): AppResult<Bitmap> =
        decodeInternal(bytes = bytes, targetPx = maxSidePx, invert = inverted)

    override fun decodeTile(bytes: ByteArray, tilePx: Int): AppResult<Bitmap> =
        decodeInternal(bytes = bytes, targetPx = tilePx, invert = false)

    private companion object {
        /** Имя формата для [AppError.Parse]: декодеру неизвестен исходный формат файла. */
        const val FORMAT_NAME = "cover"

        /** Максимум канала в 8-битном RGB. */
        const val COLOR_MAX = 255f
    }

    private fun decodeInternal(bytes: ByteArray, targetPx: Int, invert: Boolean): AppResult<Bitmap> {
        val size = when (val measured = measure(bytes)) {
            is AppResult.Success -> measured.value
            is AppResult.Failure -> return measured
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(size.width, size.height, targetPx)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val decoded = appRunCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) }
        val bitmap = (decoded as? AppResult.Success)?.value
        return if (bitmap == null) {
            AppResult.failure(
                AppError.Parse(format = FORMAT_NAME, message = "Байты не являются изображением"),
            )
        } else {
            // inSampleSize округляет вниз, поэтому результат может остаться больше цели — добираем
            // точным масштабированием.
            val scaled = scaleToFit(bitmap, targetPx)
            AppResult.success(if (invert) invert(scaled) else scaled)
        }
    }

    /**
     * Размер изображения без выделения памяти.
     *
     * Отдельная функция ещё и ради читаемости: [decodeInternal] остаётся с двумя выходами
     * вместо четырёх, а весь отказ-контекст собран здесь.
     */
    private fun measure(bytes: ByteArray): AppResult<Size> {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
            if (bytes.isNotEmpty()) BitmapFactory.decodeByteArray(bytes, 0, bytes.size, this)
        }
        val width = options.outWidth
        val height = options.outHeight
        // Оба признака — «изображения нет»: пустые байты, мусор, обрезанный файл. Различать их
        // пользователю незачем, поэтому один отказ вместо двух.
        return if (width > 0 && height > 0) {
            AppResult.success(Size(width, height))
        } else {
            AppResult.failure(
                AppError.Parse(format = FORMAT_NAME, message = "Байты не содержат изображения"),
            )
        }
    }

    /** Размеры изображения; свой data class вместо android.util.Size, чтобы не тянуть импорт. */
    private data class Size(val width: Int, val height: Int)

    /**
     * Наибольшая степень двойки, при которой сторона не превышает [maxSide].
     *
     * Степень двойки выбрана не случайно: `BitmapFactory` декодирует такой блок оптимальным
     * фильтром, а произвольный коэффициент заставил бы его читать изображение целиком.
     */
    private fun sampleSizeFor(width: Int, height: Int, maxSide: Int): Int {
        if (maxSide <= 0) return 1
        var sample = 1
        var largest = maxOf(width, height)
        while (largest / 2 >= maxSide) {
            largest /= 2
            sample *= 2
        }
        return sample
    }

    private fun scaleToFit(source: Bitmap, maxSide: Int): Bitmap {
        val largestSide = maxOf(source.width, source.height)
        if (maxSide <= 0 || largestSide <= maxSide) return source
        val ratio = maxSide.toFloat() / largestSide
        val width = (source.width * ratio).toInt().coerceAtLeast(1)
        val height = (source.height * ratio).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(source, width, height, true)
        // От recycle освобождаем исходник: на полке десятки обложек, и удержание двух копий
        // каждой удваивает расход памяти ровно там, где он и не нужен.
        if (scaled !== source) source.recycle()
        return scaled
    }

    /** Инверсия цветов матрицей: одна операция вместо цикла по пикселям. */
    private fun invert(source: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.RGB_565)
        val paint = Paint().apply {
            // Каждый канал цвета вычитается из максимума: 255 - c. Альфа не трогаем.
            colorFilter = ColorMatrixColorFilter(
                ColorMatrix(
                    floatArrayOf(
                        -1f, 0f, 0f, 0f, COLOR_MAX,
                        0f, -1f, 0f, 0f, COLOR_MAX,
                        0f, 0f, -1f, 0f, COLOR_MAX,
                        0f, 0f, 0f, 1f, 0f,
                    ),
                ),
            )
        }
        Canvas(output).drawBitmap(source, 0f, 0f, paint)
        source.recycle()
        return output
    }
}
