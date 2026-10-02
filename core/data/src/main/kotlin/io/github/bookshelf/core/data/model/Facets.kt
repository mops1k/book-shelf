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
    /**
     * Значение подписи, а не текст интерфейса.
     *
     * Для жанров это код метаданных (`sf_space`): слова выбирает экран (`genreText`), потому что
     * у слоя данных нет ресурсов, а готовая русская строка из ядра показывалась бы по-русски
     * и в английском интерфейсе. Для авторов и серий это имя — данные, они так и показываются.
     */
    val labelCode: String,
    val count: Int,
)

/**
 * Полный набор фасетов для текущего состояния полки («Архитектура» §3.5, ADR-008).
 *
 * **Фасеты считаются запросом к БД по текущему источнику и активным фильтрам, а не в памяти.**
 * Причина прямая: на 10 000 книг фильтрация в памяти не масштабируется, а выбранный автор должен
 * показывать только те серии, которые у него действительно есть. Поэтому `FilterRepository`
 * выполняет отдельные `COUNT(*)`-запросы с индексами `books(authorsText)`, `books(seriesName)`,
 * `books(extension)`, `books(readingStatus)`.
 */
data class FacetSet(
    val authors: List<FacetItem> = emptyList(),
    val series: List<FacetItem> = emptyList(),
    val formats: List<FacetItem> = emptyList(),
    val statuses: List<FacetItem> = emptyList(),
    val tags: List<FacetItem> = emptyList(),
    /** Источники (папки): `id` — id источника, подпись — имя папки (может быть пустым). */
    val sources: List<FacetItem> = emptyList(),
)

/**
 * Серии одного автора для обзорного режима полки.
 *
 * @param series серии автора с числом книг, по алфавиту;
 * @param withoutSeries сколько книг автора лежит вне серий: в обзоре это отдельная категория
 *   «Вне серий», иначе такие книги нельзя было бы найти без поиска.
 */
data class AuthorSeries(
    val series: List<FacetItem> = emptyList(),
    val withoutSeries: Int = 0,
)
