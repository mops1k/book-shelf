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
    fun normalizes_case_after_removing_article() {
        assertThat(BookMetadata("ВОЙНА И МИР").sortTitleOf()).isEqualTo("Война и мир")
        assertThat(BookMetadata("THE TWO TOWERS").sortTitleOf()).isEqualTo("Two towers")
    }
}
