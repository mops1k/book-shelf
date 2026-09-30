package io.github.bookshelf.feature.search

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.data.model.Book
import org.junit.Test

/**
 * Подпись «где нашлось совпадение» в строке результата.
 *
 * FTS не сообщает, в каком поле совпал токен, поэтому поле определяется по самому термину.
 * Проверяется и обратное: если ни одно поле совпадение не подтверждает, подпись не выдумывается.
 */
class SearchMatchTest {

    @Test
    fun `title is reported first`() {
        assertThat(book(title = "Солярис", authors = "Станислав Лем").matchedFieldRes("соляр"))
            .isEqualTo(R.string.search_field_title)
    }

    @Test
    fun `author, series, file and description are recognized`() {
        assertThat(book(title = "Солярис", authors = "Станислав Лем").matchedFieldRes("лем"))
            .isEqualTo(R.string.search_field_author)
        assertThat(
            book(title = "Солярис", authors = "Лем", series = "Приключения Флинкса").matchedFieldRes("флинкса"),
        ).isEqualTo(R.string.search_field_series)
        assertThat(book(title = "Солярис", authors = "Лем", path = "Лем/Солярис.fb2").matchedFieldRes("fb2"))
            .isEqualTo(R.string.search_field_file)
        assertThat(
            book(title = "Солярис", authors = "Лем", description = "Планета-океан").matchedFieldRes("океан"),
        ).isEqualTo(R.string.search_field_description)
    }

    @Test
    fun `unconfirmed match yields no label`() {
        // FTS мог найти книгу по стеммингу («planet» → «planets»), а по подстроке поля её нет:
        // выдуманная подпись хуже её отсутствия.
        assertThat(book(title = "Солярис", authors = "Станислав Лем").matchedFieldRes("планета"))
            .isNull()
        assertThat(book(title = "Солярис", authors = "Лем").matchedFieldRes("   ")).isNull()
    }

    private fun book(
        title: String,
        authors: String,
        series: String? = null,
        path: String = "",
        description: String? = null,
    ): Book = Book(
        id = "book-1",
        title = title,
        authorsText = authors,
        extension = "fb2",
        relativePath = path,
        seriesName = series,
        description = description,
        sizeBytes = 1024L,
        addedAt = 1L,
    )
}

