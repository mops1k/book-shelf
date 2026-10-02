package io.github.bookshelf.core.readerlink.mime

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Категории форматов для экрана «Читалки»: книги, документы, манги, другое.
 *
 * Александр (01.10.2026): «сделаем не по форматам, а по категориям — книги, документы, манги,
 * другое, при выборе открывается экран с поддерживающими форматами, выбрал формат — далее экран
 * с выбором поддерживающих формат читалок». Поэтому нужен один честный ответ: к какой категории
 * относится расширение.
 */
class FormatCategoryTest {

    @Test
    fun `book formats are books`() {
        listOf("fb2", "fb2.zip", "epub", "mobi", "azw3", "prc").forEach { extension ->
            assertThat(formatCategoryOf(extension)).isEqualTo(ReaderFormatCategory.BOOKS)
        }
    }

    @Test
    fun `documents are documents`() {
        listOf("pdf", "txt", "docx", "odt", "rtf").forEach { extension ->
            assertThat(formatCategoryOf(extension)).isEqualTo(ReaderFormatCategory.DOCUMENTS)
        }
    }

    @Test
    fun `comic archives are manga`() {
        // Ради этого случая всё и затевалось: мангу хочется открывать в удобной для неё читалке.
        assertThat(formatCategoryOf("cbz")).isEqualTo(ReaderFormatCategory.MANGA)
        assertThat(formatCategoryOf("cbr")).isEqualTo(ReaderFormatCategory.MANGA)
    }

    @Test
    fun `scanned books and the unknown go to other`() {
        assertThat(formatCategoryOf("djvu")).isEqualTo(ReaderFormatCategory.OTHER)
        assertThat(formatCategoryOf("fb2z")).isEqualTo(ReaderFormatCategory.OTHER)
    }

    @Test
    fun `the case and the leading dot do not matter`() {
        assertThat(formatCategoryOf(".EPUB")).isEqualTo(ReaderFormatCategory.BOOKS)
        assertThat(formatCategoryOf(" CBZ ")).isEqualTo(ReaderFormatCategory.MANGA)
    }

    @Test
    fun `the order of categories is the order on screen`() {
        assertThat(ReaderFormatCategory.entries.map { it.extensionLabel })
            .containsExactly("Книги", "Документы", "Манги", "Другое")
    }
}
