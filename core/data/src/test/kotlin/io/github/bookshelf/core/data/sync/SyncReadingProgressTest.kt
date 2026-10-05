package io.github.bookshelf.core.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.common.error.getOrNull
import io.github.bookshelf.core.common.testing.TestAppDispatchers
import io.github.bookshelf.core.common.testing.FakeAppClock
import io.github.bookshelf.core.common.testing.RecordingAppLogger
import io.github.bookshelf.core.data.duplicates.DuplicateFinder
import io.github.bookshelf.core.fb2opt.DefaultFb2Optimizer
import io.github.bookshelf.core.fb2opt.Fb2ImageOptimizer
import io.github.bookshelf.core.fb2opt.JpegEntropyOptimizer
import io.github.bookshelf.core.testing.fake.FakeBookContentOpener
import io.github.bookshelf.core.testing.fake.FakeReaderSession
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
import io.github.bookshelf.core.database.entity.BookCopyEntity
import io.github.bookshelf.core.database.entity.BookEntity
import io.github.bookshelf.core.database.model.ReadingStatus
import io.github.bookshelf.core.datastore.SettingsRepository
import io.github.bookshelf.core.datastore.model.AppSettings
import io.github.bookshelf.core.datastore.model.ReaderSettings
import io.github.bookshelf.core.readerlink.progress.ProgressBookRef
import io.github.bookshelf.core.readerlink.progress.ProgressSourceId
import io.github.bookshelf.core.readerlink.koreader.KoReaderPreset
import io.github.bookshelf.core.readerlink.progress.ReaderBookAddress
import io.github.bookshelf.core.readerlink.progress.ReadingProgressSnapshot
import io.github.bookshelf.core.readerlink.progress.ReadingProgressSource
import io.github.bookshelf.core.readerlink.progress.ReadingProgressSources
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
 * Синхронизация прогресса из читалок (`SyncReadingProgress`).
 *
 * Проверяется порядок действий и границы, где ошибка тихая: если спросить читалку при выключенной
 * настройке — мы читаем чужие данные без спроса; если записать процент чужой книги или книги,
 * которой нет в библиотеке, — на полке появится прогресс, которого не было.
 *
 * Источников теперь несколько (FBReader через AIDL, KOReader через sidecar), поэтому отдельно
 * проверяется правило нагрузки: пока источник выключен, его не спрашивают вовсе, а молчание одного
 * источника не мешает прочитать прогресс из другого.
 *
 * Хранилище здесь настоящее (Room в памяти): проверяются и поиск книги по адресу документа, и сама
 * запись в `reading_progress` — в том виде, в каком её потом читает полка.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class SyncReadingProgressTest {

    private lateinit var database: BookshelfDatabase
    private lateinit var repository: BookRepository
    private val settings = FakeSettingsRepository()
    private val session = FakeReaderSession()

    /** Источник FBReader: умеет сам сказать, что открыто сейчас. */
    private val fbReader = FakeProgressSource(id = ProgressSourceId.FBREADER_AIDL, knowsOpenBook = true)

    /** Источник KOReader: файловый, «что открыто сейчас» он не знает и читается по книге. */
    private val koReader = FakeProgressSource(id = ProgressSourceId.KOREADER_SIDECAR, knowsOpenBook = false)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BookshelfDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = BookRepositoryImpl(
            dispatchers = TestAppDispatchers(),
            bookDao = database.bookDao(),
            overrideDao = database.metadataOverrideDao(),
            readingProgressDao = database.readingProgressDao(),
            sourceRepository = FakeSourceRepository(),
            scanRepository = FakeScanRepository(),
            readerSession = FakeReaderSession(),
            duplicateFinder = DuplicateFinder(FakeContentHasher()),
            fileRemover = FakeFileRemover(),
            clock = FakeAppClock().apply { setTo(1_000L) },
            authorWriter = BookAuthorWriter(database.authorDao()),
            bookCleanupDao = database.bookCleanupDao(),
            bookCopyDao = database.bookCopyDao(),
            contentOpener = FakeBookContentOpener(),
            fb2Optimizer = DefaultFb2Optimizer(Fb2ImageOptimizer(JpegEntropyOptimizer())),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `disabled setting never touches the reader`() = runBlocking {
        settings.fbReaderEnabled = false

        val outcome = sync().syncNow().getOrNull()

        assertThat(outcome).isEqualTo(ProgressSyncOutcome.Disabled)
        // Читалку не будили вовсе: при выключенной настройке обращаться к чужому сервису нельзя.
        assertThat(fbReader.connectCalls).isEqualTo(0)
    }

    @Test
    fun `a source that is switched off is not asked even when another one is on`() = runBlocking {
        settings.fbReaderEnabled = true
        settings.koReaderEnabled = false
        givenBook(documentUri = DOCUMENT_URI)
        fbReader.address = ReaderBookAddress(documentUri = DOCUMENT_URI)
        fbReader.snapshot = snapshot(percent = 25f)

        sync().syncNow().getOrNull()

        // Выключенный KOReader не читаем: это чужой файл на карте, и открывать его без разрешения
        // пользователя нельзя.
        assertThat(koReader.connectCalls).isEqualTo(0)
    }

    @Test
    fun `unavailable reader is reported and nothing is written`() = runBlocking {
        settings.fbReaderEnabled = true
        fbReader.available = false
        givenBook(documentUri = DOCUMENT_URI)

        val outcome = sync().syncNow().getOrNull()

        assertThat(outcome).isEqualTo(ProgressSyncOutcome.ReaderUnavailable)
        assertThat(savedProgress()).isNull()
    }

    @Test
    fun `no book open is reported`() = runBlocking {
        settings.fbReaderEnabled = true
        fbReader.address = null

        val outcome = sync().syncNow().getOrNull()

        assertThat(outcome).isEqualTo(ProgressSyncOutcome.NoBookOpen)
        assertThat(savedProgress()).isNull()
    }

    @Test
    fun `silent source does not stop the other one from reporting progress`() = runBlocking {
        settings.fbReaderEnabled = true
        settings.koReaderEnabled = true
        givenBook(documentUri = DOCUMENT_URI, status = ReadingStatus.READING)
        // FBReader молчит (ничего не открыто), а KOReader — файловый источник: он читается по
        // книге, выбранной для чтения, и его прогресс должен дойти до полки.
        fbReader.address = null
        koReader.snapshot = snapshot(percent = 12f)

        val outcome = sync().syncNow().getOrNull()

        assertThat(outcome).isEqualTo(ProgressSyncOutcome.Saved(bookId = BOOK_ID, percentPercent = 12f))
        assertThat(savedProgress()?.source).isEqualTo(ReadingProgressOrigins.KOREADER_SIDECAR)
    }

    @Test
    fun `a file source reads the books selected for reading`() = runBlocking {
        settings.koReaderEnabled = true
        givenBook(documentUri = DOCUMENT_URI, status = ReadingStatus.READING)
        koReader.snapshot = snapshot(percent = 30f)

        val outcome = sync().syncNow().getOrNull()

        assertThat(outcome).isEqualTo(ProgressSyncOutcome.Saved(bookId = BOOK_ID, percentPercent = 30f))
        assertThat(koReader.askedRefs.single().bookId).isEqualTo(BOOK_ID)
        assertThat(savedProgress()?.source).isEqualTo(ReadingProgressOrigins.KOREADER_SIDECAR)
    }

    @Test
    fun `books that are not selected for reading are not asked at all`() = runBlocking {
        settings.koReaderEnabled = true
        // Книга не выбрана для чтения: полный проход по всей библиотеке на слабом устройстве
        // подвешивал бы его (требование Александра, 05.10.2026).
        givenBook(documentUri = DOCUMENT_URI, status = ReadingStatus.UNREAD)
        koReader.snapshot = snapshot(percent = 30f)

        val outcome = sync().syncNow().getOrNull()

        assertThat(outcome).isEqualTo(ProgressSyncOutcome.NoBookOpen)
        assertThat(koReader.askedRefs).isEmpty()
        assertThat(savedProgress()).isNull()
    }

    @Test
    fun `while reading only the open reader is asked`() = runBlocking {
        settings.fbReaderEnabled = true
        settings.koReaderEnabled = true
        givenBook(documentUri = DOCUMENT_URI, status = ReadingStatus.READING)
        // Сессия отмечает книгу и читалку так же, как это делает запуск из полки.
        session.markOpened(BOOK_ID, readerProfileId = KoReaderPreset.ID)
        koReader.snapshot = snapshot(percent = 15f)

        val outcome = sync().syncNow().getOrNull()

        assertThat(outcome).isEqualTo(ProgressSyncOutcome.Saved(bookId = BOOK_ID, percentPercent = 15f))
        // Пока человек читает в KOReader, состояние FBReader не разбираем вовсе.
        assertThat(fbReader.connectCalls).isEqualTo(0)
    }

    @Test
    fun `reading in a reader we have no source for asks nobody`() = runBlocking {
        settings.fbReaderEnabled = true
        givenBook(documentUri = DOCUMENT_URI, status = ReadingStatus.READING)
        // Moon+ Reader: прогресса у нас для него нет. Спросить «всех остальных» нельзя — это и есть
        // нагрузка, от которой отказались.
        session.markOpened(BOOK_ID, readerProfileId = "moonreader")

        val outcome = sync().syncNow().getOrNull()

        assertThat(outcome).isEqualTo(ProgressSyncOutcome.ReaderUnavailable)
        assertThat(fbReader.connectCalls).isEqualTo(0)
    }

    @Test
    fun `a reader that knows the open book is not walked over every selected book`() = runBlocking {
        settings.fbReaderEnabled = true
        givenBook(documentUri = DOCUMENT_URI, status = ReadingStatus.READING)
        fbReader.address = ReaderBookAddress(documentUri = DOCUMENT_URI)
        fbReader.snapshot = snapshot(percent = 25f)

        sync().syncNow().getOrNull()

        // AIDL-читалка сама называет открытую книгу: перебирать книги и дёргать сервис по каждой
        // нельзя — это лишние привязки к чужому сервису.
        assertThat(fbReader.askedRefs).hasSize(1)
        assertThat(fbReader.connectCalls).isEqualTo(1)
    }

    @Test
    fun `book opened outside our library is not stored`() = runBlocking {
        settings.fbReaderEnabled = true
        fbReader.address = ReaderBookAddress(documentUri = "content://tree/books/document/чужая.fb2")
        fbReader.snapshot = snapshot(percent = 42f)

        val outcome = sync().syncNow().getOrNull()

        // Книгу открыли в самой читалке, у нас её нет: записывать некуда и нечего.
        assertThat(outcome).isEqualTo(ProgressSyncOutcome.BookUnknown)
        assertThat(savedProgress()).isNull()
    }

    @Test
    fun `progress of a copy is stored for the original book`() = runBlocking {
        settings.fbReaderEnabled = true
        givenBook(documentUri = DOCUMENT_URI)
        // Так выглядит копия, которую создала читалка: файл внутри нашей библиотеки, названный по
        // названию книги. Прогресс, записанный на копию, на полке был бы виден у копии, а книга
        // пользователя оставалась бы без прогресса (дефект 01.10.2026).
        database.bookCopyDao().upsert(
            BookCopyEntity(
                copyDocumentUri = COPY_URI,
                bookId = BOOK_ID,
                sourceId = "source-1",
                sizeBytes = 1_000L,
                lastModified = 1_000L,
                contentHash = "hash-1",
            ),
        )
        fbReader.address = ReaderBookAddress(documentUri = COPY_URI)
        fbReader.snapshot = snapshot(percent = 25f)

        val outcome = sync().syncNow().getOrNull()

        assertThat(outcome).isEqualTo(ProgressSyncOutcome.Saved(bookId = BOOK_ID, percentPercent = 25f))
        assertThat(savedProgress()?.percent).isWithin(0.0001f).of(0.25f)
    }

    @Test
    fun `progress of a book inside an archive is stored too`() = runBlocking {
        settings.fbReaderEnabled = true
        // Настоящий адрес из журнала устройства: читалка называет книгу в архиве через zip-entry,
        // и раньше прогресс по таким книгам терялся (в журнале было BookUnknown).
        givenBook(documentUri = fixture("document-uri.txt"))
        fbReader.address = ReaderBookAddress(documentUri = fixture("zip-entry-path.txt"))
        fbReader.snapshot = snapshot(percent = 42f)

        val outcome = sync().syncNow().getOrNull()

        assertThat(outcome).isEqualTo(ProgressSyncOutcome.Saved(bookId = BOOK_ID, percentPercent = 42f))
        assertThat(savedProgress()?.percent).isWithin(0.0001f).of(0.42f)
    }

    @Test
    fun `reader that cannot give progress leaves the library untouched`() = runBlocking {
        settings.fbReaderEnabled = true
        givenBook(documentUri = DOCUMENT_URI)
        fbReader.address = ReaderBookAddress(documentUri = DOCUMENT_URI)
        fbReader.snapshot = null

        val outcome = sync().syncNow().getOrNull()

        assertThat(outcome).isEqualTo(ProgressSyncOutcome.NoBookOpen)
        assertThat(savedProgress()).isNull()
    }

    @Test
    fun `progress of the open book is stored as a share with its origin`() = runBlocking {
        settings.fbReaderEnabled = true
        givenBook(documentUri = DOCUMENT_URI)
        fbReader.address = ReaderBookAddress(documentUri = DOCUMENT_URI)
        fbReader.snapshot = snapshot(percent = 25f)

        val outcome = sync().syncNow().getOrNull()

        assertThat(outcome).isEqualTo(ProgressSyncOutcome.Saved(bookId = BOOK_ID, percentPercent = 25f))
        val stored = savedProgress()
        // В таблице хранится доля, а не проценты: полка читает именно её.
        assertThat(stored?.percent).isWithin(0.0001f).of(0.25f)
        assertThat(stored?.source).isEqualTo(ReadingProgressOrigins.FBREADER_AIDL)
        assertThat(stored?.updatedAt).isEqualTo(1_000L)
    }

    @Test
    fun `source is asked about our book, not about the address the reader named`() = runBlocking {
        settings.fbReaderEnabled = true
        givenBook(documentUri = DOCUMENT_URI)
        // Читалка читает свою копию: книга опознаётся через связь копии, а спросить её надо про
        // копию — по ней читалка и знает позицию.
        database.bookCopyDao().upsert(
            BookCopyEntity(
                copyDocumentUri = COPY_URI,
                bookId = BOOK_ID,
                sourceId = "source-1",
                sizeBytes = 1_000L,
                lastModified = 1_000L,
                contentHash = "hash-1",
            ),
        )
        fbReader.address = ReaderBookAddress(documentUri = COPY_URI)
        fbReader.snapshot = snapshot(percent = 25f)

        sync().syncNow().getOrNull()

        // Источник получает нашу книгу (и её id, и адрес читалки): адрес читалки (копия, архив)
        // остаётся её собственным ключом, а прогресс записывается нашей книге.
        assertThat(fbReader.askedRefs.single().bookId).isEqualTo(BOOK_ID)
        assertThat(fbReader.askedRefs.single().readerAddress).isEqualTo(COPY_URI)
    }

    @Test
    fun `book open in the reader is marked as reading`() = runBlocking {
        settings.fbReaderEnabled = true
        givenBook(documentUri = DOCUMENT_URI)
        fbReader.address = ReaderBookAddress(documentUri = DOCUMENT_URI)
        fbReader.snapshot = snapshot(percent = 25f)

        sync().syncNow().getOrNull()

        // Полка показывает процент только у книги со статусом «читаю»: без этой отметки
        // синхронизация была бы невидимой, хотя данные записаны.
        assertThat(database.bookDao().getById(BOOK_ID)?.readingStatus).isEqualTo(ReadingStatus.READING)
    }

    @Test
    fun `already finished book keeps its status`() = runBlocking {
        settings.fbReaderEnabled = true
        givenBook(documentUri = DOCUMENT_URI, status = ReadingStatus.READ)
        fbReader.address = ReaderBookAddress(documentUri = DOCUMENT_URI)
        fbReader.snapshot = snapshot(percent = 99f)

        sync().syncNow().getOrNull()

        // Явную отметку «прочитано» синхронизация не сбрасывает: человек мог перечитать главу.
        assertThat(database.bookDao().getById(BOOK_ID)?.readingStatus).isEqualTo(ReadingStatus.READ)
    }

    @Test
    fun `session is closed even when the reader fails`() = runBlocking {
        settings.fbReaderEnabled = true
        fbReader.address = ReaderBookAddress(documentUri = DOCUMENT_URI)
        fbReader.failure = true

        sync().syncNow().getOrNull()

        // Незакрытая привязка держала бы чужой сервис поднятым — этого делать нельзя.
        assertThat(fbReader.disconnectCalls).isEqualTo(1)
    }

    private fun sync() = SyncReadingProgress(
        sources = ReadingProgressSources(setOf(fbReader, koReader)),
        books = repository,
        settings = settings,
        session = session,
        copies = database.bookCopyDao(),
        logger = RecordingAppLogger(),
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

    /**
     * Настоящие строки с устройства (Bigme HiBreak, 30.09.2026): путь, которым читалка назвала книгу
     * в архиве, и наш `books.documentUri` для той же книги. Держим их файлами, а не константами в
     * коде: они длиннее допустимой длины строки, а подделывать их нельзя — на них и держится фикс.
     */
    private fun fixture(name: String): String = checkNotNull(
        javaClass.getResourceAsStream("/reader-paths/$name"),
    ) { "нет фикстуры $name" }.bufferedReader().use { reader -> reader.readText() }.trim()

    private suspend fun savedProgress() = database.readingProgressDao().getByBook(BOOK_ID)

    private fun snapshot(percent: Float) = ReadingProgressSnapshot(
        percent = percent,
        pageIndex = 4,
        locatorJson = null,
    )

    /**
     * Источник прогресса-заглушка.
     *
     * Свои интерфейсы не мокаются (ADR-012, правило 2), поэтому здесь маленький фейк: он помнит,
     * сколько раз его включали, что он называет открытым и какой книгой его спросили.
     */
    private class FakeProgressSource(
        override val id: ProgressSourceId,
        override val knowsOpenBook: Boolean,
    ) : ReadingProgressSource {

        var available: Boolean = true
        var address: ReaderBookAddress? = null
        var snapshot: ReadingProgressSnapshot? = null
        var failure: Boolean = false
        var connectCalls: Int = 0
        var disconnectCalls: Int = 0
        val askedRefs: MutableList<ProgressBookRef> = mutableListOf()

        override suspend fun connect(): Boolean {
            connectCalls++
            return available
        }

        override suspend fun disconnect() {
            disconnectCalls++
        }

        override suspend fun openedBook(): ReaderBookAddress? = address

        override suspend fun progressFor(book: ProgressBookRef): AppResult<ReadingProgressSnapshot> {
            askedRefs += book
            return when {
                failure -> AppResult.failure(AppError.Unknown(message = "читалка не ответила"))
                snapshot != null -> AppResult.success(snapshot!!)
                else -> AppResult.failure(AppError.NotFound(what = "прогресс"))
            }
        }
    }

    private class FakeSettingsRepository : SettingsRepository {

        var fbReaderEnabled: Boolean = false
        var koReaderEnabled: Boolean = false

        override val settings: Flow<AppSettings> = flowOf(currentSnapshot())

        override suspend fun current(): AppSettings = currentSnapshot()

        override suspend fun update(transform: (AppSettings) -> AppSettings) = Unit

        override suspend fun resetToDefaults() = Unit

        private fun currentSnapshot() = AppSettings(
            reader = ReaderSettings(
                enableFbReaderProgress = fbReaderEnabled,
                enableKoReaderProgress = koReaderEnabled,
            ),
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

        override suspend fun addDirectPathSource(path: String, displayName: String): AppResult<Source> =
            AppResult.failure(AppError.Unknown(message = "в этом тесте не используется"))

        override suspend fun addGrantedSource(uri: String, displayName: String): AppResult<Source> =
            AppResult.failure(AppError.Unknown(message = "в этом тесте не используется"))

        override suspend fun removeSource(sourceId: String): AppResult<Unit> =
            AppResult.failure(AppError.Unknown(message = "в этом тесте не используется"))

        override suspend fun rescan(sourceId: String): AppResult<Unit> =
            AppResult.failure(AppError.Unknown(message = "в этом тесте не используется"))

        override suspend fun hasWriteAccess(sourceId: String): Boolean = false
    }

    private class FakeScanRepository : ScanRepository {

        override suspend fun startScan(sourceId: String, restart: Boolean): AppResult<Unit> =
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

        /** Адрес копии, созданной читалкой: папка-дата и имя по названию книги. */
        const val COPY_URI =
            "content://com.android.externalstorage.documents/tree/primary%3ABooks/document/" +
                "primary%3ABooks%2F2026-09-30%2Fbook.fb2.zip"

    }
}
