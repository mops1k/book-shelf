package io.github.bookshelf.core.readerlink.mime

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Тесты соответствия «расширение ↔ MIME».
 *
 * Логика чистая и используется при выборе читалки и построении `Intent`, поэтому покрывается
 * с первого дня; здесь же зафиксировано осознанное решение про [MimeTypes.FALLBACK] для FB2.
 */
class MimeTypesTest {

    @Test
    fun epub_maps_to_standard_mime() {
        assertThat(MimeTypes.mimeOf("epub")).isEqualTo("application/epub+zip")
    }

    @Test
    fun extension_is_normalized() {
        assertThat(MimeTypes.mimeOf(".EPUB")).isEqualTo("application/epub+zip")
        assertThat(MimeTypes.mimeOf("  Epub  ")).isEqualTo("application/epub+zip")
    }

    @Test
    fun fb2_uses_fallback_because_no_standard_mime_exists() {
        assertThat(MimeTypes.mimeOf("fb2")).isEqualTo(MimeTypes.FALLBACK)
        assertThat(MimeTypes.mimeOf("fb2.zip")).isEqualTo(MimeTypes.FALLBACK)
    }

    @Test
    fun unknown_extension_uses_fallback() {
        assertThat(MimeTypes.mimeOf("cb7")).isEqualTo(MimeTypes.FALLBACK)
        assertThat(MimeTypes.isKnown("cb7")).isFalse()
    }

    @Test
    fun mobi_family_shares_one_mime() {
        assertThat(MimeTypes.mimeOf("mobi")).isEqualTo("application/x-mobipocket-ebook")
        assertThat(MimeTypes.mimeOf("azw3")).isEqualTo("application/x-mobipocket-ebook")
        assertThat(MimeTypes.mimeOf("prc")).isEqualTo("application/x-mobipocket-ebook")
    }

    @Test
    fun comic_formats_keep_distinct_mime() {
        assertThat(MimeTypes.mimeOf("cbz")).isEqualTo("application/vnd.comicbook+zip")
        assertThat(MimeTypes.mimeOf("cbr")).isEqualTo("application/vnd.comicbook-rar")
    }

    @Test
    fun djvu_maps_to_image_mime() {
        assertThat(MimeTypes.mimeOf("djvu")).isEqualTo("image/vnd.djvu")
    }

    @Test
    fun extensions_for_mime_preserve_declaration_order() {
        assertThat(MimeTypes.extensionsFor("application/x-mobipocket-ebook"))
            .containsExactly("mobi", "azw3", "prc")
            .inOrder()
    }

    @Test
    fun fallback_mime_covers_fb2_extensions() {
        assertThat(MimeTypes.extensionsFor(MimeTypes.FALLBACK))
            .containsExactly("fb2", "fb2.zip")
            .inOrder()
    }

    @Test
    fun extensions_for_unknown_mime_is_empty() {
        assertThat(MimeTypes.extensionsFor("application/x-unknown")).isEmpty()
    }

    @Test
    fun known_extension_is_reported_as_known() {
        assertThat(MimeTypes.isKnown("epub")).isTrue()
        assertThat(MimeTypes.isKnown(".fb2")).isTrue()
    }
}
