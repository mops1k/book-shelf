package io.github.bookshelf.core.designsystem.text

import io.github.bookshelf.core.designsystem.R

/**
 * Подпись статуса чтения по имени значения (`UNREAD`, `READING`, `READ`).
 *
 * Живёт в интерфейсе, а не в `:core:data`: тамошний `ReadingStatus.label` отдавал готовые русские
 * слова, и они попадали и в фасеты фильтров, и в карточку книги — в английском интерфейсе
 * оставались русскими (docs/tdd.md §8). Имя значения при этом остаётся кодом фильтра.
 *
 * Неизвестное имя даёт `null`: экран покажет само имя как данные, а не упадёт.
 */
fun readingStatusText(name: String): AppText? = when (name.trim().lowercase()) {
    "unread" -> AppText(R.string.status_unread)
    "reading" -> AppText(R.string.status_reading)
    "read" -> AppText(R.string.status_read)
    else -> null
}

/**
 * Подпись статуса для бейджа на обложке: «читаю 42%».
 *
 * Процент не выдумывается: если читалка его не отдала (`null`), показывается просто «читаю» —
 * «читаю 0%» на свежей книге читалось бы как ошибка.
 */
fun readingStatusBadge(name: String, progressPercent: Int?): AppText? {
    val base = readingStatusText(name) ?: return null
    if (base.res != R.string.status_reading || progressPercent == null) return base
    return AppText(R.string.status_reading_progress, progressPercent)
}
