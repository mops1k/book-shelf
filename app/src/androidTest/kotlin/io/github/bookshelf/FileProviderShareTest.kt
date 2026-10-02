package io.github.bookshelf

import android.content.Context
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FileProvider: какие файлы приложение отдаёт другим (ADR-004).
 *
 * Проверяется на настоящем провайдере и настоящих `file_paths.xml`: временные копии книг для
 * читалок из `cacheDir/export` отдаются и читаются, а сама библиотека — нет. Так проверяется
 * и объявление провайдера в манифесте, и объявленные пути.
 */
@RunWith(AndroidJUnit4::class)
class FileProviderShareTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun a_cached_export_is_readable_by_another_app() {
        val exportDir = File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }
        val exported = File(exportDir, "book.epub")
        exported.writeBytes(EPUB_BYTES)

        val uri = FileProvider.getUriForFile(context, authority(), exported)

        assertThat(uri.authority).isEqualTo(authority())
        assertThat(uri.path).startsWith("/$EXPORT_DIR")
        // Читаем через провайдер, а не через файл: так проверяется и путь, и выдача.
        val read = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        assertThat(read).isEqualTo(EPUB_BYTES)
    }

    @Test
    fun the_library_outside_the_export_folder_is_not_exposed() {
        // Библиотека лежит в SAF-дереве пользователя, но даже файл в cacheDir вне `export/`
        // провайдер отдавать не должен: иначе одним объявленным путём утекала бы вся папка.
        val outside = File(context.cacheDir, "library/book.epub").apply {
            parentFile?.mkdirs()
            writeBytes(EPUB_BYTES)
        }

        val failure = runCatching { FileProvider.getUriForFile(context, authority(), outside) }

        assertThat(failure.isFailure).isTrue()
    }

    private fun authority(): String = "${context.packageName}.fileprovider"

    private companion object {
        const val EXPORT_DIR = "export"
        val EPUB_BYTES = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 1, 2, 3)
    }
}
