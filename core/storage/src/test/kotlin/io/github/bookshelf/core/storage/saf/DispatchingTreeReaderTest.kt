package io.github.bookshelf.core.storage.saf

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.common.error.getOrNull
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Выбор способа доступа по адресу источника.
 *
 * От этого зависит, читается ли съёмный носитель вообще: SAF-адрес должен уходить провайдеру, а
 * путь файловой системы — прямому читателю (прошивки без публичной точки `/storage/<uuid>`,
 * проверено на Bigme B6 Color 02.10.2026).
 */
class DispatchingTreeReaderTest {

    private val saf = RecordingReader(tag = "saf")
    private val direct = RecordingReader(tag = "direct")
    private val reader = DispatchingTreeReader(saf = saf, direct = direct)

    @Test
    fun `a content address is served by the provider`() = runBlocking<Unit> {
        val entries = reader.children(CONTENT_TREE, "").getOrNull()!!

        assertThat(entries.single().documentId).isEqualTo("saf")
        assertThat(direct.calls).isEmpty()
    }

    @Test
    fun `a file path is served by the direct reader`() = runBlocking<Unit> {
        val entries = reader.children("/mnt/media_rw/0436-C297", "").getOrNull()!!

        assertThat(entries.single().documentId).isEqualTo("direct")
        assertThat(saf.calls).isEmpty()
    }

    @Test
    fun `the scheme is matched regardless of case`() = runBlocking<Unit> {
        reader.children("CONTENT://provider/tree/1A2B-3C4D%3A", "").getOrNull()

        assertThat(saf.calls).hasSize(1)
        assertThat(direct.calls).isEmpty()
    }

    @Test
    fun `the root id is asked from the same reader`() {
        assertThat(reader.rootDocumentId(CONTENT_TREE)).isEqualTo("saf-root")
        assertThat(reader.rootDocumentId("/mnt/media_rw/0436-C297")).isEqualTo("direct-root")
    }

    @Test
    fun `address and relative path follow the same choice`() {
        assertThat(reader.documentUri(CONTENT_TREE, "book.fb2")).isEqualTo("saf:book.fb2")
        assertThat(reader.documentUri("/mnt/media_rw/0436-C297", "book.fb2"))
            .isEqualTo("direct:book.fb2")
        assertThat(reader.relativePath("/mnt/media_rw/0436-C297", "book.fb2")).isEqualTo("direct/book.fb2")
    }

    @Test
    fun `permissions are asked from the same reader that serves the address`() = runBlocking<Unit> {
        reader.takePersistablePermission("/mnt/media_rw/0436-C297").getOrNull()
        reader.hasPersistedPermission("/mnt/media_rw/0436-C297")
        reader.hasWritePermission("/mnt/media_rw/0436-C297")
        reader.hasPersistedPermission(CONTENT_TREE)

        assertThat(direct.calls).containsExactly(
            "permission:/mnt/media_rw/0436-C297",
            "has:/mnt/media_rw/0436-C297",
            "write:/mnt/media_rw/0436-C297",
        )
        assertThat(saf.calls).containsExactly("has:$CONTENT_TREE")
    }

    private companion object {
        const val CONTENT_TREE = "content://com.android.externalstorage.documents/tree/1A2B-3C4D%3A"
    }

    /** Свой читатель-фейк: по правилу ADR-012 собственные типы не мокаются. */
    private class RecordingReader(private val tag: String) : SafTreeReader {

        val calls: MutableList<String> = mutableListOf()

        override suspend fun children(treeUri: String, parentDocumentId: String): AppResult<List<DocumentEntry>> {
            calls += "children:$treeUri"
            return AppResult.success(
                listOf(
                    DocumentEntry(
                        documentId = tag,
                        displayName = tag,
                        isDirectory = false,
                        sizeBytes = 1L,
                        lastModified = 2L,
                        mimeType = null,
                    ),
                ),
            )
        }

        override suspend fun takePersistablePermission(treeUri: String, modeFlags: Int): AppResult<Unit> {
            calls += "permission:$treeUri"
            return AppResult.success(Unit)
        }

        override fun hasPersistedPermission(treeUri: String): Boolean {
            calls += "has:$treeUri"
            return true
        }

        override fun hasWritePermission(treeUri: String): Boolean {
            calls += "write:$treeUri"
            return true
        }

        override fun rootDocumentId(treeUri: String): String = "$tag-root"

        override fun documentUri(treeUri: String, documentId: String): String = "$tag:$documentId"

        override fun relativePath(treeUri: String, documentId: String): String = "$tag/$documentId"
    }
}
