package io.github.bookshelf.core.readerlink.progress

/**
 * Снимок прогресса чтения.
 *
 * @param percent прогресс в процентах, 0f..100f.
 * @param pageIndex номер абзаца (или виртуальной страницы), если источник его отдаёт.
 * @param locatorJson точная позиция строкой, если источник её отдаёт; формат определяет адаптер,
 *   доменный слой её не разбирает.
 */
data class ReadingProgressSnapshot(
    val percent: Float,
    val pageIndex: Int?,
    val locatorJson: String?,
)
