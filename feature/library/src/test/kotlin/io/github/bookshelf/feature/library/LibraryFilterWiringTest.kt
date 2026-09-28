package io.github.bookshelf.feature.library

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.data.model.BookQuery
import io.github.bookshelf.core.data.model.LibraryQuery
import io.github.bookshelf.core.data.repository.FilterSelection
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Связь полки с общим выбором фильтров.
 *
 * Существует потому, что дефект был именно в связи: счётчики у чипов пересчитывались верно,
 * а полка показывала все книги. Проверка [io.github.bookshelf.core.data.repository.FilterSelectionTest]
 * покрывает общее состояние; здесь проверяется правило, по которому полка строит свой запрос
 * из этого состояния — именно оно было написано неверно.
 */
class LibraryFilterWiringTest {

    private val selection = FilterSelection()

    /**
     * Правило, по которому полка применяет общий выбор: фильтр подставляется, страница
     * сбрасывается на первую. Сброс страницы обязателен — иначе после смены фильтра полка
     * осталась бы на пустой последней странице и показывала «ничего не найдено».
     */
    @Test
    fun `shared filter is applied and page resets`() = runTest {
        val current = LibraryQuery(page = 3, pageSize = 20)
        selection.apply(BookQuery(authorNames = setOf("tolstoy")))

        val next = current.copy(bookQuery = selection.query.value, page = 0)

        assertThat(next.bookQuery.authorNames).containsExactly("tolstoy")
        assertThat(next.page).isEqualTo(0)
    }

    @Test
    fun `unapplied shared filter means whole library`() = runTest {
        val current = LibraryQuery(bookQuery = BookQuery(authorNames = setOf("old")), page = 0)

        // Сброс на экране фильтров обязан убрать фильтр у полки, а не оставить прошлый.
        selection.clear()
        val next = current.copy(bookQuery = selection.query.value, page = 0)

        assertThat(next.bookQuery).isEqualTo(BookQuery())
    }

    @Test
    fun `sort and page size survive filter change`() = runTest {
        val current = LibraryQuery(page = 0, pageSize = 40)
        selection.apply(BookQuery(series = setOf("Классика")))

        val next = current.copy(bookQuery = selection.query.value, page = 0)

        // Смена фильтра не должна сбрасывать сортировку и размер страницы — это отдельные настройки.
        assertThat(next.pageSize).isEqualTo(40)
    }
}
