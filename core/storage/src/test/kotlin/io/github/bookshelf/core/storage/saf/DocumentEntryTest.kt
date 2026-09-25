package io.github.bookshelf.core.storage.saf

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * [DocumentEntry.extension] и [DocumentEntry.isHidden] — чистая логика, от которой зависит
 * фильтр скана, поэтому проверяется обычным JUnit без Android.
 */
class DocumentEntryTest {

    @Test
    fun extension_is_lower_case_without_dot() {
        assertThat(entry("Book.FB2").extension).isEqualTo("fb2")
        assertThat(entry("novel.ePub").extension).isEqualTo("epub")
    }

    @Test
    fun compound_names_return_last_segment_only() {
        assertThat(entry("book.fb2.zip").extension).isEqualTo("zip")
        assertThat(entry("archive.tar.gz").extension).isEqualTo("gz")
    }

    @Test
    fun no_extension_for_plain_names_and_dot_files() {
        assertThat(entry("README").extension).isEmpty()
        assertThat(entry(".nomedia").extension).isEmpty()
        assertThat(entry("trailing.").extension).isEmpty()
    }

    @Test
    fun dot_files_are_hidden() {
        assertThat(entry(".nomedia").isHidden).isTrue()
        assertThat(entry(".hidden.fb2").isHidden).isTrue()
        assertThat(entry("book.fb2").isHidden).isFalse()
    }

    private fun entry(name: String): DocumentEntry = DocumentEntry(
        documentId = "id:$name",
        displayName = name,
        isDirectory = false,
        sizeBytes = null,
        lastModified = null,
        mimeType = null,
    )
}
