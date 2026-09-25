package io.github.bookshelf.core.data.model

/**
 * Элемент фасета: значение фильтра и число книг, которые ему соответствуют.
 *
 * @param id идентификатор значения (для авторов — id, для серий/форматов/тегов — само значение);
 * @param label человекочитаемая подпись для чипа в UI;
 * @param count сколько книг попадёт в результат при выборе этого значения.
 */
data class FacetItem(
    val id: String,
    val label: String,
    val count: Int,
)

/**
 * Полный набор фасетов для текущего состояния полки (docs/architecture.md §3.5, ADR-008).
 *
 * **Фасеты считаются запросом к БД по текущему источнику и активным фильтрам, а не в памяти.**
 * Причина прямая: на 10 000 книг фильтрация в памяти не масштабируется, а выбранный автор должен
 * показывать только те серии, которые у него действительно есть. Поэтому `FilterRepository`
 * выполняет отдельные `COUNT(*)`-запросы с индексами `book_authors(authorId)`, `books(seriesName)`,
 * `books(extension)`, `books(readingStatus)`.
 */
data class FacetSet(
    val authors: List<FacetItem> = emptyList(),
    val series: List<FacetItem> = emptyList(),
    val formats: List<FacetItem> = emptyList(),
    val statuses: List<FacetItem> = emptyList(),
    val tags: List<FacetItem> = emptyList(),
)
