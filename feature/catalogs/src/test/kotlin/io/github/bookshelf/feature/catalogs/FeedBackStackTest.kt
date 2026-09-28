package io.github.bookshelf.feature.catalogs

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.data.model.OpdsEntry
import org.junit.Test

/**
 * Стек возврата по разделам каталога.
 *
 * Проверяется ровно то, на что жаловался пользователь: из вложенной категории «Назад» возвращает
 * на одну категорию выше, а не в список каталогов, и делает это без сети (уровни уже загружены).
 */
class FeedBackStackTest {

    @Test
    fun `empty stack means the catalog root`() {
        val stack = FeedBackStack()

        assertThat(stack.canGoBack).isFalse()
        assertThat(stack.pop()).isNull()
    }

    @Test
    fun `pop returns levels in reverse order`() {
        val stack = FeedBackStack()
        stack.push(level(title = "Корень"))
        stack.push(level(title = "Жанры"))

        assertThat(stack.canGoBack).isTrue()
        assertThat(stack.pop()?.feed?.title).isEqualTo("Жанры")
        assertThat(stack.pop()?.feed?.title).isEqualTo("Корень")
        assertThat(stack.canGoBack).isFalse()
    }

    @Test
    fun `level keeps entries and feed url for a request-free return`() {
        val stack = FeedBackStack()
        stack.push(
            FeedLevel(
                entries = listOf(entry(id = "book-1")),
                feed = FeedUi(title = "Фантастика"),
                feedUrl = "https://flibusta.is/opds/genre/1",
            ),
        )

        val restored = stack.pop()

        assertThat(restored?.feedUrl).isEqualTo("https://flibusta.is/opds/genre/1")
        assertThat(restored?.entries?.map { it.id }).containsExactly("book-1")
    }

    @Test
    fun `clear drops the history of the previous catalog`() {
        val stack = FeedBackStack()
        stack.push(level(title = "Чужой каталог"))

        stack.clear()

        assertThat(stack.canGoBack).isFalse()
        assertThat(stack.size).isEqualTo(0)
    }

    private fun level(title: String) = FeedLevel(
        entries = emptyList(),
        feed = FeedUi(title = title),
        feedUrl = null,
    )

    private fun entry(id: String) = OpdsEntry(id = id, title = "Книга")
}
