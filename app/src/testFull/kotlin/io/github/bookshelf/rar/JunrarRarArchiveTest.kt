package io.github.bookshelf.rar

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.io.SeekableFile
import io.github.bookshelf.core.metadata.parser.BookInput
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * junrar-реализация порта чтения RAR — единственное место в проекте, где вызывается библиотека
 * под UnRar License (ADR-007), поэтому проверяется на НАСТОЯЩЕМ архиве: `libarchive-sample.rar`
 * (происхождение — `app/src/testFull/resources/fixtures/rar/SOURCE.md`).
 *
 * Проверяем то, на чём легко ошибиться молча: записи читаются в порядке архива, поток отдаёт
 * именно содержимое записи, второй проход видит тот же архив (парсер читает его дважды —
 * за метаданными и за обложкой), а не-RAR файл даёт понятную ошибку, а не пустой список.
 */
class JunrarRarArchiveTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `entries of a real rar archive are read in order with their content`() = runTest {
        val file = fixture()
        val read = mutableListOf<Pair<String, String>>()

        JunrarRarArchive().forEachEntry(input(file)) { entry, open ->
            if (!entry.name.endsWith("/")) read += entry.name to open().readBytes().decodeToString()
        }

        assertThat(read.map { it.first })
            .containsAtLeast("test.txt", "testdir\\test.txt")
            .inOrder()
        // В архиве запись хранится с CRLF — сравниваем байты как есть: так проверка ловит
        // подмену содержимого, а не только факт «что-то прочитали».
        assertThat(read.first { it.first == "test.txt" }.second).isEqualTo("test text document\r\n")
    }

    @Test
    fun `the archive can be read twice with the same result`() = runTest {
        // Парсер комикса делает два прохода: метаданные, затем обложка. Канал между проходами
        // обязан вернуться в начало, иначе второй проход молча не найдёт ничего.
        val file = fixture()
        val archive = JunrarRarArchive()
        val input = input(file)

        val first = names(archive, input)
        val second = names(archive, input)

        assertThat(second).isEqualTo(first)
        assertThat(first).isNotEmpty()
    }

    @Test
    fun `a file that is not a rar archive fails instead of looking empty`() = runTest {
        val file = temporaryFolder.newFile("not-a-rar.cbr").apply { writeText("это не архив") }

        val failure = runCatching { names(JunrarRarArchive(), input(file)) }.exceptionOrNull()

        assertThat(failure).isNotNull()
    }

    private fun names(archive: JunrarRarArchive, input: BookInput): List<String> {
        val names = mutableListOf<String>()
        archive.forEachEntry(input) { entry, _ -> names += entry.name }
        return names
    }

    private fun fixture(): File =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/rar/libarchive-sample.rar")) {
            "Фикстура fixtures/rar/libarchive-sample.rar не найдена в тестовых ресурсах"
        }.use { source ->
            temporaryFolder.newFile("sample.cbr").also { target ->
                target.outputStream().use { source.copyTo(it) }
            }
        }

    private fun input(file: File): BookInput = BookInput(
        fileName = file.name,
        extension = "cbr",
        mimeType = "application/vnd.comicbook-rar",
        sizeBytes = file.length(),
        openStream = { ByteArrayInputStream(file.readBytes()) },
        openSeekable = { seekable(file) },
        bytes = { offset, length ->
            file.readBytes().copyOfRange(offset.toInt(), (offset + length).toInt())
        },
    )

    private fun seekable(file: File): SeekableFile {
        val channel = FileChannel.open(file.toPath(), StandardOpenOption.READ)
        return SeekableFile(channel = channel, owner = Closeable { })
    }
}
