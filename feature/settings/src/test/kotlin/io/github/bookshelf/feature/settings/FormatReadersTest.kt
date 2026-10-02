package io.github.bookshelf.feature.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Строки «формат → читалка» для экрана «Читалки».
 *
 * Требование Александра (01.10.2026): «надо сделать, чтобы пользователь мог выбрать, какому
 * формату какую читалку привязать, если читалка поддерживает конечно этот формат». Значит в списке
 * не должно быть ни читалок, которые формат не умеют, ни форматов, которые никто не умеет.
 */
class FormatReadersTest {

    @Test
    fun `a format is bound to the reader chosen for it`() {
        val rows = formatReadersOf(
            readers = listOf(reader("fb", "FBReader", "fb2", "epub"), reader("xr", "XReader", "epub")),
            defaults = mapOf("epub" to "xr"),
        )

        assertThat(rows.map { it.extension }).containsExactly("epub", "fb2").inOrder()
        assertThat(rows.first { it.extension == "epub" }.readerId).isEqualTo("xr")
        assertThat(rows.first { it.extension == "epub" }.readerName).isEqualTo("XReader")
        assertThat(rows.first { it.extension == "fb2" }.readerName).isNull()
    }

    @Test
    fun `only readers that declared the format are counted as candidates`() {
        val rows = formatReadersOf(
            readers = listOf(reader("fb", "FBReader", "fb2"), reader("xr", "XReader", "epub")),
            defaults = emptyMap(),
        )

        assertThat(rows).hasSize(2)
        assertThat(rows.first { it.extension == "fb2" }.candidates).isEqualTo(1)
        assertThat(rows.first { it.extension == "epub" }.candidates).isEqualTo(1)
    }

    @Test
    fun `a binding to a reader that is gone is shown as not chosen`() {
        // Читалку удалили: привязка осталась строкой в базе, но показывать её нечем.
        val rows = formatReadersOf(
            readers = listOf(reader("fb", "FBReader", "fb2")),
            defaults = mapOf("fb2" to "xr"),
        )

        assertThat(rows.single().readerName).isNull()
        assertThat(rows.single().readerId).isNull()
    }

    @Test
    fun `without readers there are no format rows`() {
        assertThat(formatReadersOf(readers = emptyList(), defaults = emptyMap())).isEmpty()
    }

    private fun reader(id: String, name: String, vararg extensions: String) = ReaderItemUi(
        id = id,
        name = name,
        packageName = "com.example.$id",
        supportedExtensions = extensions.toSet(),
    )
}
