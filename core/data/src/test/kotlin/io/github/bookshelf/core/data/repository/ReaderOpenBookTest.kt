package io.github.bookshelf.core.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.common.error.errorOrNull
import io.github.bookshelf.core.common.error.getOrNull
import io.github.bookshelf.core.common.testing.FakeAppClock
import io.github.bookshelf.core.database.model.ReadingStatus
import io.github.bookshelf.core.data.repository.impl.ReaderRepositoryImpl
import io.github.bookshelf.core.database.BookshelfDatabase
import io.github.bookshelf.core.common.reader.ReaderSession
import io.github.bookshelf.core.database.entity.BookEntity
import io.github.bookshelf.core.readerlink.discovery.InstalledReader
import io.github.bookshelf.core.readerlink.discovery.ReaderDiscovery
import io.github.bookshelf.core.readerlink.launch.LaunchRequest
import io.github.bookshelf.core.readerlink.launch.ReaderExport
import io.github.bookshelf.core.readerlink.launch.ReaderLauncher
import io.github.bookshelf.core.readerlink.profile.ReaderProfile
import io.github.bookshelf.core.testing.fake.FakeReaderSession
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Открытие книги в читалке и запасной путь (`ReaderRepositoryImpl.openBook`).
 *
 * Проверяется то, чего не видно на устройстве при обычном запуске: что происходит, когда читалка
 * **отказалась** открыть наш `content://`. Тогда есть один запасной ход — временная копия файла,
 * отданная через FileProvider, — и он допустим только при включённом экспорте. Молча повторить
 * запуск с тем же адресом нельзя: пользователь увидит «нажал, ничего не произошло».
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class ReaderOpenBookTest {

    private lateinit var database: BookshelfDatabase
    private val launcher = FakeLauncher()
    private val export = FakeReaderExport()
    private val discovery = FakeReaderDiscovery()
    private val session = FakeReaderSession()
    private val clock = FakeAppClock()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BookshelfDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `successful launch does not touch the export`() = runBlocking {
        givenBook()

        val outcome = repository().openBook(BOOK_ID).getOrNull()

        assertThat(outcome).isNotNull()
        assertThat(launcher.requests).hasSize(1)
        assertThat(launcher.requests.single().documentUri.toString()).isEqualTo(DOCUMENT_URI)
        assertThat(export.calls).isEqualTo(0)
    }

    @Test
    fun `refused launch retries with the exported copy`() = runBlocking {
        givenBook()
        launcher.refuseUris = setOf(DOCUMENT_URI)
        export.exported = Uri.parse(EXPORTED_URI)

        val outcome = repository().openBook(BOOK_ID).getOrNull()

        assertThat(outcome).isNotNull()
        // Вторая попытка идёт с адресом копии, а не с тем же самым, который читалка отвергла.
        assertThat(launcher.requests).hasSize(2)
        assertThat(launcher.requests.last().documentUri.toString()).isEqualTo(EXPORTED_URI)
        assertThat(export.lastDocumentUri).isEqualTo(DOCUMENT_URI)
    }

    @Test
    fun `refused launch without export reports the setting`() = runBlocking {
        givenBook()
        // Экспорт выключен: его собственная проверка настройки возвращает эту ошибку.
        export.error = AppError.PermissionDenied(
            message = "Временный экспорт выключен — включите его в настройках → «Читалки».",
        )
        launcher.refuseUris = setOf(DOCUMENT_URI)

        val result = repository().openBook(BOOK_ID)

        assertThat(result.getOrNull()).isNull()
        // Причина должна быть в тексте: человеку нужно знать, что включить и где.
        assertThat(result.errorOrNull()?.message).contains("Временный экспорт")
        // Повтора запуска не было: адрес, который читалка уже отвергла, второй раз не отправляем.
        assertThat(launcher.requests).hasSize(1)
    }

    @Test
    fun `export failure is reported instead of a silent retry`() = runBlocking {
        givenBook()
        launcher.refuseUris = setOf(DOCUMENT_URI)
        export.error = AppError.InvalidArgument(what = "bookSize", message = "Файл больше лимита копии")

        val result = repository().openBook(BOOK_ID)

        assertThat(result.errorOrNull()?.message).contains("больше лимита")
        assertThat(launcher.requests).hasSize(1)
    }

    @Test
    fun `a successful launch starts the reading session`() = runBlocking {
        givenBook()

        repository().openBook(BOOK_ID)

        // Пока читалка открыта, тяжёлые работы обязаны уступать: 30.09.2026 её главный поток встал
        // в сборке мусора из-за нашей фоновой работы, и человек видел «FBReader не отвечает».
        assertThat(session.opened).isEqualTo(listOf(BOOK_ID))
    }

    @Test
    fun `a refused launch does not start the reading session`() = runBlocking {
        givenBook()
        launcher.refuseUris = setOf(DOCUMENT_URI)
        export.error = AppError.PermissionDenied(message = "Временный экспорт выключен")

        repository().openBook(BOOK_ID)

        // Читалка не открылась — чтения нет, и запрещать фоновую работу было бы вредно.
        assertThat(session.opened).isEmpty()
    }

    @Test
    fun `the fallback copy also starts the reading session`() = runBlocking {
        givenBook()
        launcher.refuseUris = setOf(DOCUMENT_URI)
        export.exported = Uri.parse(EXPORTED_URI)

        repository().openBook(BOOK_ID)

        // Открыли копией — чтение всё равно началось, и фон должен уступить так же.
        assertThat(session.opened).isEqualTo(listOf(BOOK_ID))
    }

    @Test
    fun `a successful launch marks the book as being read`() = runBlocking {
        givenBook()

        repository().openBook(BOOK_ID)

        // Требование Александра: статус меняется при открытии из полки, независимо от того,
        // есть ли у нас интеграция с читалкой и подхватился ли прогресс.
        val book = database.bookDao().getById(BOOK_ID)
        assertThat(book?.readingStatus).isEqualTo(ReadingStatus.READING)
        // Дата открытия — по ней полка сортирует «Дата открытия» и собирает полку «Читаю».
        assertThat(book?.lastOpenedAt).isNotNull()
    }

    @Test
    fun `a finished book keeps its status when opened again`() = runBlocking {
        givenBook(status = ReadingStatus.READ)

        repository().openBook(BOOK_ID)

        val book = database.bookDao().getById(BOOK_ID)
        // Человек мог открыть законченную книгу, чтобы перечитать главу: «прочитано» не сбрасываем.
        assertThat(book?.readingStatus).isEqualTo(ReadingStatus.READ)
        assertThat(book?.lastOpenedAt).isNotNull()
    }

    @Test
    fun `a refused launch does not change the status`() = runBlocking {
        givenBook()
        launcher.refuseUris = setOf(DOCUMENT_URI)
        export.error = AppError.PermissionDenied(message = "Временный экспорт выключен")

        repository().openBook(BOOK_ID)

        val book = database.bookDao().getById(BOOK_ID)
        // Книга не открылась — «читаю» было бы неправдой.
        assertThat(book?.readingStatus).isEqualTo(ReadingStatus.UNREAD)
        assertThat(book?.lastOpenedAt).isNull()
    }

    private fun repository() = ReaderRepositoryImpl(
        discovery = discovery,
        launcher = launcher,
        export = export,
        session = session,
        clock = clock,
        profileDao = database.readerProfileDao(),
        bookDao = database.bookDao(),
        formatDefaultDao = database.formatDefaultDao(),
    )

    private suspend fun givenBook(status: ReadingStatus = ReadingStatus.UNREAD) {
        database.bookDao().insert(
            BookEntity(
                id = BOOK_ID,
                sourceId = "source-1",
                documentUri = DOCUMENT_URI,
                relativePath = "Автор/Книга.fb2",
                fileName = "Книга.fb2",
                extension = "fb2",
                mimeType = "application/fb2",
                sizeBytes = 1_000L,
                lastModified = 1_000L,
                title = "Книга",
                sortTitle = "Книга",
                authorsText = "Автор",
                addedAt = 1_000L,
                updatedAt = 1_000L,
                lastSeenAt = 1_000L,
                readingStatus = status,
            ),
        )
    }

    private class FakeLauncher : ReaderLauncher {

        val requests = mutableListOf<LaunchRequest>()

        /**
         * Адреса, которые читалка отвергает.
         *
         * Именно по адресу, а не «всегда»: тогда видно, что повтор идёт с другим адресом, а не
         * повторяется тот же самый, который уже не сработал.
         */
        var refuseUris: Set<String> = emptySet()

        override suspend fun launch(request: LaunchRequest): AppResult<Unit> {
            requests += request
            return if (request.documentUri.toString() in refuseUris) {
                AppResult.failure(AppError.PermissionDenied(message = "Читалка отказала в доступе"))
            } else {
                AppResult.success(Unit)
            }
        }
    }

    private class FakeReaderExport : ReaderExport {

        var exported: Uri? = null
        var error: AppError? = null
        var calls: Int = 0
        var lastDocumentUri: String? = null

        override suspend fun exportedReaderUri(documentUri: String): AppResult<Uri> {
            calls++
            lastDocumentUri = documentUri
            val failure = error
            val uri = exported
            return when {
                failure != null -> AppResult.failure(failure)
                uri != null -> AppResult.success(uri)
                else -> AppResult.failure(AppError.NotFound(what = "export"))
            }
        }
    }

    /** Одна установленная читалка: этого достаточно, чтобы профиль выбрался без диалога. */
    private class FakeReaderDiscovery : ReaderDiscovery {

        override suspend fun installedReaders(): List<InstalledReader> = listOf(
            InstalledReader(
                profile = ReaderProfile(
                    id = READER_PACKAGE,
                    name = "Пример читалки",
                    packageName = READER_PACKAGE,
                    activityName = "$READER_PACKAGE.MainActivity",
                    mimeTypes = listOf("application/fb2"),
                    supportsContentUri = true,
                    extrasJson = null,
                    isBuiltIn = false,
                ),
                resolveInfoLike = "$READER_PACKAGE.MainActivity",
            ),
        )
    }

    private companion object {
        const val BOOK_ID = "book-1"
        const val DOCUMENT_URI =
            "content://com.android.externalstorage.documents/tree/primary%3ABooks/document/primary%3ABooks%2Fbook.fb2"
        const val EXPORTED_URI = "content://io.github.bookshelf.fileprovider/export/book.fb2"
        const val READER_PACKAGE = "com.example.reader"
    }
}
