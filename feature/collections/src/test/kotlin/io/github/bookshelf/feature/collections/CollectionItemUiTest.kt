package io.github.bookshelf.feature.collections

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.data.model.Book
import io.github.bookshelf.core.data.model.ReadingStatus
import io.github.bookshelf.core.data.model.badge
import org.junit.Test

/**
 * Преобразование книги домена в элемент полки.
 *
 * Проверяется то, от чего зависит плитка содержимого полки: обложка, статус чтения и автор.
 * Пока это преобразование жило кодом внутри `combine`, ни один тест его не касался — и полка
 * показывала книги списком строк без обложек и статуса.
 */
class CollectionItemUiTest {

    @Test
    fun `book keeps cover, status and authors in the shelf item`() {
        val item = book(
            title = "Солярис",
            authors = "Лем Станислав",
            coverPath = "/covers/solaris.webp",
            status = ReadingStatus.READING,
        ).toCollectionItemUi()

        assertThat(item.id).isEqualTo(BOOK_ID)
        assertThat(item.title).isEqualTo("Солярис")
        assertThat(item.authors).isEqualTo("Лем Станислав")
        assertThat(item.extension).isEqualTo("fb2")
        assertThat(item.coverPath).isEqualTo("/covers/solaris.webp")
        assertThat(item.readingStatus).isEqualTo(ReadingStatus.READING)
    }

    @Test
    fun `book without cover and authors stays drawable`() {
        val item = book(authors = "", coverPath = null).toCollectionItemUi()

        // Обложки нет — плитка рисует формат в углу обложки, а не пустой прямоугольник.
        assertThat(item.coverPath).isNull()
        // Автора нет — экран подставляет формат: пустая строка выглядела бы как потеря данных.
        assertThat(item.authors).isEmpty()
        assertThat(item.extension).isEqualTo("fb2")
    }

    @Test
    fun `reading progress is rounded to whole percents`() {
        val item = book(status = ReadingStatus.READING, progress = 42.4f).toCollectionItemUi()

        // Бейдж на обложке показывает «читаю 42%»: дробь читалке не важна, а «42.4%» не влезает.
        assertThat(item.progressPercent).isEqualTo(42)
        assertThat(item.readingStatus.badge(item.progressPercent)).isEqualTo("читаю 42%")
    }

    @Test
    fun `progress outside 0-100 is clamped`() {
        assertThat(book(progress = -5f).toCollectionItemUi().progressPercent).isEqualTo(0)
        assertThat(book(progress = 150f).toCollectionItemUi().progressPercent).isEqualTo(100)
        assertThat(book(progress = null).toCollectionItemUi().progressPercent).isNull()
    }

    private fun book(
        title: String = "Книга",
        authors: String = "Автор",
        coverPath: String? = null,
        status: ReadingStatus = ReadingStatus.UNREAD,
        progress: Float? = null,
    ): Book = Book(
        id = BOOK_ID,
        title = title,
        authorsText = authors,
        authors = listOfNotNull(authors.ifBlank { null }),
        coverPath = coverPath,
        extension = "fb2",
        sizeBytes = 1024,
        readingStatus = status,
        progressPercent = progress,
        addedAt = 1_700_000_000_000,
    )

    private companion object {
        const val BOOK_ID = "doc:abc"
    }
}
