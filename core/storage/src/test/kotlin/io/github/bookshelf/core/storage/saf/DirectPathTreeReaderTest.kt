package io.github.bookshelf.core.storage.saf

import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.errorOrNull
import io.github.bookshelf.core.common.error.getOrNull
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Обход дерева по обычному пути файловой системы.
 *
 * Нужен для съёмного носителя, который прошивка смонтировала только приватно
 * (`/mnt/media_rw/<uuid>`, проверено на Bigme B6 Color 02.10.2026): SAF про такой том не знает, а
 * приложение с доступом ко всем файлам читает его напрямую.
 *
 * Идентификатор документа — путь относительно корня источника, а не абсолютный путь: при
 * переподключении носитель может оказаться в другой точке монтирования, и книга не должна
 * превратиться в новую.
 */
class DirectPathTreeReaderTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val reader = DirectPathTreeReader()

    @Test
    fun `children of the root carry relative ids and folder flags`() = runBlocking<Unit> {
        val root = folder.newFolder("card")
        folder.newFolder("card", "Лем")
        folder.newFile("card/Солярис.fb2")

        val entries = reader.children(root.absolutePath, "").getOrNull()!!

        assertThat(entries.map { entry -> entry.documentId }).containsExactly("Лем", "Солярис.fb2")
        assertThat(entries.first { entry -> entry.displayName == "Лем" }.isDirectory).isTrue()
        val book = entries.first { entry -> entry.displayName == "Солярис.fb2" }
        assertThat(book.isDirectory).isFalse()
        assertThat(book.sizeBytes).isNotNull()
        assertThat(book.lastModified).isNotNull()
        assertThat(book.extension).isEqualTo("fb2")
    }

    @Test
    fun `children of a subfolder keep the path inside the id`() = runBlocking<Unit> {
        val root = folder.newFolder("card")
        folder.newFolder("card", "Лем")
        folder.newFile("card/Лем/Эдем.fb2")

        val entries = reader.children(root.absolutePath, "Лем").getOrNull()!!

        assertThat(entries.map { entry -> entry.documentId }).containsExactly("Лем/Эдем.fb2")
        assertThat(reader.relativePath(root.absolutePath, "Лем/Эдем.fb2")).isEqualTo("Лем/Эдем.fb2")
    }

    @Test
    fun `hidden files are listed and hiding is left to the scan filter`() = runBlocking<Unit> {
        val root = folder.newFolder("card")
        folder.newFile("card/.nomedia")

        val entries = reader.children(root.absolutePath, "").getOrNull()!!

        assertThat(entries.single().isHidden).isTrue()
    }

    @Test
    fun `a missing folder is a failure, not an empty list`() = runBlocking<Unit> {
        val root = folder.newFolder("card")

        val failure = reader.children(root.absolutePath, "нет-такой").errorOrNull()

        assertThat(failure).isInstanceOf(AppError.NotFound::class.java)
    }

    @Test
    fun `a path outside the root is refused`() = runBlocking<Unit> {
        val root = folder.newFolder("card")
        folder.newFile("outside.fb2")

        val failure = reader.children(root.absolutePath, "../..").errorOrNull()

        assertThat(failure).isInstanceOf(AppError.InvalidArgument::class.java)
    }

    @Test
    fun `a document address is a plain file path`() = runBlocking<Unit> {
        val root = folder.newFolder("card")
        folder.newFolder("card", "Лем")

        val address = reader.documentUri(root.absolutePath, "Лем/Эдем.fb2")

        assertThat(address).isEqualTo("${root.absolutePath}/Лем/Эдем.fb2")
    }

    @Test
    fun `read access is the folder's own readability`() {
        val root = folder.newFolder("card")

        assertThat(reader.hasPersistedPermission(root.absolutePath)).isTrue()
        assertThat(reader.hasWritePermission(root.absolutePath)).isTrue()
        assertThat(reader.hasPersistedPermission("${root.absolutePath}/нет-такой")).isFalse()
    }

    @Test
    fun `saving permission checks that the folder is readable`() = runBlocking<Unit> {
        val root = folder.newFolder("card")

        assertThat(reader.takePersistablePermission(root.absolutePath).getOrNull()).isEqualTo(Unit)
        val failure = reader.takePersistablePermission("${root.absolutePath}/нет-такой").errorOrNull()
        assertThat(failure).isInstanceOf(AppError.PermissionDenied::class.java)
    }
}
