package io.github.bookshelf.core.readerlink.discovery

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Кого считать читалкой — по заявленным книжным форматам, а не по зашитому списку.
 *
 * ДЕФЕКТ (Александр, 01.10.2026): «не видит других читалок, как будто он зашит в коде».
 * Так и было: discovery отбрасывал всё, чего нет в списке `KNOWN_READER_PACKAGES`, и XReader
 * (`com.xrz.xreaderV3`, объявляет `application/epub+zip` и `application/fb2`) в списке не
 * появлялся вовсе — хотя система отдавала его в ответ на запрос по книжному типу.
 *
 * Теперь список известных читалок остаётся, но только как ПОДСКАЗКА для порядка: обычная
 * читалка не отбрасывается, а попадает в список по заявленным форматам.
 */
class ReaderCandidateRankTest {

    @Test
    fun `a known reader is ranked above everything else`() {
        assertThat(readerRankOf("com.fbreader", listOf("application/epub+zip")))
            .isEqualTo(ReaderRank.KNOWN)
    }

    @Test
    fun `an unknown reader that declares a book format is still a reader`() {
        // Так выглядит XReader с устройства: не в списке известных, но epub и fb2 объявлены.
        assertThat(readerRankOf("com.xrz.xreaderV3", listOf("application/epub+zip", "application/fb2")))
            .isEqualTo(ReaderRank.BOOK_FORMAT)
    }

    @Test
    fun `kindle and comic formats also make an app a reader`() {
        assertThat(readerRankOf("com.example.kindle", listOf("application/x-mobipocket-ebook")))
            .isEqualTo(ReaderRank.BOOK_FORMAT)
        assertThat(readerRankOf("com.example.comics", listOf("application/vnd.comicbook+zip")))
            .isEqualTo(ReaderRank.BOOK_FORMAT)
    }

    @Test
    fun `an app that only opens pdf or text is not a reader`() {
        // Такие приложения тоже отвечают на запрос по книжному типу, но читалками не являются:
        // они и создавали «длинный список с браузерами», из-за которого появился зашитый фильтр.
        assertThat(readerRankOf("com.example.viewer", listOf("application/pdf")))
            .isEqualTo(ReaderRank.OTHER)
        assertThat(readerRankOf("com.example.editor", listOf("text/plain")))
            .isEqualTo(ReaderRank.OTHER)
    }

    @Test
    fun `the fallback mime does not turn a file manager into a reader`() {
        assertThat(readerRankOf("com.example.files", listOf("application/octet-stream")))
            .isEqualTo(ReaderRank.OTHER)
    }
}
