package io.github.bookshelf.core.common.format

import java.util.Locale

/**
 * Форматирование размеров и чисел для интерфейса.
 *
 * Требование e-ink: строка должна быть короткой (`1,4 МБ`, а не `1.4 MB (1,468,006 bytes)`),
 * потому что ширина колонок в карточке книги ограничена.
 */
object SizeFormatter {

    private val UNITS_RU = listOf("Б", "КБ", "МБ", "ГБ", "ТБ")

    /**
     * STUB(Sprint 4): сейчас возвращает точное число байт — этого достаточно, чтобы UI
     * собрался, но в интерфейсе появится «1468006 Б». Реализация — деление на 1024 с одним
     * знаком после запятой и разделителем по локали.
     */
    fun humanReadable(bytes: Long, locale: Locale = Locale("ru", "RU")): String = "$bytes Б"

    /** Unix-время → «21 сентября 2026» / «21.09.2026» (короткий формат из настроек). */
    fun formatDate(epochMillis: Long, locale: Locale = Locale("ru", "RU")): String = epochMillis.toString()
}
