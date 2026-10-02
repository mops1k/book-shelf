package io.github.bookshelf.core.data.removable

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.data.model.Source
import io.github.bookshelf.core.data.model.SourceKind
import org.junit.Test

/**
 * Правила переноса книги с носителя в библиотеку (решение Александра 02.10.2026).
 *
 * Проверяется то, из-за чего человек может потерять данные: куда книга уезжает, что делать с уже
 * имеющимся дублем (сравнение по содержимому, а не по имени) и как освободить занятое имя, не
 * перезаписав чужой файл.
 */
class TransferToLibraryRulesTest {

    @Test
    fun `the target is the first plain folder of the library`() {
        val catalogs = source(id = "opds", uri = "content://opds/catalog", kind = SourceKind.OPDS)
        val card = source(id = "card", uri = "/mnt/media_rw/0436-C297", kind = SourceKind.DIRECT_PATH)
        val books = source(id = "books", uri = "content://com.android.externalstorage.documents/tree/primary%3ABooks")

        assertThat(libraryTargetOf(listOf(catalogs, card, books))?.id).isEqualTo("books")
    }

    @Test
    fun `without a plain folder there is nowhere to transfer`() {
        val catalogs = source(id = "opds", uri = "content://opds/catalog", kind = SourceKind.OPDS)
        val card = source(id = "card", uri = "/mnt/media_rw/0436-C297", kind = SourceKind.DIRECT_PATH)

        assertThat(libraryTargetOf(listOf(catalogs, card))).isNull()
    }

    @Test
    fun `a duplicate is skipped even when the name is free`() {
        val decision = transferDecision(duplicateFound = true, nameTaken = false, base = "Солярис.fb2")

        assertThat(decision).isEqualTo(TransferDecision.SkipDuplicate)
    }

    @Test
    fun `a taken name with other content is renamed, not overwritten`() {
        val decision = transferDecision(duplicateFound = false, nameTaken = true, base = "Солярис.fb2")

        assertThat(decision).isEqualTo(TransferDecision.Rename("Солярис.fb2"))
    }

    @Test
    fun `a free name is moved as is`() {
        val decision = transferDecision(duplicateFound = false, nameTaken = false, base = "Солярис.fb2")

        assertThat(decision).isEqualTo(TransferDecision.Move)
    }

    @Test
    fun `a free name is returned unchanged`() {
        assertThat(uniqueFileName("Солярис.fb2") { false }).isEqualTo("Солярис.fb2")
    }

    @Test
    fun `a taken name gets a suffix before the extension`() {
        val taken = setOf("Солярис.fb2", "Солярис (1).fb2")

        assertThat(uniqueFileName("Солярис.fb2") { name -> name in taken })
            .isEqualTo("Солярис (2).fb2")
    }

    @Test
    fun `a name without extension keeps the suffix at the end`() {
        assertThat(uniqueFileName("book") { name -> name == "book" }).isEqualTo("book (1)")
    }

    @Test
    fun `a hidden file keeps its dot at the start`() {
        // Точка в начале — не расширение: имя `.nomedia` не должно превратиться в `. (1)nomedia`.
        assertThat(uniqueFileName(".nomedia") { name -> name == ".nomedia" }).isEqualTo(".nomedia (1)")
    }

    @Test
    fun `the search stops instead of looping forever`() {
        assertThat(uniqueFileName("book.fb2", limit = 3) { true }).isEqualTo("book (3).fb2")
    }

    private fun source(
        id: String,
        uri: String,
        kind: SourceKind = SourceKind.SAF_TREE,
    ) = Source(id = id, uri = uri, displayName = id, kind = kind)
}
