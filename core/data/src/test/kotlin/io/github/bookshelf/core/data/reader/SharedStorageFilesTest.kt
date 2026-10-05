package io.github.bookshelf.core.data.reader

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.testing.RecordingAppLogger
import io.github.bookshelf.core.common.testing.TestAppDispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Чтение файлов читалок на общей памяти (`SharedStorageFiles`).
 *
 * Так читается прогресс Librera: JSON-файлы лежат в `/sdcard/Librera/profile.Librera/device.<модель>/`,
 * и путь к ним не вывести из пути книги — их надо найти в каталоге и прочитать по имени. Нужен доступ
 * ко всей памяти, поэтому «нет каталога» и «нет файла» здесь обычное дело: это «прогресса нет».
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class SharedStorageFilesTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val files = SharedStorageFiles(TestAppDispatchers(), RecordingAppLogger())

    @Test
    fun `a text file is read as text`() = runBlocking {
        val file = folder.newFile("app-Progress.json").apply { writeText("""{"книга": {"p": 0.5}}""") }

        assertThat(files.read(file.absolutePath)).isEqualTo("""{"книга": {"p": 0.5}}""")
    }

    @Test
    fun `a missing file reads as nothing`() = runBlocking {
        val path = File(folder.root, "нет-такого.json").absolutePath

        assertThat(files.read(path)).isNull()
    }

    @Test
    fun `a directory is not a file`() = runBlocking {
        // Прочитать каталог как файл нельзя, и падать из-за этого синхронизация не должна.
        val dir = folder.newFolder("device.HiBreak")

        assertThat(files.read(dir.absolutePath)).isNull()
    }

    @Test
    fun `a huge file is cut off instead of pulled into memory whole`() = runBlocking {
        // Чужой файл может оказаться чем угодно: читаем с ограничением, а не целиком в память.
        val file = folder.newFile("app-Recent.json").apply {
            writeText("x".repeat(SharedStorageFiles.MAX_TEXT_BYTES + 5_000))
        }

        val text = files.read(file.absolutePath).orEmpty()

        assertThat(text.length).isAtMost(SharedStorageFiles.MAX_TEXT_BYTES)
        assertThat(text).isNotEmpty()
    }

    @Test
    fun `a directory listing gives names`(): Unit = runBlocking {
        val dir = folder.newFolder("profile.Librera")
        File(dir, "device.HiBreak").mkdirs()
        File(dir, "app-Progress.json").writeText("{}")

        assertThat(files.children(dir.absolutePath)).containsExactly("device.HiBreak", "app-Progress.json")
    }

    @Test
    fun `a missing directory lists as empty`() = runBlocking {
        val dir = File(folder.root, "нет-такого").absolutePath

        assertThat(files.children(dir)).isEmpty()
    }

    @Test
    fun `a file path lists as empty`() = runBlocking {
        val file = folder.newFile("app-Progress.json").apply { writeText("{}") }

        assertThat(files.children(file.absolutePath)).isEmpty()
    }
}
