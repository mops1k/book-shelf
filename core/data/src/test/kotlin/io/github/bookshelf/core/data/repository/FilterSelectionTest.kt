package io.github.bookshelf.core.data.repository

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.data.model.BookQuery
import io.github.bookshelf.core.data.model.ReadingStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Общий выбор фильтров между полкой и экраном фильтров.
 *
 * Тест на то, что полка применяет именно то, что выбрал пользователь. До появления общего
 * состояния каждый ViewModel держал своё: счётчики у чипов пересчитывались верно, а полка
 * показывала все книги — и ни один тест этого не ловил, потому что проверялись репозитории
 * по отдельности, а не связь между двумя экранами.
 */
class FilterSelectionTest {

    private val selection = FilterSelection()

    @Test
    fun starts_with_empty_query() = runTest {
        assertThat(selection.query.first()).isEqualTo(BookQuery())
    }

    @Test
    fun applied_query_is_visible_to_shelf() = runTest {
        selection.apply(BookQuery(authorNames = setOf("tolstoy"), series = setOf("Классика")))

        val onShelf = selection.query.first()
        assertThat(onShelf.authorNames).containsExactly("tolstoy")
        assertThat(onShelf.series).containsExactly("Классика")
    }

    @Test
    fun clear_returns_to_whole_library() = runTest {
        selection.apply(BookQuery(authorNames = setOf("tolstoy")))
        selection.clear()

        // Сброс без явного вызова apply: иначе экран фильтров продолжил бы показывать старые чипы.
        assertThat(selection.query.first()).isEqualTo(BookQuery())
    }

    @Test
    fun statuses_survive_round_trip() = runTest {
        selection.apply(BookQuery(statuses = setOf(ReadingStatus.READ)))

        assertThat(selection.query.first().statuses).containsExactly(ReadingStatus.READ)
    }

    @Test
    fun repeated_apply_of_same_query_is_ignored() = runTest {
        val query = BookQuery(authorNames = setOf("a"))
        selection.apply(query)
        selection.apply(query)

        // Смена состояния без изменения значения перезапустила бы подписку полки вхолостую.
        assertThat(selection.query.first()).isEqualTo(query)
    }

    @Test
    fun filter_by_author_and_format_combined() = runTest {
        selection.apply(BookQuery(authorNames = setOf("a"), formats = setOf("fb2")))

        val applied = selection.query.first()
        assertThat(applied.authorNames).containsExactly("a")
        assertThat(applied.formats).containsExactly("fb2")
    }
}
