package io.github.bookshelf.core.data.mapper

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.data.model.BookQuery
import org.junit.Test

/**
 * Зависимость фасетов от текущего выбора (docs/architecture.md §3.5, ADR-008).
 *
 * Контракт: фасет считается по предикату списка книг, но БЕЗ собственного фильтра. Именно это
 * даёт «выбрал автора → серии только его, жанры только его» и при этом не мешает выбрать второго
 * автора. Проверка идёт по SQL: сборка запроса — чистая функция, без базы.
 */
class FacetSqlTest {

    @Test
    fun `series facet keeps the author filter but drops its own`() {
        val sql = FacetSql.series(
            BookQuery(authorNames = setOf("Лев Толстой"), series = setOf("Хроники")),
        ).sql

        assertThat(sql).contains("books.authorsText IN")
        assertThat(sql).doesNotContain("books.seriesName IN")
        assertThat(sql).contains("GROUP BY books.seriesName")
    }

    @Test
    fun `authors facet keeps the series filter but drops its own`() {
        val sql = FacetSql.authors(
            BookQuery(authorNames = setOf("Лев Толстой"), series = setOf("Хроники")),
        ).sql

        assertThat(sql).contains("books.seriesName IN")
        assertThat(sql).doesNotContain("books.authorsText IN")
    }

    @Test
    fun `tags facet follows the series filter and drops its own`() {
        val sql = FacetSql.tags(
            BookQuery(series = setOf("Хроники"), tags = setOf("sf_space")),
        ).sql

        assertThat(sql).contains("books.seriesName IN")
        assertThat(sql).doesNotContain("book_tags WHERE tagId IN")
        assertThat(sql).contains("INNER JOIN book_tags")
    }

    @Test
    fun `empty selection gives a facet over the whole library`() {
        val sql = FacetSql.formats(BookQuery()).sql

        assertThat(sql).doesNotContain("WHERE books.")
        assertThat(sql).contains("GROUP BY books.extension")
    }
}
