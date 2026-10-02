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
import java.io.File
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
        val opener = DefaultBookContentOpener(
            resolver = resolver,
            importDir = temporaryFolder.newFolder("import"),
            exportDir = temporaryFolder.newFolder("export"),
        )

        val actual = opener.openStream(URI)!!.readBytes()

        assertThat(actual).isEqualTo(payload)
    }

    @Test
    fun size_of_reads_openable_size_column() {
        val resolver = mockk<ContentResolver>()
        val cursor = MatrixCursor(arrayOf(OpenableColumns.SIZE)).apply { addRow(arrayOf<Any?>(42L)) }
        every { resolver.query(any(), any(), any(), any(), any()) } returns cursor
        val opener = DefaultBookContentOpener(
            resolver = resolver,
            importDir = temporaryFolder.newFolder("import"),
            exportDir = temporaryFolder.newFolder("export"),
        )

        assertThat(opener.sizeOf(URI)).isEqualTo(42L)
    }

    @Test
    fun temp_copy_writes_atomically_and_removes_temporary_file() {
        val resolver = mockk<ContentResolver>()
        val payload = "содержимое книги".toByteArray()
        every { resolver.openInputStream(Uri.parse(URI)) } returns ByteArrayInputStream(payload)
        val tempDir = temporaryFolder.newFolder("import")
        val opener = DefaultBookContentOpener(
            resolver = resolver,
            importDir = tempDir,
            exportDir = temporaryFolder.newFolder("export"),
        )

        val path = opener.tempCopy(URI).getOrNull()

        assertThat(path).isNotNull()
        assertThat(Files.readAllBytes(java.nio.file.Path.of(path!!))).isEqualTo(payload)
        assertThat(tempDir.listFiles().orEmpty().filter { it.name.endsWith(".tmp") }).isEmpty()
    }

    @Test
    fun export_copy_goes_to_the_export_directory() {
        val resolver = mockk<ContentResolver>()
        val payload = "книга для читалки".toByteArray()
        every { resolver.openInputStream(Uri.parse(URI)) } returns ByteArrayInputStream(payload)
        val importDir = temporaryFolder.newFolder("import")
        val exportDir = temporaryFolder.newFolder("export")
        val opener = DefaultBookContentOpener(resolver, importDir, exportDir)

        val path = opener.exportCopy(URI, "epub").getOrNull()

        // Копия для читалки обязана лежать в каталоге, открытом через FileProvider: иначе
        // другая программа её просто не увидит, и «запасной путь» окажется пустым.
        assertThat(path).isNotNull()
        assertThat(java.io.File(path!!).parentFile).isEqualTo(exportDir)
        assertThat(importDir.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun export_copy_keeps_the_book_extension() {
        val resolver = mockk<ContentResolver>()
        every { resolver.openInputStream(Uri.parse(URI)) } returns ByteArrayInputStream("книга".toByteArray())
        val exportDir = temporaryFolder.newFolder("export")
        val opener = DefaultBookContentOpener(resolver, temporaryFolder.newFolder("import"), exportDir)

        val path = opener.exportCopy(URI, "fb2").getOrNull()

        // ДЕФЕКТ (отчёт Александра 01.10.2026: «нажимал читать на одной книге, а открывалась
        // другая»): копия называлась голым UUID. Читалка, которая определяет формат по имени
        // файла, такой файл не опознаёт и открывает свою последнюю книгу вместо выбранной.
        assertThat(java.io.File(path!!).name).endsWith(".fb2")
    }

    @Test
    fun export_copy_does_not_take_a_directory_from_the_extension() {
        val resolver = mockk<ContentResolver>()
        every { resolver.openInputStream(Uri.parse(URI)) } returns ByteArrayInputStream("книга".toByteArray())
        val exportDir = temporaryFolder.newFolder("export")
        val opener = DefaultBookContentOpener(resolver, temporaryFolder.newFolder("import"), exportDir)

        val path = opener.exportCopy(URI, "../fb2/../../secret").getOrNull()

        // Расширение приходит из имени чужого файла: разделитель пути в имени копии означал бы
        // запись за пределами каталога экспорта, а «..» — подмену файла снаружи.
        val copy = java.io.File(path!!)
        assertThat(copy.parentFile).isEqualTo(exportDir)
        assertThat(copy.name).doesNotContain("/")
        assertThat(copy.name).doesNotContain("..")
    }

    @Test
    fun export_copy_rejects_file_over_limit() {
        val resolver = mockk<ContentResolver>()
        val cursor = MatrixCursor(arrayOf(OpenableColumns.SIZE)).apply {
            addRow(arrayOf<Any?>(DefaultBookContentOpener.DEFAULT_MAX_TEMP_COPY_BYTES + 1L))
        }
        every { resolver.query(any(), any(), any(), any(), any()) } returns cursor
        val opener = DefaultBookContentOpener(
            resolver,
            temporaryFolder.newFolder("import"),
            temporaryFolder.newFolder("export"),
        )

        val result = opener.exportCopy(URI, "epub")

        assertThat(result.getOrNull()).isNull()
        assertThat(result.errorOrNull()).isInstanceOf(AppError.InvalidArgument::class.java)
    }

    @Test
    fun temp_copy_rejects_file_over_limit() {
        val resolver = mockk<ContentResolver>()
        val cursor = MatrixCursor(arrayOf(OpenableColumns.SIZE)).apply {
            addRow(arrayOf<Any?>(DefaultBookContentOpener.DEFAULT_MAX_TEMP_COPY_BYTES + 1L))
        }
        every { resolver.query(any(), any(), any(), any(), any()) } returns cursor
        val opener = DefaultBookContentOpener(
            resolver = resolver,
            importDir = temporaryFolder.newFolder("import"),
            exportDir = temporaryFolder.newFolder("export"),
        )

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
            exportDir = temporaryFolder.newFolder("export"),
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
            exportDir = temporaryFolder.newFolder("export"),
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
        val opener = DefaultBookContentOpener(
            resolver = resolver,
            importDir = temporaryFolder.newFolder("import"),
            exportDir = temporaryFolder.newFolder("export"),
        )

        val handle = opener.openSeekableChannel(URI)!!
        val buffer = ByteBuffer.allocate(3)
        handle.channel.read(buffer)

        assertThat(buffer.array()).isEqualTo(byteArrayOf(7, 8, 9))

        handle.close()

        // Дескриптор освобождается вместе с каналом: иначе он оставался бы открытым до сборки мусора.
        assertThat(handle.channel.isOpen).isFalse()
        assertThat(descriptor.fileDescriptor.valid()).isFalse()
    }


    @Test
    fun open_stream_reads_bytes_by_file_path() {
        val book = temporaryFolder.newFile("book.fb2").apply { writeBytes(byteArrayOf(7, 8, 9)) }
        val opener = fileOpener()

        assertThat(opener.openStream(book.absolutePath)!!.readBytes()).isEqualTo(byteArrayOf(7, 8, 9))
        // `file://` — тот же путь: адрес приходит и в таком виде.
        assertThat(opener.openStream("file://${book.absolutePath}")!!.readBytes()).isEqualTo(byteArrayOf(7, 8, 9))
    }

    @Test
    fun a_file_path_gives_a_seekable_channel() {
        val book = temporaryFolder.newFile("book.fb2").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val opener = fileOpener()

        opener.openSeekableChannel(book.absolutePath)!!.use { seekable ->
            val buffer = ByteBuffer.allocate(2)
            seekable.channel.position(1)
            seekable.channel.read(buffer)
            assertThat(buffer.array()).isEqualTo(byteArrayOf(2, 3))
            assertThat(seekable.channel.isOpen).isTrue()
        }
    }

    @Test
    fun size_and_facts_of_a_file_are_read_from_the_file() {
        val book = temporaryFolder.newFile("book.fb2").apply { writeBytes(ByteArray(5)) }
        val opener = fileOpener()

        assertThat(opener.sizeOf(book.absolutePath)).isEqualTo(5L)
        val facts = opener.factsOf(book.absolutePath)!!
        assertThat(facts.sizeBytes).isEqualTo(5L)
        assertThat(facts.lastModified).isNotNull()
        // Несуществующий файл — не выдуманный ноль, а отсутствие данных.
        assertThat(opener.sizeOf("${book.absolutePath}.нет")).isNull()
        assertThat(opener.factsOf("${book.absolutePath}.нет")).isNull()
    }

    @Test
    fun a_file_copy_lands_in_the_export_directory() {
        val book = temporaryFolder.newFile("book.fb2").apply { writeBytes("текст".toByteArray()) }
        val opener = fileOpener()

        val copy = opener.exportCopy(book.absolutePath, "fb2").getOrNull()!!

        assertThat(File(copy).readBytes()).isEqualTo("текст".toByteArray())
        assertThat(copy).endsWith(".fb2")
    }

    @Test
    fun replacing_content_writes_the_file() {
        val book = temporaryFolder.newFile("book.fb2")
        val opener = fileOpener()

        opener.replaceContent(book.absolutePath, byteArrayOf(9, 9)).getOrNull()

        assertThat(book.readBytes()).isEqualTo(byteArrayOf(9, 9))
    }

    @Test
    fun provider_addresses_are_told_apart_from_file_paths() {
        assertThat(filePathOf("content://provider/tree/1A2B-3C4D%3A")).isNull()
        assertThat(filePathOf("/mnt/media_rw/0436-C297/книга.fb2")).isEqualTo("/mnt/media_rw/0436-C297/книга.fb2")
        assertThat(filePathOf("file:///mnt/media_rw/0436-C297/книга.fb2"))
            .isEqualTo("/mnt/media_rw/0436-C297/книга.fb2")
        assertThat(filePathOf("   ")).isNull()
    }

    /** Opener для проверок по обычному пути: резолвер в этой ветке не участвует. */
    private fun fileOpener(): DefaultBookContentOpener = DefaultBookContentOpener(
        resolver = mockk(relaxed = true),
        importDir = temporaryFolder.newFolder("import"),
        exportDir = temporaryFolder.newFolder("export"),
    )

    private companion object {

        const val URI = "content://com.example.documents/document/book-1"
    }
}
