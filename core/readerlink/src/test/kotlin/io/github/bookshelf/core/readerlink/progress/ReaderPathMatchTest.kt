package io.github.bookshelf.core.readerlink.progress

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Сверка пути читалки с нашей книгой (`readerPathMatches`).
 *
 * Читалки CoolReader, PocketBook и Librera называют книгу **реальным путём** (`/sdcard/Books/…`), а у
 * нас адрес SAF, поэтому сравнивается хвост пути. Для книги в подпапке этого достаточно, а для книги
 * в корне библиотеки — нет: `/sdcard/Other/Книга.epub` тоже кончается на `/Книга.epub`, и без
 * дополнительной проверки мы записали бы её процент нашей книге.
 *
 * Поэтому, если относительный путь книги — одно имя файла, требуется ещё и папка библиотеки из адреса
 * документа (`content://…/tree/primary%3ABooks/…` → `Books`).
 */
class ReaderPathMatchTest {

    @Test
    fun `a book inside a folder matches by its path tail and the library folder`() {
        val book = book(relativePath = "Лем/Книга.fb2.epub")

        assertThat(readerPathMatches("/sdcard/Books/Лем/Книга.fb2.epub", book)).isTrue()
        assertThat(readerPathMatches("/storage/emulated/0/Books/Лем/Книга.fb2.epub", book)).isTrue()
        // Хвост совпал, но папка библиотеки другая: это не наша книга.
        assertThat(readerPathMatches("/sdcard/Other/Лем/Книга.fb2.epub", book)).isFalse()
    }

    @Test
    fun `another name does not match`() {
        val book = book(relativePath = "Лем/Книга.fb2.epub")

        assertThat(readerPathMatches("/sdcard/Books/Лем/Другая.fb2.epub", book)).isFalse()
    }

    @Test
    fun `a book at the root of the library must sit in the library folder`() {
        // Та же беда, из-за которой появилась проверка: имя совпало, папка — чужая.
        val book = book(relativePath = "Книга.fb2.epub")

        assertThat(readerPathMatches("/sdcard/Books/Книга.fb2.epub", book)).isTrue()
        assertThat(readerPathMatches("internal-storage:/Books/Книга.fb2.epub", book)).isTrue()
        assertThat(readerPathMatches("/sdcard/Other/Книга.fb2.epub", book)).isFalse()
        assertThat(readerPathMatches("/mnt/media_rw/1/Книга.fb2.epub", book)).isFalse()
    }

    @Test
    fun `without a SAF tree the tail is all we have`() {
        // Адрес не SAF-дерево (например, прямой путь или чужой провайдер): требуем только хвост,
        // иначе книга в корне библиотеки вообще не нашлась бы.
        val book = book(relativePath = "Книга.fb2.epub", documentUri = "raw:/storage/1/Книга.fb2.epub")

        assertThat(readerPathMatches("/sdcard/Other/Книга.fb2.epub", book)).isTrue()
    }

    @Test
    fun `the library folder is read from the document address`() {
        assertThat(
            libraryRootFolder(
                "content://com.android.externalstorage.documents/tree/primary%3ABooks/document/" +
                    "primary%3ABooks%2F%D0%9B%D0%B5%D0%BC%2F%D0%9A%D0%BD%D0%B8%D0%B3%D0%B0.fb2.epub",
            ),
        ).isEqualTo("Books")
        assertThat(
            libraryRootFolder("content://com.android.externalstorage.documents/tree/" +
                "primary%3ABooks%2F%D0%9B%D0%B5%D0%BC"),
        ).isEqualTo("Лем")
        assertThat(libraryRootFolder("content://com.android.externalstorage.documents/tree/primary%3A")).isNull()
        assertThat(libraryRootFolder("raw:/storage/emulated/0/Books/Книга.epub")).isNull()
    }

    private fun book(
        relativePath: String,
        documentUri: String =
            "content://com.android.externalstorage.documents/tree/primary%3ABooks/document/" +
                "primary%3ABooks%2F%D0%BA%D0%BD%D0%B8%D0%B3%D0%B0",
    ) = ProgressBookRef(
        bookId = "book-1",
        documentUri = documentUri,
        readerAddress = documentUri,
        fileName = relativePath.substringAfterLast('/'),
        relativePath = relativePath,
        sizeBytes = 1_000L,
        librarySourceId = "source-1",
    )
}
