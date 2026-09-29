package io.github.bookshelf.core.storage.saf

import android.content.ContentResolver
import android.content.Intent
import android.content.UriPermission
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.DocumentsContract
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.errorOrNull
import io.github.bookshelf.core.common.error.getOrNull
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException

/**
 * Проверка чтения SAF через настоящий `ContentResolver` и подменённый источник курсоров.
 *
 * Провайдер намеренно остаётся фейком: проверяется перевод Android-ошибок и корректное чтение
 * неполных данных, которые разрешено не отдавать SAF-провайдером.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class SafTreeReaderTest {

    @Test
    fun children_maps_files_and_directories() = runTest {
        val cursor = MatrixCursor(COLUMNS).apply {
            addRow(
                arrayOf<Any?>(
                    "folder-id",
                    "Книги",
                    DocumentsContract.Document.MIME_TYPE_DIR,
                    null,
                    null,
                ),
            )
            addRow(arrayOf<Any?>("file-id", "Роман.fb2", "application/epub+zip", 42L, 1_700_000L))
        }
        val resolver = mockk<ContentResolver>(relaxed = true)
        every { resolver.query(any(), any(), any(), any(), any()) } returns cursor
        val reader = AndroidSafTreeReader(resolver)

        val result = reader.children(TREE_URI, ROOT_DOCUMENT_ID)

        assertThat(result.getOrNull()).containsExactly(
            DocumentEntry("folder-id", "Книги", true, null, null, DocumentsContract.Document.MIME_TYPE_DIR),
            DocumentEntry("file-id", "Роман.fb2", false, 42L, 1_700_000L, "application/epub+zip"),
        ).inOrder()
        verify(exactly = 1) {
            resolver.query(childrenUri(), any(), null, null, null)
        }
        assertThat(cursor.isClosed).isTrue()
    }

    @Test
    fun children_returns_empty_list_when_cursor_has_no_rows() = runTest {
        val resolver = mockk<ContentResolver>(relaxed = true)
        every { resolver.query(any(), any(), any(), any(), any()) } returns MatrixCursor(COLUMNS)
        val reader = AndroidSafTreeReader(resolver)

        val result = reader.children(TREE_URI, ROOT_DOCUMENT_ID)

        assertThat(result.getOrNull()).isEmpty()
    }

    @Test
    fun children_keeps_optional_size_and_modified_null_when_columns_are_missing() = runTest {
        val partialColumns = COLUMNS.take(3).toTypedArray()
        val cursor = MatrixCursor(partialColumns).apply {
            addRow(arrayOf<Any?>("file-id", "Роман.epub", "application/epub+zip"))
        }
        val resolver = mockk<ContentResolver>(relaxed = true)
        every { resolver.query(any(), any(), any(), any(), any()) } returns cursor
        val reader = AndroidSafTreeReader(resolver)

        val entry = reader.children(TREE_URI, ROOT_DOCUMENT_ID).getOrNull()?.single()

        assertThat(entry).isEqualTo(DocumentEntry("file-id", "Роман.epub", false, null, null, "application/epub+zip"))
    }

    @Test
    fun children_maps_security_exception_to_permission_denied() = runTest {
        val resolver = mockk<ContentResolver>(relaxed = true)
        every { resolver.query(any(), any(), any(), any(), any()) } throws
            SecurityException("нет доступа к дереву")
        val reader = AndroidSafTreeReader(resolver)

        val result = reader.children(TREE_URI, ROOT_DOCUMENT_ID)

        val error = result.errorOrNull()
        assertThat(error).isInstanceOf(AppError.PermissionDenied::class.java)
        assertThat((error as AppError.PermissionDenied).uri).isEqualTo(TREE_URI)
    }

    @Test
    fun children_maps_missing_document_to_not_found() = runTest {
        val resolver = mockk<ContentResolver>(relaxed = true)
        every { resolver.query(any(), any(), any(), any(), any()) } throws
            FileNotFoundException("папка удалена")
        val reader = AndroidSafTreeReader(resolver)

        val result = reader.children(TREE_URI, "missing-id")

        assertThat(result.errorOrNull()).isInstanceOf(AppError.NotFound::class.java)
    }

    @Test
    fun children_propagates_cancellation_from_cursor_loop() = runTest {
        val parentJob = Job()
        val cursor = mockk<Cursor>(relaxed = true)
        every { cursor.moveToFirst() } answers {
            parentJob.cancel(CancellationException("отмена теста"))
            true
        }
        every { cursor.getColumnIndexOrThrow(any()) } returns 0
        every { cursor.getColumnIndex(any()) } returns -1
        val resolver = mockk<ContentResolver>(relaxed = true)
        every { resolver.query(any(), any(), any(), any(), any()) } returns cursor
        val reader = AndroidSafTreeReader(resolver)

        val result = runCatching {
            withContext(parentJob) {
                reader.children(TREE_URI, ROOT_DOCUMENT_ID)
            }
        }

        assertThat(result.exceptionOrNull()).isInstanceOf(CancellationException::class.java)
        verify { cursor.close() }
    }

    private fun childrenUri(): Uri = DocumentsContract.buildChildDocumentsUriUsingTree(
        Uri.parse(TREE_URI),
        ROOT_DOCUMENT_ID,
    )

    @Test
    fun take_persistable_permission_saves_read_and_write() = runTest {
        // Дефект 29.09.2026: сохранялось только чтение, и скачивание книги в папку отклонялось
        // провайдером, как только у приложения не было системного «доступа ко всем файлам».
        val resolver = mockk<ContentResolver>(relaxed = true)
        val reader = AndroidSafTreeReader(resolver)

        reader.takePersistablePermission(TREE_URI)

        verify(exactly = 1) {
            resolver.takePersistableUriPermission(Uri.parse(TREE_URI), PERSISTABLE_TREE_MODES)
        }
    }

    @Test
    fun take_persistable_permission_falls_back_to_read_only() = runTest {
        val resolver = mockk<ContentResolver>(relaxed = true)
        every {
            resolver.takePersistableUriPermission(any(), PERSISTABLE_TREE_MODES)
        } throws SecurityException("провайдер дал только чтение")
        val reader = AndroidSafTreeReader(resolver)

        val result = reader.takePersistablePermission(TREE_URI)

        // Папка обязана пережить перезапуск даже без записи: потерять источник целиком хуже,
        // чем не суметь в него скачать.
        assertThat(result.getOrNull()).isEqualTo(Unit)
        verify(exactly = 1) {
            resolver.takePersistableUriPermission(Uri.parse(TREE_URI), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    @Test
    fun write_permission_is_read_from_persisted_grants() {
        // UriPermission — системный класс с package-private конструктором, поэтому его свойства
        // подменяются: проверяется ровно то, что читает реализация.
        val writeGrant = mockk<UriPermission> {
            every { uri } returns Uri.parse(TREE_URI)
            every { isWritePermission } returns true
        }
        val readGrant = mockk<UriPermission> {
            every { uri } returns Uri.parse(TREE_URI)
            every { isWritePermission } returns false
        }
        val withWrite = mockk<ContentResolver>()
        every { withWrite.persistedUriPermissions } returns listOf(writeGrant)
        val readOnly = mockk<ContentResolver>()
        every { readOnly.persistedUriPermissions } returns listOf(readGrant)

        assertThat(AndroidSafTreeReader(withWrite).hasWritePermission(TREE_URI)).isTrue()
        assertThat(AndroidSafTreeReader(readOnly).hasWritePermission(TREE_URI)).isFalse()
    }

    @Test
    fun relative_path_keeps_the_folders_inside_the_tree() {
        // Дефект 29.09.2026: сканер писал в `relativePath` только имя файла, и книга из подпапки
        // выглядела лежащей в корне. Здесь проверяется, что корень дерева отбрасывается, а папки
        // внутри него — нет.
        val reader = AndroidSafTreeReader(mockk(relaxed = true))

        val path = reader.relativePath(TREE_URI, "$ROOT_DOCUMENT_ID/Автор/Книга.fb2")

        assertThat(path).isEqualTo("Автор/Книга.fb2")
    }

    @Test
    fun relative_path_falls_back_to_the_file_name_for_a_foreign_document() {
        // Идентификатор без общего с корнем префикса: полный идентификатор в поле «Путь» выглядел
        // бы мусором, поэтому берётся имя файла.
        val reader = AndroidSafTreeReader(mockk(relaxed = true))

        val path = reader.relativePath(TREE_URI, "другое-дерево/Книга.fb2")

        assertThat(path).isEqualTo("Книга.fb2")
    }

    private companion object {

        const val TREE_URI = "content://com.example.documents/tree/root-id"
        const val ROOT_DOCUMENT_ID = "root-id"

        val COLUMNS = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
    }
}
