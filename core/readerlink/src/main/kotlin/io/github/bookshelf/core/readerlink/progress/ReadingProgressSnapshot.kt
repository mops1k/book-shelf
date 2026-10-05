package io.github.bookshelf.core.readerlink.progress

/**
 * Снимок прогресса чтения.
 *
 * @param percent прогресс в процентах, 0f..100f.
 * @param pageIndex номер абзаца (или виртуальной страницы), если источник его отдаёт.
 * @param locatorJson точная позиция строкой, если источник её отдаёт; формат определяет адаптер,
 *   доменный слой её не разбирает.
 * @param updatedAt когда читалка записала эту позицию (миллисекунды UTC), если она это сообщает.
 *   Нужен, чтобы при нескольких источниках записать **самую свежую** запись, а не ту, чей источник
 *   спросили позже (решение Александра 05.10.2026). `null` — читалка времени не сообщает (FBReader,
 *   KOReader: в его sidecar времени нет), и тогда порядок источников остаётся решающим.
 */
data class ReadingProgressSnapshot(
    val percent: Float,
    val pageIndex: Int?,
    val locatorJson: String?,
    val updatedAt: Long? = null,
)
