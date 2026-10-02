package io.github.bookshelf.core.metadata.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Проверяет чистое правило сортировки без Android-зависимостей. */
class SortTitleTest {

    @Test
    fun strips_cyrillic_articles_only_when_separated() {
        assertThat(BookMetadata("А Книга").sortTitleOf()).isEqualTo("Книга")
        assertThat(BookMetadata("Аа Книга").sortTitleOf()).isEqualTo("Книга")
        assertThat(BookMetadata("аа Книга").sortTitleOf()).isEqualTo("Книга")
        assertThat(BookMetadata("Анна Каренина").sortTitleOf()).isEqualTo("Анна каренина")
    }

    @Test
    fun strips_latin_articles_only_when_separated() {
        assertThat(BookMetadata("An Old Man").sortTitleOf()).isEqualTo("Old man")
        assertThat(BookMetadata("The Two Towers").sortTitleOf()).isEqualTo("Two towers")
        assertThat(BookMetadata("The Beatles").sortTitleOf()).isEqualTo("Beatles")
        assertThat(BookMetadata("TheBeatles").sortTitleOf()).isEqualTo("Thebeatles")
    }

    @Test
    fun keeps_original_article_only_title() {
        assertThat(BookMetadata("А").sortTitleOf()).isEqualTo("А")
        assertThat(BookMetadata("The").sortTitleOf()).isEqualTo("The")
    }

    @Test
    fun strips_latin_single_letter_article() {
        // «А» латинская в списке артиклей была пропущена: «A Tale of Two Cities» уезжала
        // в начало полки, к букве A, хотя её место — у T.
        assertThat(BookMetadata("A Tale of Two Cities").sortTitleOf()).isEqualTo("Tale of two cities")
        assertThat(BookMetadata("a tale of two cities").sortTitleOf()).isEqualTo("Tale of two cities")
        // Без разделителя статья не снимается: это уже другое слово.
        assertThat(BookMetadata("Atlas Shrugged").sortTitleOf()).isEqualTo("Atlas shrugged")
        assertThat(BookMetadata("Anthropology").sortTitleOf()).isEqualTo("Anthropology")
    }

    @Test
    fun ignores_leading_punctuation_and_quotes() {
        // Кавычки и скобки в начале — оформление, а не буква: иначе книги уезжали в конец полки.
        assertThat(BookMetadata("«Тихий Дон»").sortTitleOf()).isEqualTo("Тихий дон")
        assertThat(BookMetadata("\"The Hobbit\"").sortTitleOf()).isEqualTo("Hobbit")
        assertThat(BookMetadata("[1984]").sortTitleOf()).isEqualTo("1984")
        assertThat(BookMetadata("— Тень").sortTitleOf()).isEqualTo("Тень")
    }

    @Test
    fun sorts_yo_as_ye() {
        // В русском «ё» принято сортировать как «е»: иначе «Ёлка» стоит после «Яблока».
        assertThat(BookMetadata("Ёлка").sortTitleOf()).isEqualTo("Елка")
        assertThat(BookMetadata("Королёв").sortTitleOf()).isEqualTo("Королев")
    }

    @Test
    fun normalizes_case_after_removing_article() {
        assertThat(BookMetadata("ВОЙНА И МИР").sortTitleOf()).isEqualTo("Война и мир")
        assertThat(BookMetadata("THE TWO TOWERS").sortTitleOf()).isEqualTo("Two towers")
    }
}
