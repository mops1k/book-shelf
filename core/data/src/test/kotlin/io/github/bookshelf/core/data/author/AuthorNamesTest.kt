package io.github.bookshelf.core.data.author

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Разбор строки авторов из метаданных.
 *
 * Авторы хранятся строкой («Александр Тарарев, Юрий Тарарев»), и от разбора зависит, кого человек
 * видит в обзоре: раньше ключом была вся строка, поэтому те же соавторы в другом порядке
 * превращались в других авторов (дефект Александра, 29.09.2026).
 */
class AuthorNamesTest {

    @Test
    fun `single author stays one author`() {
        assertThat(parseAuthors("Лев Толстой")).containsExactly("Лев Толстой")
    }

    @Test
    fun `comma separated co-authors are split and order is kept`() {
        assertThat(parseAuthors("Александр Тарарев, Юрий Тарарев"))
            .containsExactly("Александр Тарарев", "Юрий Тарарев")
    }

    @Test
    fun `semicolon is a separator too`() {
        assertThat(parseAuthors("Александр Тарарев; Юрий Тарарев"))
            .containsExactly("Александр Тарарев", "Юрий Тарарев")
    }

    @Test
    fun `extra spaces and empty pieces are dropped`() {
        assertThat(parseAuthors("  Лев Толстой ,, ;  "))
            .containsExactly("Лев Толстой")
    }

    @Test
    fun `blank text means no authors`() {
        assertThat(parseAuthors(null)).isEmpty()
        assertThat(parseAuthors("   ")).isEmpty()
    }

    @Test
    fun `the same author written twice counts once`() {
        assertThat(parseAuthors("Лев Толстой, Лев Толстой")).containsExactly("Лев Толстой")
    }

    @Test
    fun `author set key ignores the order of co-authors`() {
        val first = parseAuthors("Александр Тарарев, Юрий Тарарев")
        val second = parseAuthors("Юрий Тарарев, Александр Тарарев")

        // Именно это ломало обзор: «A, B» и «B, A» — одна и та же пара соавторов.
        assertThat(authorSetKey(first)).isEqualTo(authorSetKey(second))
    }

    @Test
    fun `author set label lists names alphabetically`() {
        val label = authorSetLabel(parseAuthors("Юрий Тарарев, Александр Тарарев"))

        // Подпись группы всегда одна и та же, как бы ни были написаны авторы в файле.
        assertThat(label).isEqualTo("Александр Тарарев, Юрий Тарарев")
    }

    @Test
    fun `different author sets have different keys`() {
        assertThat(authorSetKey(parseAuthors("A, B")))
            .isNotEqualTo(authorSetKey(parseAuthors("A, B, C")))
        assertThat(authorSetKey(parseAuthors("A"))).isNotEqualTo(authorSetKey(parseAuthors("A, B")))
    }
}
