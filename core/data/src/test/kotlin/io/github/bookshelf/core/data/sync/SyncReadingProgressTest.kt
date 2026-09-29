package io.github.bookshelf.core.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.common.error.getOrNull
import io.github.bookshelf.core.common.testing.FakeAppClock
import io.github.bookshelf.core.data.duplicates.DuplicateFinder
import io.github.bookshelf.core.data.model.ScanProgress
import io.github.bookshelf.core.data.model.ScanReport
import io.github.bookshelf.core.data.model.ScanStatus
import io.github.bookshelf.core.data.model.Source
import io.github.bookshelf.core.data.repository.BookRepository
import io.github.bookshelf.core.data.repository.ScanRepository
import io.github.bookshelf.core.data.repository.SourceRepository
import io.github.bookshelf.core.data.repository.ReadingProgressOrigins
import io.github.bookshelf.core.data.author.BookAuthorWriter
import io.github.bookshelf.core.data.repository.impl.BookRepositoryImpl
import io.github.bookshelf.core.database.BookshelfDatabase
import io.github.bookshelf.core.database.entity.BookEntity
import io.github.bookshelf.core.database.model.ReadingStatus
import io.github.bookshelf.core.datastore.SettingsRepository
import io.github.bookshelf.core.datastore.model.AppSettings
import io.github.bookshelf.core.datastore.model.ReaderSettings
import io.github.bookshelf.core.readerlink.fbreader.FbReaderApi
import io.github.bookshelf.core.readerlink.fbreader.FbReaderOpenBook
import io.github.bookshelf.core.readerlink.fbreader.FbReaderReadingPosition
import io.github.bookshelf.core.readerlink.fbreader.ReadingProgressSnapshot
import io.github.bookshelf.core.readerlink.fbreader.ReadingProgressSource
import io.github.bookshelf.core.storage.files.ContentHasher
import io.github.bookshelf.core.storage.saf.SafTreeWriter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Синхронизация прогресса из читалки (`SyncReadingProgress`).
 *
 * Проверяется порядок действий и границы, где ошибка тихая: если спросить читалку при выключенной
 * настройке — мы дёргаем чужой сервис без спроса; если записать процент чужой книги или книги,
 * которой нет в библиотеке, — на полке появится прогресс, которого не было.
 *
 * Хранилище здесь настоящее (Room в памяти): проверяются и поиск книги по адресу документа, и сама
 * запись в `reading_progress` — в том виде, в каком её потом читает полка.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class SyncReadingProgressTest {

    private lateinit var database: BookshelfDatabase
    private lateinit var repository: BookRepository
    private val reader = FakeFbReaderApi()
    private val progress = FakeProgressSource()
    private val settings = FakeSettingsRepository()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BookshelfDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = BookRepositoryImpl(
            bookDao = database.bookDao(),
            overrideDao = database.metadataOverrideDao(),
            readingProgressDao = database.readingProgressDao(),
            sourceRepository = FakeSourceRepository(),
            scanRepository = FakeScanRepository(),
            duplicateFinder = DuplicateFinder(FakeContentHasher()),
            fileRemover = FakeFileRemover(),
            clock = FakeAppClock().apply { setTo(1_000L) },
            authorWriter = BookAuthorWriter(database.authorDao()),
            bookCleanupDao = database.bookCleanupDao(),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `disabled setting never touches the reader`() = runBlocking {
        settings.enabled = false

        val outcome = sync().syncNow().getOrNull()

        assertThat(outcome).isEqualTo(ProgressSyncOutcome.Disabled)
        // Читалку не будили вовсе: при выключенной настройке обращаться к чужому сервису нельзя.
        assertThat(reader.connectCalls).isEqualTo(0)
    }

    @Test
    fun `unavailable reader is reported and nothing is written`() = runBlocking {
        settings.enabled = true
        reader.available = false
        givenBook(documentUri = DOCUMENT_URI)

        val outcome = sync().syncNow().getOrNull()

        assertThat(outcome).isEqualTo(ProgressSyncOutcome.ReaderUnavailable)
        assertThat(savedProgress()).isNull()
    }

    @Test
    fun `no book open is reported`() = runBlocking {
        settings.enabled = true
        reader.open = null

        val outcome = sync().syncNow().getOrNull()

        assertThat(outcome).isEqualTo(ProgressSyncOutcome.NoBookOpen)
        assertThat(savedProgress()).isNull()
    }

    @Test
    fun `book opened outside our library is not stored`() = runBlocking {
        settings.enabled = true
        reader.open = openBook(path = "content://tree/books/document/чужая.fb2")
        progress.snapshot = snapshot(percent = 42f)

        val outcome = sync().syncNow().getOrNull()

        // Книгу открыли в самой читалке, у нас её нет: записывать некуда и нечего.
        assertThat(outcome).isEqualTo(ProgressSyncOutcome.BookUnknown)
        assertThat(savedProgress()).isNull()
    }

    @Test
    fun `reader that cannot give progress leaves the library untouched`() = runBlocking {
        settings.enabled = true
        givenBook(documentUri = DOCUMENT_URI)
        reader.open = openBook(path = DOCUMENT_URI)
        progress.snapshot = null

        val outcome = sync().syncNow().getOrNull()

        assertThat(outcome).isEqualTo(ProgressSyncOutcome.NoBookOpen)
        assertThat(savedProgress()).isNull()
    }

    @Test
    fun `progress of the open book is stored as a share with its origin`() = runBlocking {
        settings.enabled = true
        givenBook(documentUri = DOCUMENT_URI)
        reader.open = openBook(path = DOCUMENT_URI)
        progress.snapshot = snapshot(percent = 25f)

        val outcome = sync().syncNow().getOrNull()

        assertThat(outcome).isEqualTo(ProgressSyncOutcome.Saved(bookId = BOOK_ID, percentPercent = 25f))
        val stored = savedProgress()
        // В таблице хранится доля, а не проценты: полка читает именно её.
        assertThat(stored?.percent).isWithin(0.0001f).of(0.25f)
        assertThat(stored?.source).isEqualTo(ReadingProgressOrigins.FBREADER_AIDL)
        assertThat(stored?.updatedAt).isEqualTo(1_000L)
    }

    @Test
    fun `book open in the reader is marked as reading`() = runBlocking {
        settings.enabled = true
        givenBook(documentUri = DOCUMENT_URI)
        reader.open = openBook(path = DOCUMENT_URI)
        progress.snapshot = snapshot(percent = 25f)

        sync().syncNow().getOrNull()

        // Полка показывает процент только у книги со статусом «читаю»: без этой отметки
        // синхронизация была бы невидимой, хотя данные записаны.
        assertThat(database.bookDao().getById(BOOK_ID)?.readingStatus).isEqualTo(ReadingStatus.READING)
    }

    @Test
    fun `already finished book keeps its status`() = runBlocking {
        settings.enabled = true
        givenBook(documentUri = DOCUMENT_URI, status = ReadingStatus.READ)
        reader.open = openBook(path = DOCUMENT_URI)
        progress.snapshot = snapshot(percent = 99f)

        sync().syncNow().getOrNull()

        // Явную отметку «прочитано» синхронизация не сбрасывает: человек мог перечитать главу.
        assertThat(database.bookDao().getById(BOOK_ID)?.readingStatus).isEqualTo(ReadingStatus.READ)
    }

    @Test
    fun `session is closed even when the reader fails`() = runBlocking {
        settings.enabled = true
        reader.open = openBook(path = DOCUMENT_URI)
        progress.failure = true

        sync().syncNow().getOrNull()

        // Незакрытая привязка держала бы чужой сервис поднятым — этого делать нельзя.
        assertThat(reader.disconnectCalls).isEqualTo(1)
    }

    private fun sync() = SyncReadingProgress(
        api = reader,
        progressSource = progress,
        books = repository,
        settings = settings,
    )

    private suspend fun givenBook(
        documentUri: String,
        status: ReadingStatus = ReadingStatus.UNREAD,
    ) {
        database.bookDao().insert(
            BookEntity(
                id = BOOK_ID,
                sourceId = "source-1",
                documentUri = documentUri,
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

    private suspend fun savedProgress() = database.readingProgressDao().getByBook(BOOK_ID)

    private fun openBook(path: String) = FbReaderOpenBook(path = path, title = "Книга", language = "ru")

    private fun snapshot(percent: Float) = ReadingProgressSnapshot(
        percent = percent,
        pageIndex = 4,
        locatorJson = null,
    )

    private class FakeFbReaderApi : FbReaderApi {

        var available: Boolean = true
        var open: FbReaderOpenBook? = null
        var connectCalls: Int = 0
        var disconnectCalls: Int = 0

        override suspend fun connect(): Boolean {
            connectCalls++
            return available
        }

        override suspend fun disconnect() {
            disconnectCalls++
        }

        override fun isAvailable(): Boolean = available

        override suspend fun currentBook(): FbReaderOpenBook? = open

        override suspend fun currentPosition(): FbReaderReadingPosition? =
            FbReaderReadingPosition(paragraphIndex = 4, elementIndex = 0, paragraphsNumber = 100)
    }

    private class FakeProgressSource : ReadingProgressSource {

        var snapshot: ReadingProgressSnapshot? = null
        var failure: Boolean = false

        override suspend fun progressFor(filePath: String): AppResult<ReadingProgressSnapshot> = when {
            failure -> AppResult.failure(AppError.Unknown(message = "читалка не ответила"))
            snapshot != null -> AppResult.success(snapshot!!)
            else -> AppResult.failure(AppError.NotFound(what = "прогресс"))
        }
    }

    private class FakeSettingsRepository : SettingsRepository {

        var enabled: Boolean = false

        override val settings: Flow<AppSettings> = flowOf(currentSnapshot())

        override suspend fun current(): AppSettings = currentSnapshot()

        override suspend fun update(transform: (AppSettings) -> AppSettings) = Unit

        override suspend fun resetToDefaults() = Unit

        private fun currentSnapshot() = AppSettings(
            reader = ReaderSettings(enableFbReaderProgress = enabled),
        )
    }

    /**
     * Заглушки «остальных» зависимостей репозитория.
     *
     * Свои интерфейсы не мокаются (ADR-012, правило 2), поэтому здесь маленькие фейки: сценарию
     * синхронизации нужны только `books` и `reading_progress`, а остальное существует, чтобы
     * собрать репозиторий.
     */
    private class FakeSourceRepository : SourceRepository {

        override fun observeSources(): Flow<List<Source>> = flowOf(emptyList())

        override suspend fun addSafSource(uri: String, displayName: String): AppResult<Source> =
            AppResult.failure(AppError.Unknown(message = "в этом тесте не используется"))

        override suspend fun removeSource(sourceId: String): AppResult<Unit> =
            AppResult.failure(AppError.Unknown(message = "в этом тесте не используется"))

        override suspend fun rescan(sourceId: String): AppResult<Unit> =
            AppResult.failure(AppError.Unknown(message = "в этом тесте не используется"))

        override suspend fun hasWriteAccess(sourceId: String): Boolean = false
    }

    private class FakeScanRepository : ScanRepository {

        override suspend fun startScan(sourceId: String): AppResult<Unit> =
            AppResult.failure(AppError.Unknown(message = "в этом тесте не используется"))

        override fun observeProgress(sourceId: String): Flow<ScanProgress> =
            flowOf(ScanProgress(sourceId = sourceId, status = ScanStatus.IDLE))

        override suspend fun pause(sourceId: String) = Unit

        override fun observeReports(): Flow<List<ScanReport>> = flowOf(emptyList())
    }

    private class FakeFileRemover : SafTreeWriter {

        override suspend fun write(
            treeUri: String,
            relativeDir: String,
            fileName: String,
            bytes: ByteArray,
            mimeType: String,
        ): AppResult<String> = AppResult.failure(AppError.Unknown(message = "в этом тесте не используется"))

        override suspend fun writeStream(
            treeUri: String,
            relativeDir: String,
            fileName: String,
            mimeType: String,
            onProgress: suspend (bytesWritten: Long, total: Long) -> Unit,
            block: suspend (emit: suspend (ByteArray) -> Unit) -> Unit,
        ): AppResult<String> = AppResult.failure(AppError.Unknown(message = "в этом тесте не используется"))

        override suspend fun delete(documentUri: String): AppResult<Unit> =
            AppResult.failure(AppError.Unknown(message = "в этом тесте не используется"))
    }

    /** Хеш содержимого поиску дублей не нужен: сценарий синхронизации его не вызывает. */
    private class FakeContentHasher : ContentHasher {
        override fun contentHash(documentUri: String): String? = null
    }

    private companion object {
        const val BOOK_ID = "book-1"
        const val DOCUMENT_URI =
            "content://com.android.externalstorage.documents/tree/primary%3ABooks/document/primary%3ABooks%2Fbook.fb2"

    }
}
