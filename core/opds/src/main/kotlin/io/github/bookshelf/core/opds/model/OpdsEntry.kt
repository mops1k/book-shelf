package io.github.bookshelf.core.opds.model

import kotlinx.serialization.Serializable

/**
 * Запись фида OPDS (`atom:entry`).
 *
 * Поля собраны из метаданных, обязательных по docs/opds.md §1: `title`, `author/name`,
 * `dc:language`, `dc:issued`, `dc:identifier` (ISBN), `dc:publisher`, `summary`/`content`,
 * `category`, `dcterms:extent`. Здесь оставлены только те, что нужны полке; остальное
 * сохраняется парсером при необходимости.
 *
 * @param id `atom:id` или `dc:identifier` — ключ дедупликации скачанного.
 * @param title название книги.
 * @param authors авторы в порядке объявления.
 * @param summary аннотация (`summary`/`content`) без разметки.
 * @param updated дата обновления записи в ISO-8601; null, если не указана.
 * @param links все ссылки записи (приобретение, обложка, alternate).
 * @param coverUrl вычисленная обложка (`.../image` или `.../image/thumbnail`); null, если нет.
 * @param language `dc:language`.
 * @param seriesName серия; null, если каталог её не сообщает.
 * @param seriesIndex номер в серии; Double, так как встречаются дробные значения.
 * @param dcIdentifier `dc:identifier` (ISBN и т. п.) — используется для дедупликации.
 * @param issued `dc:issued` — год издания.
 * @param genres жанры из `category/@term` в порядке объявления.
 * @param authorUrl фид автора (`rel="related"`), если каталог его объявил: по нему открывается
 *   список его книг и серий, вместо того чтобы искать автора поиском.
 * @param seriesUrl фид серии (`rel="related"`), если каталог его объявил.
 */
@Serializable
data class OpdsEntry(
    val id: String,
    val title: String,
    val authors: List<String> = emptyList(),
    val summary: String? = null,
    val updated: String? = null,
    val links: List<OpdsLink> = emptyList(),
    val coverUrl: String? = null,
    val language: String? = null,
    val seriesName: String? = null,
    val seriesIndex: Double? = null,
    val dcIdentifier: String? = null,
    val issued: String? = null,
    val genres: List<String> = emptyList(),
    val authorUrl: String? = null,
    val seriesUrl: String? = null,
)

/**
 * Лучшая ссылка приобретения для записи.
 *
 * Сначала ищем ссылку с MIME из [preferredMimeTypes] в порядке предпочтения (например,
 * `application/epub+zip` раньше `application/pdf`), затем — просто первую ссылку приобретения.
 * null означает, что скачать запись нельзя (например, только `sample`/`buy` без прямого файла).
 */
fun OpdsEntry.bestAcquisitionLink(preferredMimeTypes: List<String>): OpdsLink? {
    val acquisitions = links.filter { it.isAcquisition() }
    for (mime in preferredMimeTypes) {
        val preferred = acquisitions.firstOrNull { it.type.equals(mime, ignoreCase = true) }
        if (preferred != null) return preferred
    }
    return acquisitions.firstOrNull()
}
