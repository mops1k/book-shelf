package io.github.bookshelf.feature.bookdetails

import io.github.bookshelf.core.data.model.Book

/** Ссылка на книгу серии: стрелкам нужны только её идентификатор и название. */
data class SeriesNeighbourUi(
    /** `books.id` соседней книги. */
    val bookId: String,
    /** Название книги — для подписи «Предыдущая книга серии: …». */
    val title: String,
)

/** Соседи книги в её серии: предыдущая и следующая по порядку чтения. */
data class SeriesNeighbours(
    /** Более ранняя книга серии; `null` — книга первая или серии нет. */
    val previous: SeriesNeighbourUi? = null,
    /** Следующая книга серии; `null` — книга последняя или серии нет. */
    val next: SeriesNeighbourUi? = null,
)

/**
 * Соседи книги в списке серии.
 *
 * Требование Александра (08.10.2026): в карточке книги — стрелки «назад/вперёд» по книгам той же
 * серии. Книги приходят уже в порядке чтения (`BookPageSql.SERIES_ORDER`: с номером по возрастанию,
 * без номера — по году и названию), поэтому функция не сортирует, а только находит место книги в
 * списке. Порядок обязан остаться в одном месте — в SQL: иначе стрелка «вперёд» вела бы не туда,
 * где книга стоит в списке серии на полке.
 *
 * Книга не найдена в списке (например, серию переименовали, пока карточка была открыта) — соседей
 * нет: обещать переход, которого не будет, нельзя.
 */
fun seriesNeighbours(books: List<Book>, currentBookId: String): SeriesNeighbours {
    val index = books.indexOfFirst { book -> book.id == currentBookId }
    if (index < 0) return SeriesNeighbours()
    return SeriesNeighbours(
        previous = books.getOrNull(index - 1)?.toNeighbour(),
        next = books.getOrNull(index + 1)?.toNeighbour(),
    )
}

private fun Book.toNeighbour() = SeriesNeighbourUi(bookId = id, title = title)
