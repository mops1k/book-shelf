package io.github.bookshelf.feature.settings

import io.github.bookshelf.core.datastore.model.BYTES_PER_MEGABYTE
import io.github.bookshelf.feature.settings.R

/**
 * Варианты лимитов диска из раздела «Библиотека и скан», МБ.
 *
 * Один список на экран и на проверку в [SettingsViewModel]: если бы экран рисовал свои значения,
 * а ViewModel проверяла другие, «лишний» вариант молча не сохранялся бы — пользователь нажимал бы
 * чип, и ничего не происходило. Наборы подобраны по документации: лимит кэша обложек
 * (docs/architecture.md §5) и лимит временной копии книги (§4.4).
 */
internal val COVER_CACHE_LIMIT_CHOICES_MB: List<Int> = listOf(100, 200, 500)

/** Лимит временной копии книги для облачных источников, МБ (docs/architecture.md §4.4). */
internal val TEMP_COPY_LIMIT_CHOICES_MB: List<Int> = listOf(50, 100, 200)

/**
 * Размер словами по правилу локализации: число считает эта функция, единицу даёт ресурс.
 *
 * Единственное место, где считается освобождённый объём: раньше рядом жила копия, склеивавшая
 * готовую строку («12,4 МБ»), и две реализации расходились бы в подписях (1024 против 1000,
 * запятая против точки).
 */
internal fun freedBytesText(bytes: Long): SettingsText {
    val safe = bytes.coerceAtLeast(0L)
    return when {
        safe < BYTES_PER_KILOBYTE -> SettingsText(R.string.settings_freed_bytes, safe)
        safe < BYTES_PER_MEGABYTE -> SettingsText(R.string.settings_freed_kb, safe / BYTES_PER_KILOBYTE)
        else -> SettingsText(R.string.settings_freed_mb, tenthsOfMegabyte(safe))
    }
}

/** Десятые доли мегабайта с запятой: «12,4» — так объём читается по-русски. */
private fun tenthsOfMegabyte(safeBytes: Long): String {
    val tenths = safeBytes * TENTHS_PER_UNIT / BYTES_PER_MEGABYTE
    return "${tenths / TENTHS_PER_UNIT},${tenths % TENTHS_PER_UNIT}"
}

/** 1 КБ — 1024 байта: так объём считает файловая система. */
private const val BYTES_PER_KILOBYTE: Long = 1024L

/** Сколько десятых долей в единице: нужно для «12,4 МБ». */
private const val TENTHS_PER_UNIT: Long = 10L
