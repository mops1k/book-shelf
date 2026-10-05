package io.github.bookshelf.core.data.repository.impl

import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.common.reader.ReaderSession
import io.github.bookshelf.core.common.testing.FakeAppClock
import io.github.bookshelf.core.data.repository.ReaderRepository
import io.github.bookshelf.core.database.BookshelfDatabase
import io.github.bookshelf.core.database.entity.BookEntity
import io.github.bookshelf.core.readerlink.discovery.InstalledReader
import io.github.bookshelf.core.readerlink.discovery.ReaderDiscovery
import io.github.bookshelf.core.readerlink.launch.LaunchRequest
import io.github.bookshelf.core.readerlink.launch.ReaderExport
import io.github.bookshelf.core.readerlink.launch.ReaderLauncher
import io.github.bookshelf.core.readerlink.profile.ReaderProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Книга открывается в ТОЙ читалке, которую выбрал человек.
 *
 * ДЕФЕКТ (Александр, 01.10.2026): «не видит других читалок, как будто он зашит в коде… должно
 * видеть и открывать в выбранной, к примеру в устройстве есть ещё XReader». Так и было: discovery
 * отбрасывал всё, чего нет в зашитом списке, а выбранная найденная читалка в базу не попадала —
 * `resolveProfile` не находил её по id и открывал первую из списка (FBReader).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class ReaderLaunchInChosenReaderTest {

    private lateinit var database: BookshelfDatabase
    private lateinit var repository: ReaderRepository
    private val launcher = RecordingLauncher()
    private val export = RecordingExport()
    private val discovery = FixedDiscovery(
        listOf(reader(FB_READER, "FBReader"), reader(X_READER, "XReader"), reader(PDF_VIEWER, "PDF Viewer")),
    )

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BookshelfDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = ReaderRepositoryImpl(
            discovery = discovery,
            launcher = launcher,
            export = export,
            profileDao = database.readerProfileDao(),
            bookDao = database.bookDao(),
            formatDefaultDao = database.formatDefaultDao(),
            session = NoSession,
            clock = FakeAppClock(),
        )
        runTest { database.bookDao().upsertAll(listOf(book())) }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `the book opens in the reader the person chose`() = runTest {
        // XReader не в зашитом списке известных читалок — раньше он не попадал даже в список выбора.
        repository.setDefault(format = "epub", profileId = X_READER)

        repository.openBook(bookId = BOOK_ID)

        assertThat(launcher.launched.map { request -> request.profile.packageName })
            .containsExactly(X_READER)
    }

    @Test
    fun `the copy for the reader keeps the book extension`() = runTest {
        // ДЕФЕКТ (Александр, 01.10.2026: «нажимал читать на одной книге, а в читалке открывалась
        // другая»). Запасной путь отдаёт читалке копию, а копия называлась голым UUID: FBReader
        // такой файл не опознаёт и открывает свою последнюю книгу. Расширение книги известно здесь,
        // в `LaunchRequest`, — значит оно обязано дойти и до экспорта, и до адреса читалки.
        repository.setDefault(format = "epub", profileId = FB_READER)
        launcher.failNext = true

        repository.openBook(bookId = BOOK_ID)

        assertThat(export.lastExtension).isEqualTo("epub")
        assertThat(launcher.launched.last().documentUri.toString()).endsWith(".epub")
    }

    @Test
    fun `a chosen reader is remembered for the next time`() = runTest {
        repository.setDefault(format = "epub", profileId = X_READER)

        // Профиль обязан появиться в базе: иначе следующий запуск снова искал бы его и не находил.
        val stored = database.readerProfileDao().getById(X_READER)

        assertThat(stored).isNotNull()
        assertThat(stored?.activityName).isEqualTo("com.xrz.xreaderV3.MainActivity")
    }

    @Test
    fun `a saved reader keeps the list of formats it can open`() = runTest {
        repository.setDefault(format = "cbr", profileId = X_READER)

        val summaries = repository.installedReaders().first()

        // ДЕФЕКТ, найденный на устройстве: у СОХРАНЁННОГО профиля список форматов был пуст
        // (заполнялся только у найденных), поэтому строка «CBR» пропадала с экрана сразу после
        // того, как человек выбирал для неё читалку.
        assertThat(summaries.first { summary -> summary.packageName == X_READER }.supportedExtensions)
            .containsAtLeast("epub", "cbr")
    }

    @Test
    fun `without a choice the app does not open books in a pdf viewer`() = runTest {
        repository.openBook(bookId = BOOK_ID)

        // Запасной ход берёт лучшую читалку, а не первое приложение, которое подошло под запрос.
        assertThat(launcher.launched.map { request -> request.profile.packageName })
            .containsExactly(FB_READER)
    }

    private fun reader(packageName: String, label: String) = InstalledReader(
        profile = ReaderProfile(
            id = packageName,
            name = label,
            packageName = packageName,
            activityName = "$packageName.MainActivity",
            // Так объявляет себя XReader на устройстве: epub и архив комиксов.
            mimeTypes = listOf("application/epub+zip", "application/vnd.comicbook-rar"),
            supportsContentUri = true,
            extrasJson = null,
            isBuiltIn = false,
        ),
        resolveInfoLike = "$packageName.MainActivity",
    )

    private fun book() = BookEntity(
        id = BOOK_ID,
        sourceId = "source:one",
        documentUri = "content://documents/one.epub",
        relativePath = "one.epub",
        fileName = "one.epub",
        extension = "epub",
        mimeType = "application/epub+zip",
        sizeBytes = 1_000L,
        lastModified = 1_000L,
        title = "Книга",
        sortTitle = "Книга",
        authorsText = "",
        addedAt = 1_000L,
        updatedAt = 1_000L,
        lastSeenAt = 1_000L,
    )

    private class RecordingLauncher : ReaderLauncher {
        val launched = mutableListOf<LaunchRequest>()

        /** Отказ первой попытки — так проверяется запасной путь через копию. */
        var failNext: Boolean = false

        override suspend fun launch(request: LaunchRequest): AppResult<Unit> {
            launched += request
            if (failNext) {
                failNext = false
                return AppResult.failure(io.github.bookshelf.core.common.error.AppError.PermissionDenied())
            }
            return AppResult.success(Unit)
        }
    }

    private class FixedDiscovery(private val readers: List<InstalledReader>) : ReaderDiscovery {
        override suspend fun installedReaders(): List<InstalledReader> = readers
    }

    /** Копия для читалки: запоминает, с каким расширением её просили. */
    private class RecordingExport : ReaderExport {
        var lastExtension: String? = null

        override suspend fun exportedReaderUri(documentUri: String, extension: String): AppResult<Uri> {
            lastExtension = extension
            return AppResult.success(Uri.parse("content://provider/export/copy.$extension"))
        }
    }

    private object NoSession : ReaderSession {
        override fun markOpened(bookId: String, readerProfileId: String?) = Unit

        override fun isActive(): Boolean = false

        override fun clear() = Unit

        override fun onAppForeground() = Unit

        override fun onAppBackground() = Unit
    }

    private companion object {
        const val BOOK_ID = "book:one"
        const val X_READER = "com.xrz.xreaderV3"
        const val FB_READER = "com.fbreader"
        const val PDF_VIEWER = "com.example.pdfviewer"
    }
}
