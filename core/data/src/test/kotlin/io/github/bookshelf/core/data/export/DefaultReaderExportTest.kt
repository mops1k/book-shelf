package io.github.bookshelf.core.data.export

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.common.error.errorOrNull
import io.github.bookshelf.core.common.error.getOrNull
import io.github.bookshelf.core.common.testing.FakeAppClock
import io.github.bookshelf.core.datastore.SettingsRepository
import io.github.bookshelf.core.datastore.model.AppSettings
import io.github.bookshelf.core.datastore.model.ReaderSettings
import io.github.bookshelf.core.readerlink.launch.ExportedUriFactory
import io.github.bookshelf.core.storage.files.BookContentOpener
import java.io.File
import java.io.InputStream
import java.nio.channels.FileChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Временный экспорт книги для читалки (`DefaultReaderExport`).
 *
 * Проверяется то, из-за чего экспорт может навредить: копия создаётся при выключенной настройке,
 * копии копятся и съедают диск, ошибка копирования подменяется общим текстом. Все три случая
 * незаметны на глаз — книгу читалка просто не откроет или место кончится через неделю.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class DefaultReaderExportTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val opener = FakeOpener()
    private val settings = FakeSettingsRepository()
    private val clock = FakeAppClock()

    @Test
    fun `disabled export refuses without touching the disk`() {
        settings.tempExportEnabled = false
        val export = export(temporaryFolder.newFolder("export"))

        val result = runBlocking { export.exportedReaderUri(DOCUMENT_URI) }

        assertThat(result.getOrNull()).isNull()
        assertThat(result.errorOrNull()?.message).contains("Временный экспорт")
        assertThat(opener.calls).isEqualTo(0)
    }

    @Test
    fun `enabled export copies the book and returns the shared address`() = runBlocking {
        settings.tempExportEnabled = true
        val export = export(temporaryFolder.newFolder("export"))
        opener.path = "/tmp/export/книга.fb2"

        val uri = export.exportedReaderUri(DOCUMENT_URI).getOrNull()

        assertThat(opener.lastDocumentUri).isEqualTo(DOCUMENT_URI)
        // Адрес обязан приходить из фабрики: только она знает authority FileProvider, по которому
        // копию видит другая программа.
        assertThat(uri.toString()).isEqualTo("content://provider/export/книга.fb2")
    }

    @Test
    fun `copy failure is surfaced with its own reason`() = runBlocking {
        settings.tempExportEnabled = true
        opener.error = AppError.InvalidArgument(what = "bookSize", message = "Файл слишком большой")
        val export = export(temporaryFolder.newFolder("export"))

        val result = export.exportedReaderUri(DOCUMENT_URI)

        assertThat(result.errorOrNull()?.message).contains("слишком большой")
    }

    @Test
    fun `old copies are removed before a new one is made`() = runBlocking {
        settings.tempExportEnabled = true
        val exportDir = temporaryFolder.newFolder("export")
        val old = File(exportDir, "старая-копия").apply { writeText("книга"); setLastModified(0L) }
        val fresh = File(exportDir, "свежая-копия").apply { writeText("книга") }
        val export = export(exportDir)

        export.exportedReaderUri(DOCUMENT_URI)

        // Копии книг — это мегабайты: без уборки каталог растёт, пока не кончится место.
        assertThat(old.exists()).isFalse()
        assertThat(fresh.exists()).isTrue()
    }

    private fun export(exportDir: File) = DefaultReaderExport(
        opener = opener,
        settings = settings,
        uriFactory = ExportedUriFactory { file -> Uri.parse("content://provider/export/${file.name}") },
        exportDir = exportDir,
        clock = clock,
    )

    private class FakeOpener : BookContentOpener {

        var path: String? = null
        var error: AppError? = null
        var calls: Int = 0
        var lastDocumentUri: String? = null

        override fun openStream(documentUri: String): InputStream? = null

        override fun openSeekableChannel(documentUri: String): FileChannel? = null

        override fun sizeOf(documentUri: String): Long? = null

        override fun replaceContent(documentUri: String, bytes: ByteArray): AppResult<Unit> =
            AppResult.failure(AppError.Unknown(message = "замена в этом тесте не нужна"))

        override fun tempCopy(documentUri: String): AppResult<String> =
            AppResult.failure(AppError.Unknown(message = "в этом тесте не используется"))

        override fun exportCopy(documentUri: String): AppResult<String> {
            calls++
            lastDocumentUri = documentUri
            val failure = error
            val target = path
            return when {
                failure != null -> AppResult.failure(failure)
                target != null -> AppResult.success(target)
                else -> AppResult.failure(AppError.Unknown(message = "копия не подготовлена"))
            }
        }
    }

    private class FakeSettingsRepository : SettingsRepository {

        var tempExportEnabled: Boolean = true

        override val settings: Flow<AppSettings> = flowOf(AppSettings())

        override suspend fun current(): AppSettings = AppSettings(
            reader = ReaderSettings(tempExportEnabled = tempExportEnabled),
        )

        override suspend fun update(transform: (AppSettings) -> AppSettings) = Unit

        override suspend fun resetToDefaults() = Unit
    }

    private companion object {
        const val DOCUMENT_URI = "content://documents/tree/books/document/book.fb2"

    }
}
