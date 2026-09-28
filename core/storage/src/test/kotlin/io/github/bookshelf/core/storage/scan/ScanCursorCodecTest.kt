package io.github.bookshelf.core.storage.scan

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Формат cursorDocumentId должен переживать JSON-цикл, а повреждённые данные не ронять скан. */
class ScanCursorCodecTest {

    @Test
    fun encode_decode_preserves_last_document_and_folder_depths() {
        val cursor = ScanCursor(
            documentId = "file-42",
            queue = listOf(
                ScanCursor.Item("folder-a", 1),
                ScanCursor.Item("folder-b", 2),
            ),
        )

        val restored = ScanCursorCodec.decode(ScanCursorCodec.encode(cursor))

        assertThat(restored).isEqualTo(cursor)
    }

    @Test
    fun decode_returns_null_for_broken_json() {
        assertThat(ScanCursorCodec.decode("{not-json")).isNull()
    }
}
