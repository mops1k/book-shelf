package io.github.bookshelf.feature.search

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Пустой раздел «Результаты» на экране поиска.
 *
 * Дефект, ради которого это проверяется (найден на устройстве 30.09.2026): раздел считался пустым
 * только по `results.isEmpty()`, поэтому **во время набора** запроса экран сообщал «По запросу
 * «litres» совпадений нет», хотя поиск ещё не запускали. После кнопки «Найти» те же «litres»
 * находили 28 книг — то есть текст прямо врал человеку про его библиотеку.
 *
 * Тексты теперь живут в `res/values` (ru) и `values-en`, поэтому тест фиксирует **выбранный
 * вариант**, а не строку: до поиска выбран ресурс «Нажмите «Найти»», а не «Ничего не найдено».
 */
class SearchEmptyStateTest {

    @Test
    fun `before a search the screen asks to press the button`() {
        val state = searchEmptyState(query = "толстой", searchedTerm = null)

        assertThat(state.titleRes).isEqualTo(R.string.search_empty_press_find)
        assertThat(state.messageRes).isEqualTo(R.string.search_empty_press_find_message)
        // Главное: экран не утверждает, что совпадений нет.
        assertThat(state.titleRes).isNotEqualTo(R.string.search_empty_nothing_found)
        assertThat(state.messageArg).isNull()
    }

    @Test
    fun `an empty query asks for a query`() {
        val state = searchEmptyState(query = "   ", searchedTerm = null)

        assertThat(state.titleRes).isEqualTo(R.string.search_empty_enter_query)
    }

    @Test
    fun `after a search the empty result is reported honestly`() {
        val state = searchEmptyState(query = "толстой", searchedTerm = "толстой")

        assertThat(state.titleRes).isEqualTo(R.string.search_empty_nothing_found)
        // Подставляется именно тот термин, который искали.
        assertThat(state.messageArg).isEqualTo("толстой")
    }

    @Test
    fun `the message names the term that was searched, not the one being typed`() {
        // Человек искал «толстой», потом начал править запрос: сообщение обязано говорить о том,
        // что искали, иначе оно снова врёт.
        val state = searchEmptyState(query = "толстой война", searchedTerm = "толстой")

        assertThat(state.messageArg).isEqualTo("толстой")
    }
}
