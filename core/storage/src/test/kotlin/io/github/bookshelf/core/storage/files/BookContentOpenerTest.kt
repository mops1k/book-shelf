package io.github.bookshelf.core.storage.files

import android.content.ContentResolver
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.errorOrNull
import io.github.bookshelf.core.common.error.getOrNull
import io.mockk.every
import io.mockk.mockk
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.file.Files

/** Проверка доступа к содержимому SAF: поток, размер и атомарная временная копия. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class BookContentOpenerTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun open_stream_reads_bytes_from_content_resolver() {
        val resolver = mockk<ContentResolver>()
        val uri = Uri.parse(URI)
        val payload = byteArrayOf(1, 2, 3)
        every { resolver.openInputStream(uri) } returns ByteArrayInputStream(payload)
        val opener = DefaultBookContentOpener(resolver, temporaryFolder.newFolder("import"))

        val actual = opener.openStream(URI)!!.readBytes()

        assertThat(actual).isEqualTo(payload)
    }

    @Test
    fun size_of_reads_openable_size_column() {
        val resolver = mockk<ContentResolver>()
        val cursor = MatrixCursor(arrayOf(OpenableColumns.SIZE)).apply { addRow(arrayOf<Any?>(42L)) }
        every { resolver.query(any(), any(), any(), any(), any()) } returns cursor
        val opener = DefaultBookContentOpener(resolver, temporaryFolder.newFolder("import"))

        assertThat(opener.sizeOf(URI)).isEqualTo(42L)
    }

    @Test
    fun temp_copy_writes_atomically_and_removes_temporary_file() {
        val resolver = mockk<ContentResolver>()
        val payload = "содержимое книги".toByteArray()
        every { resolver.openInputStream(Uri.parse(URI)) } returns ByteArrayInputStream(payload)
        val tempDir = temporaryFolder.newFolder("import")
        val opener = DefaultBookContentOpener(resolver, tempDir)

        val path = opener.tempCopy(URI).getOrNull()

        assertThat(path).isNotNull()
        assertThat(Files.readAllBytes(java.nio.file.Path.of(path!!))).isEqualTo(payload)
        assertThat(tempDir.listFiles().orEmpty().filter { it.name.endsWith(".tmp") }).isEmpty()
    }

    @Test
    fun temp_copy_rejects_file_over_limit() {
        val resolver = mockk<ContentResolver>()
        val cursor = MatrixCursor(arrayOf(OpenableColumns.SIZE)).apply {
            addRow(arrayOf<Any?>(DefaultBookContentOpener.DEFAULT_MAX_TEMP_COPY_BYTES + 1L))
        }
        every { resolver.query(any(), any(), any(), any(), any()) } returns cursor
        val opener = DefaultBookContentOpener(resolver, temporaryFolder.newFolder("import"))

        val result = opener.tempCopy(URI)

        assertThat(result.errorOrNull()).isInstanceOf(AppError.InvalidArgument::class.java)
    }

    @Test
    fun temp_copy_uses_the_limit_from_settings_provider() {
        // Лимит приходит из настроек (`library.tempCopyLimitMb`), а не из константы: проверяем,
        // что уменьшенный лимит действительно отказывает файлу, который прошёл бы по умолчанию.
        val limitBytes = 1024L
        val resolver = mockk<ContentResolver>()
        val cursor = MatrixCursor(arrayOf(OpenableColumns.SIZE)).apply {
            addRow(arrayOf<Any?>(limitBytes + 1L))
        }
        every { resolver.query(any(), any(), any(), any(), any()) } returns cursor
        val opener = DefaultBookContentOpener(
            resolver = resolver,
            importDir = temporaryFolder.newFolder("import"),
            tempCopyLimitBytes = { limitBytes },
        )

        val result = opener.tempCopy(URI)

        assertThat(result.errorOrNull()).isInstanceOf(AppError.InvalidArgument::class.java)
    }

    @Test
    fun temp_copy_reads_the_limit_once_per_copy() {
        // Значение берётся до первой проверки и переиспользуется после записи: если пользователь
        // поменяет настройку между проверками, копия всё равно оценивается одним лимитом.
        var reads = 0
        val resolver = mockk<ContentResolver>()
        every { resolver.query(any(), any(), any(), any(), any()) } returns null
        every { resolver.openInputStream(Uri.parse(URI)) } returns ByteArrayInputStream(byteArrayOf(1))
        val opener = DefaultBookContentOpener(
            resolver = resolver,
            importDir = temporaryFolder.newFolder("import"),
            tempCopyLimitBytes = {
                reads += 1
                DefaultBookContentOpener.DEFAULT_MAX_TEMP_COPY_BYTES
            },
        )

        opener.tempCopy(URI)

        assertThat(reads).isEqualTo(1)
    }

    @Test
    fun open_seekable_channel_reads_local_descriptor() {
        val file = temporaryFolder.newFile("book.bin").apply { writeBytes(byteArrayOf(7, 8, 9)) }
        val resolver = mockk<ContentResolver>()
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        every { resolver.openFileDescriptor(Uri.parse(URI), "r") } returns descriptor
        val opener = DefaultBookContentOpener(resolver, temporaryFolder.newFolder("import"))

        val channel = opener.openSeekableChannel(URI)!!
        val buffer = ByteBuffer.allocate(3)
        channel.read(buffer)

        assertThat(buffer.array()).isEqualTo(byteArrayOf(7, 8, 9))
        channel.close()
    }

    private companion object {

        const val URI = "content://com.example.documents/document/book-1"
    }
}
