package io.github.bookshelf.feature.catalogs

import java.util.Locale

/** Русская локаль: в подписях дробная часть отделяется запятой, а не точкой. */
internal val russianLocale: Locale = Locale.forLanguageTag("ru")

/** Килобайт и мегабайт — для подписи размера файла в списках каталога. */
private const val BYTES_IN_KILOBYTE = 1024L
private const val BYTES_IN_MEGABYTE = 1024.0 * 1024.0

/**
 * Подпись размера файла: «1,2 МБ», «730 КБ».
 *
 * Одна на весь модуль: размер показывают и листинг каталога, и карточка книги, а две копии
 * округления разошлись бы в подписях.
 */
internal fun fileSizeLabel(bytes: Long): String {
    val megabytes = bytes / BYTES_IN_MEGABYTE
    return if (megabytes >= 1.0) {
        String.format(russianLocale, "%.1f МБ", megabytes)
    } else {
        "${bytes / BYTES_IN_KILOBYTE} КБ"
    }
}
