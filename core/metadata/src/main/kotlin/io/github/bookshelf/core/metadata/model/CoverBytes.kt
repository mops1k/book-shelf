package io.github.bookshelf.core.metadata.model

/**
 * Обложка «как есть»: сырые байты, MIME и размеры, если формат их сообщил.
 *
 * `Bitmap` здесь сознательно нет: декодирование, `inSampleSize`, downscale до 600 px,
 * RGB_565 и запись WebP — задача `:core:covers` (docs/architecture.md §4.4). Парсеры
 * должны оставаться тестируемыми без Android-графики, поэтому они отдают только байты,
 * а решение «декодировать или нет» принимает вызывающий код.
 */
data class CoverBytes(
    val bytes: ByteArray,
    val mimeType: String,
    val width: Int?,
    val height: Int?,
)
