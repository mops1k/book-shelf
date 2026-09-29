package io.github.bookshelf.core.data.sync

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Удаление книг, которых не было в проходе скана.
 *
 * Проверка стоит на страже данных человека: ошибка здесь уже стоила библиотеки — возобновлённый
 * после паузы прогон видел только хвост дерева и удалил все книги, которых «не встретил» (30.09.2026,
 * 79 книг исчезли из базы, файлы остались на месте).
 */
class ScanCleanupTest {

    @Test
    fun `a resumed run deletes nothing`() {
        // Именно этот случай стёр полку: из 79 книг в возобновлённом проходе «встретились» три.
        val gone = booksToDelete(
            allIds = listOf("a", "b", "c", "d"),
            seenIds = setOf("a"),
            filesSeen = 1,
            resumed = true,
        )

        assertThat(gone).isEmpty()
    }

    @Test
    fun `a full run deletes only what it did not see`() {
        val gone = booksToDelete(
            allIds = listOf("a", "b", "c", "d"),
            seenIds = setOf("a", "c"),
            filesSeen = 2,
            resumed = false,
        )

        assertThat(gone).containsExactly("b", "d")
    }

    @Test
    fun `a full run that saw nothing deletes nothing`() {
        // Пустая папка и сбой доступа выглядят одинаково; лишние строки дождутся следующего скана.
        val gone = booksToDelete(
            allIds = listOf("a", "b"),
            seenIds = emptySet(),
            filesSeen = 0,
            resumed = false,
        )

        assertThat(gone).isEmpty()
    }

    @Test
    fun `a full run keeps everything it saw`() {
        val gone = booksToDelete(
            allIds = listOf("a", "b"),
            seenIds = setOf("a", "b"),
            filesSeen = 2,
            resumed = false,
        )

        assertThat(gone).isEmpty()
    }

    @Test
    fun `files skipped by the format filter do not make books disappear`() {
        // Файлов пройдено больше, чем книг: часть файлов отсеял фильтр форматов. Книги, которые
        // фильтр не касается, при этом остаются на месте.
        val gone = booksToDelete(
            allIds = listOf("a"),
            seenIds = setOf("a"),
            filesSeen = 5,
            resumed = false,
        )

        assertThat(gone).isEmpty()
    }
}
