package io.github.bookshelf.core.storage.scan

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Расширение и признак скрытости — чистая логика фильтра скана, поэтому проверяется
 * без Robolectric («TDD» §7: тесты только на реально реализованное поведение).
 */
class BookFileFilterTest {

    @Test
    fun supported_extensions_are_recognized_in_any_case() {
        assertThat(BookFileFilter.isSupported("fb2")).isTrue()
        assertThat(BookFileFilter.isSupported("FB2")).isTrue()
        assertThat(BookFileFilter.isSupported(".EPUB")).isTrue()
        assertThat(BookFileFilter.isSupported("fb2.zip")).isTrue()
        assertThat(BookFileFilter.isSupported("  Mobi  ")).isTrue()
    }

    @Test
    fun unknown_extensions_are_rejected() {
        assertThat(BookFileFilter.isSupported("zip")).isFalse()
        assertThat(BookFileFilter.isSupported("jpg")).isFalse()
        assertThat(BookFileFilter.isSupported("")).isFalse()
    }

    @Test
    fun cbr_is_full_flavor_only() {
        assertThat(BookFileFilter.isSupportedOnlyInFullFlavor("cbr")).isTrue()
        assertThat(BookFileFilter.isSupportedOnlyInFullFlavor("CBR")).isTrue()
        assertThat(BookFileFilter.isSupportedOnlyInFullFlavor("cbz")).isFalse()
    }

    @Test
    fun djvu_is_listed_but_has_no_parser() {
        assertThat(BookFileFilter.isSupported("djvu")).isTrue()
        assertThat(BookFileFilter.isSupportedWithoutParser("djvu")).isTrue()
        assertThat(BookFileFilter.isSupportedWithoutParser("fb2")).isFalse()
    }

    @Test
    fun compound_fb2_zip_extension_is_detected() {
        assertThat(BookFileFilter.detectExtension("Book.FB2.ZIP")).isEqualTo("fb2.zip")
        assertThat(BookFileFilter.detectExtension("novel.fb2")).isEqualTo("fb2")
        assertThat(BookFileFilter.detectExtension("no-extension")).isEmpty()
        assertThat(BookFileFilter.detectExtension(".hidden")).isEmpty()
    }

    @Test
    fun file_name_filter_uses_detected_extension() {
        assertThat(BookFileFilter.isBookFileName("Автор - Книга.fb2")).isTrue()
        assertThat(BookFileFilter.isBookFileName("scan.tmp")).isFalse()
    }

    @Test
    fun default_globs_exclude_temp_and_hidden_files() {
        assertThat(BookFileFilter.isExcluded("cover.tmp")).isTrue()
        assertThat(BookFileFilter.isExcluded("COVER.TMP")).isTrue()
        assertThat(BookFileFilter.isExcluded(".nomedia")).isTrue()
        assertThat(BookFileFilter.isExcluded("book.fb2")).isFalse()
    }

    @Test
    fun custom_glob_with_question_mark_matches_one_symbol() {
        assertThat(BookFileFilter.isExcluded("book1.fb2", listOf("book?.fb2"))).isTrue()
        assertThat(BookFileFilter.isExcluded("book12.fb2", listOf("book?.fb2"))).isFalse()
    }
}
