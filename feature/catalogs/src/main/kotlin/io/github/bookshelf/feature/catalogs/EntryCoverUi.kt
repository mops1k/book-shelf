package io.github.bookshelf.feature.catalogs

/**
 * Откуда карточка записи берёт обложку.
 *
 * @param path путь к файлу в локальном кэше (книга уже скачана и разобрана);
 * @param url ссылка на обложку из фида OPDS.
 */
data class EntryCoverUi(
    val path: String? = null,
    val url: String? = null,
)

/**
 * Источник обложки для карточки записи фида.
 *
 * Локальный файл важнее ссылки: он работает без сети и не тратит трафик на e-ink. Ссылку из фида
 * (`.../image` или `.../image/thumbnail`) карточка раньше не передавала вовсе — у записи OPDS
 * `coverPath` всегда `null`, поэтому в списке книг рисовалась заглушка с названием, хотя обложка
 * в фиде есть (дефект найден на устройстве 28.09.2026). На экране отдельной книги ссылка
 * передавалась, поэтому обложка была только там.
 *
 * Оба поля `null` — обложки нет ни локально, ни в фиде: `BookCover` нарисует заглушку.
 */
fun entryCoverOf(entry: FeedEntryUi): EntryCoverUi = when {
    entry.coverPath != null -> EntryCoverUi(path = entry.coverPath)
    else -> EntryCoverUi(url = entry.coverUrl)
}
