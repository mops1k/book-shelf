package io.github.bookshelf.core.data.search

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Построение FTS-запроса из ввода пользователя.
 *
 * Регрессия, ради которой появился тест (устройство Bigme B6, Android 14, 2026-09-28): запрос
 * склеивался через " AND ", но в standard query syntax `AND` — обычный токен, а не оператор.
 * «steel remains» не находил ни одной из трёх книг, где есть оба слова, а «techniques ways»
 * «находил» книгу только потому, что в названии есть буквальное слово «and».
 */
class FtsQueryTest {

    @Test
    fun words_are_joined_by_space() {
        assertThat(ftsQueryOf("steel remains")).isEqualTo("steel remains")
    }

    @Test
    fun many_words_are_joined_by_space_too() {
        assertThat(ftsQueryOf("война и мир толстой")).isEqualTo("война и мир толстой")
    }

    @Test
    fun and_token_never_appears_in_the_query() {
        assertThat(ftsQueryOf("толстой война")).doesNotContain("AND")
    }

    @Test
    fun punctuation_becomes_a_separator() {
        assertThat(ftsQueryOf("сталь, остаётся!")).isEqualTo("сталь остаётся")
    }

    @Test
    fun fts_service_characters_are_removed() {
        assertThat(ftsQueryOf("\"война\" (мир)*")).isEqualTo("война мир")
    }

    @Test
    fun digits_and_cyrillic_are_kept() {
        assertThat(ftsQueryOf("fb2 2020")).isEqualTo("fb2 2020")
        assertThat(ftsQueryOf("каренина")).isEqualTo("каренина")
    }

    @Test
    fun blank_input_means_no_query_at_all() {
        assertThat(ftsQueryOf(null)).isNull()
        assertThat(ftsQueryOf("")).isNull()
        assertThat(ftsQueryOf("   ")).isNull()
        assertThat(ftsQueryOf("!!! ???")).isNull()
    }
}
