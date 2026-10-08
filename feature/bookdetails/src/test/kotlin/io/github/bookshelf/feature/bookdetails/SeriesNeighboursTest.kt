package io.github.bookshelf.feature.bookdetails

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.data.model.Book
import org.junit.Test

/**
 * Соседи книги в её серии.
 *
 * Требование Александра (08.10.2026): в карточке книги — стрелки «назад/вперёд» по книгам той же
 * серии. Книги приходят уже в порядке чтения (`BookPageSql.SERIES_ORDER`: с номером по возрастанию,
 * без номера — по году и названию), поэтому функция не сортирует, а только находит место книги в
 * списке: порядок обязан остаться в одном месте — в SQL, иначе стрелка «вперёд» вела бы не туда,
 * где книга стоит в списке серии на полке.
 */
class SeriesNeighboursTest {

    @Test
    fun `the second book of three has both neighbours`() {
        val neighbours = seriesNeighbours(series, currentBookId = "s2")

        assertThat(neighbours.previous?.bookId).isEqualTo("s1")
        assertThat(neighbours.previous?.title).isEqualTo("Ксили. Книга 1")
        assertThat(neighbours.next?.bookId).isEqualTo("s3")
        assertThat(neighbours.next?.title).isEqualTo("Ксили. Книга 3")
    }

    @Test
    fun `the first book has only the next one`() {
        val neighbours = seriesNeighbours(series, currentBookId = "s1")

        assertThat(neighbours.previous).isNull()
        assertThat(neighbours.next?.bookId).isEqualTo("s2")
    }

    @Test
    fun `the last book has only the previous one`() {
        val neighbours = seriesNeighbours(series, currentBookId = "s3")

        assertThat(neighbours.previous?.bookId).isEqualTo("s2")
        assertThat(neighbours.next).isNull()
    }

    @Test
    fun `a series of one book has no neighbours`() {
        val neighbours = seriesNeighbours(listOf(book("s1", "Ксили")), currentBookId = "s1")

        assertThat(neighbours.previous).isNull()
        assertThat(neighbours.next).isNull()
    }

    @Test
    fun `a book that is not in the list has no neighbours`() {
        // Серию переименовали, пока карточка была открыта: обещать переходы, которых не будет,
        // нельзя — стрелки обязаны исчезнуть, а не вести в чужую книгу.
        val neighbours = seriesNeighbours(series, currentBookId = "чужой")

        assertThat(neighbours.previous).isNull()
        assertThat(neighbours.next).isNull()
    }

    private fun book(id: String, title: String) = Book(
        id = id,
        title = title,
        authorsText = "Стивен Бакстер",
        extension = "fb2",
        sizeBytes = 1_024L,
        addedAt = 1_000L,
    )

    private val series = listOf(
        book("s1", "Ксили. Книга 1"),
        book("s2", "Ксили. Книга 2"),
        book("s3", "Ксили. Книга 3"),
    )
}
