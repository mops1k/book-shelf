package io.github.bookshelf.core.storage.scan

import android.net.Uri
import android.provider.DocumentsContract
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.common.error.errorOrNull
import io.github.bookshelf.core.common.error.getOrNull
import io.github.bookshelf.core.common.testing.TestAppDispatchers
import io.github.bookshelf.core.storage.saf.DirectPathTreeReader
import io.github.bookshelf.core.storage.saf.DispatchingTreeReader
import io.github.bookshelf.core.storage.saf.DocumentEntry
import io.github.bookshelf.core.storage.saf.SafTreeReader
import io.github.bookshelf.core.storage.saf.SourceKind
import io.github.bookshelf.core.storage.saf.SourceRef
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Поведение обхода проверяется на собственном фейке [SafTreeReader], а Android используется
 * только для корректного разбора корневого `treeUri` через [DocumentsContract].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class TreeWalkerTest {

    @get:Rule
    val cards = TemporaryFolder()

    /**
     * Обход источника, который читается по обычному пути файловой системы.
     *
     * Так работает съёмный носитель, который прошивка не опубликовала в `/storage/<uuid>`
     * (Bigme B6 Color, 02.10.2026): адрес источника — путь, и обходчик обязан дойти до книги.
     */
    @Test
    fun walk_reads_a_filesystem_source_by_its_path() = runTest {
        val card = cards.newFolder("card")
        cards.newFolder("card", "Лем")
        cards.newFile("card/Солярис.fb2")

        val reader = DispatchingTreeReader(saf = FakeSafTreeReader(emptyMap()), direct = DirectPathTreeReader())
        val found = walkToEnd(
            reader = reader,
            options = ScanOptions.DEFAULT,
            ref = source(uri = card.absolutePath, kind = SourceKind.DIRECT_PATH),
        )

        assertThat(found.map { entry -> entry.displayName }).contains("Солярис.fb2")
        assertThat(found.map { entry -> entry.documentId }).contains("Солярис.fb2")
    }

    @Test
    fun walk_finds_books_in_nested_tree_across_resume() = runTest {
        val reader = FakeSafTreeReader(
            mapOf(
                ROOT_ID to listOf(directory("folder-id", "Книги")),
                "folder-id" to (1..101).map { index -> book("book-$index", "Книга-$index.fb2") },
            ),
        )
        val walker = walker(reader)
        val batches = mutableListOf<List<DocumentEntry>>()
        val resumePoints = mutableListOf<ScanCursor>()

        var cursor: ScanCursor? = null
        do {
            val result = walker.walk(source(), cursor, ScanOptions.DEFAULT) { batch, resumeFrom ->
                batches += batch
                resumePoints += resumeFrom
            }
            assertThat(result.errorOrNull()).isNull()
            cursor = result.getOrNull()
        } while (cursor != null)

        // Явный тип: для nullable ScanCursor вызов assertThat без него даёт неоднозначность
        // перегрузок Truth, а isNull() на выведенном типе не разрешается.
        assertThat<ScanCursor?>(cursor).isNull()
        // В курсоре нет индекса внутри Cursor: возобновление обязано переоткрыть текущую папку
        // и пропустить префикс до documentId, поэтому folder-id читается ровно дважды.
        assertThat(reader.visited).containsExactly(ROOT_ID, "folder-id", "folder-id").inOrder()
        assertThat(resumePoints).hasSize(2)
        assertThat(resumePoints.first().documentId).isEqualTo("book-100")
        assertThat(resumePoints.last().queue).isEmpty()
        assertThat(batches).hasSize(2)
        assertThat(batches.first()).hasSize(100)
        assertThat(batches.last().map(DocumentEntry::documentId)).containsExactly("book-101")
        assertThat(batches.flatten().map(DocumentEntry::documentId)).containsNoDuplicates()
    }

    @Test
    fun walk_filters_extensions_hidden_files_and_source_globs() = runTest {
        val reader = FakeSafTreeReader(
            mapOf(
                ROOT_ID to listOf(
                    book("kept-id", "Книга.pdf"),
                    book("other-format-id", "Книга.epub"),
                    book("draft-id", "Черновик.pdf"),
                    book("archived-id", "Архив.pdf"),
                    book("hidden-id", ".Скрытая.pdf"),
                    book("image-id", "Обложка.jpg"),
                ),
            ),
        )
        val options = ScanOptions(
            excludeHidden = true,
            excludeGlobs = listOf("Черновик.*"),
            extensions = setOf("pdf"),
        )

        // Глоб источника и глоб опции складываются, поэтому источник нужно передать явно:
        // иначе до обхода дойдут только options.excludeGlobs и «Архив.pdf» не отсечётся.
        val collected = walkToEnd(reader, options, source(excludeGlobs = listOf("Архив.*")))

        assertThat(collected.map(DocumentEntry::documentId)).containsExactly("kept-id")
    }

    @Test
    fun walk_stops_descending_at_max_depth() = runTest {
        val reader = FakeSafTreeReader(
            mapOf(
                ROOT_ID to listOf(directory("depth-1", "Первый уровень")),
                "depth-1" to listOf(
                    book("book-id", "Книга.fb2"),
                    directory("depth-2", "Второй уровень"),
                ),
                "depth-2" to listOf(book("too-deep-id", "Слишком.глубоко.epub")),
            ),
        )
        val collected = walkToEnd(reader, ScanOptions(maxDepth = 1))

        // С maxDepth = 1 каталоги второго уровня не открываются.
        assertThat(reader.visited).containsExactly(ROOT_ID, "depth-1").inOrder()
        assertThat(collected.map(DocumentEntry::documentId)).containsExactly("book-id")
    }

    @Test
    fun walk_does_not_descend_when_source_is_not_recursive() = runTest {
        val reader = FakeSafTreeReader(
            mapOf(
                ROOT_ID to listOf(directory("nested-id", "Вложенная")),
                "nested-id" to listOf(book("nested-book-id", "Вложенная.epub")),
            ),
        )

        val collected = walkToEnd(reader, ScanOptions.DEFAULT, source(recursive = false))

        assertThat(reader.visited).containsExactly(ROOT_ID)
        assertThat(collected).isEmpty()
    }

    @Test
    fun walk_continues_from_queue_without_visiting_processed_parents() = runTest {
        val reader = FakeSafTreeReader(
            mapOf("resume-id" to listOf(book("resume-book-id", "Продолжение.fb2"))),
        )
        val cursor = ScanCursor(
            documentId = "processed-root-id",
            queue = listOf(ScanCursor.Item("resume-id", depth = 1)),
        )

        val result = walker(reader).walk(source(), cursor, ScanOptions.DEFAULT) { _, _ -> }

        assertThat(result.getOrNull()).isNull()
        assertThat(reader.visited).containsExactly("resume-id")
    }

    @Test
    fun walk_emits_batches_of_one_hundred_entries() = runTest {
        val files = (1..205).map { index -> book("book-$index", "Книга-$index.fb2") }
        val reader = FakeSafTreeReader(mapOf(ROOT_ID to files))
        val batches = mutableListOf<List<DocumentEntry>>()

        var cursor: ScanCursor? = null
        do {
            val result = walker(reader).walk(source(), cursor, ScanOptions.DEFAULT) { batch, _ ->
                batches += batch
            }
            assertThat(result.errorOrNull()).isNull()
            cursor = result.getOrNull()
        } while (cursor != null)

        assertThat(batches.map(List<DocumentEntry>::size)).containsExactly(100, 100, 5).inOrder()
        assertThat(batches.flatten().map(DocumentEntry::documentId))
            .containsExactlyElementsIn(files.map(DocumentEntry::documentId))
    }

    private suspend fun walkToEnd(
        reader: SafTreeReader,
        options: ScanOptions,
        ref: SourceRef = source(),
    ): List<DocumentEntry> {
        val batches = mutableListOf<List<DocumentEntry>>()
        var cursor: ScanCursor? = null
        do {
            val result = walker(reader).walk(ref, cursor, options) { batch, _ -> batches += batch }
            assertThat(result.errorOrNull()).isNull()
            cursor = result.getOrNull()
        } while (cursor != null)
        return batches.flatten()
    }

    private fun walker(reader: SafTreeReader): DefaultTreeWalker =
        DefaultTreeWalker(reader, TestAppDispatchers())

    private fun source(
        recursive: Boolean = true,
        excludeGlobs: List<String> = emptyList(),
        uri: String = TREE_URI,
        kind: SourceKind = SourceKind.SAF_TREE,
    ): SourceRef = SourceRef(
        id = "source-id",
        treeUri = uri,
        displayName = "Моя полка",
        kind = kind,
        recursive = recursive,
        excludeGlobs = excludeGlobs,
    )

    private fun directory(id: String, name: String): DocumentEntry = DocumentEntry(
        documentId = id,
        displayName = name,
        isDirectory = true,
        sizeBytes = null,
        lastModified = null,
        mimeType = DocumentsContract.Document.MIME_TYPE_DIR,
    )

    private fun book(id: String, name: String): DocumentEntry = DocumentEntry(
        documentId = id,
        displayName = name,
        isDirectory = false,
        sizeBytes = 1L,
        lastModified = 1L,
        mimeType = null,
    )

    private class FakeSafTreeReader(
        private val entries: Map<String, List<DocumentEntry>>,
    ) : SafTreeReader {

        override fun rootDocumentId(treeUri: String): String =
            DocumentsContract.getTreeDocumentId(Uri.parse(treeUri))


        val visited = mutableListOf<String>()

        override suspend fun children(
            treeUri: String,
            parentDocumentId: String,
        ): AppResult<List<DocumentEntry>> {
            visited += parentDocumentId
            return AppResult.success(entries[parentDocumentId].orEmpty())
        }

        override suspend fun takePersistablePermission(
            treeUri: String,
            modeFlags: Int,
        ): AppResult<Unit> = AppResult.success(Unit)

        override fun hasPersistedPermission(treeUri: String): Boolean = true

        /** В тесте обхода дерева запись не проверяется: право считается выданным. */
        override fun hasWritePermission(treeUri: String): Boolean = true

        override fun documentUri(treeUri: String, documentId: String): String =
            Uri.parse("$treeUri/document/$documentId").toString()

        /**
         * В этом тесте проверяется обход дерева, а не путь: идентификаторы документов здесь
         * условные, поэтому возвращается последний сегмент. Сборка пути проверяется
         * в `SafRelativePathTest`.
         */
        override fun relativePath(treeUri: String, documentId: String): String =
            documentId.substringAfterLast('/')
    }

    private companion object {

        const val ROOT_ID = "root-id"
        val TREE_URI = DocumentsContract.buildTreeDocumentUri("com.example.documents", ROOT_ID).toString()
    }
}
