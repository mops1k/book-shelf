package io.github.bookshelf.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.common.error.errorOrNull
import io.github.bookshelf.core.common.error.getOrNull
import io.github.bookshelf.core.common.error.isFailure
import io.github.bookshelf.core.common.error.isSuccess
import io.github.bookshelf.core.common.testing.FakeAppClock
import io.github.bookshelf.core.data.model.SourceKind
import io.github.bookshelf.core.data.repository.impl.SourceRepositoryImpl
import io.github.bookshelf.core.data.sync.ScanScheduler
import io.github.bookshelf.core.database.BookshelfDatabase
import io.github.bookshelf.core.database.entity.BookEntity
import io.github.bookshelf.core.database.entity.SourceEntity
import io.github.bookshelf.core.database.model.MetadataSource
import io.github.bookshelf.core.database.model.ParseState
import io.github.bookshelf.core.database.model.ReadingStatus
import io.github.bookshelf.core.storage.saf.DocumentEntry
import io.github.bookshelf.core.storage.saf.SafTreeReader
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Источники библиотеки: persistable-разрешение, список и мягкое удаление.
 *
 * Проверяются свойства, потеря которых незаметна в happy path, но ломает библиотеку:
 * разрешение берётся до записи, а удаление источника сохраняет книги для возможного восстановления.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class SourceRepositoryTest {

    private lateinit var database: BookshelfDatabase
    private lateinit var scheduler: SourceRecordingScanScheduler
    private lateinit var permissionTaker: RecordingSafTreeReader
    private lateinit var clock: FakeAppClock
    private lateinit var repository: SourceRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BookshelfDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        scheduler = SourceRecordingScanScheduler()
        permissionTaker = RecordingSafTreeReader()
        clock = FakeAppClock()
        repository = SourceRepositoryImpl(
            sourceDao = database.sourceDao(),
            bookDao = database.bookDao(),
            safTreeReader = permissionTaker,
            scanScheduler = scheduler,
            appClock = clock,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun addSafSource_takes_permission_before_writing_and_returns_source() = runBlocking {
        val result = repository.addSafSource(TREE_URI, DISPLAY_NAME)

        val source = result.getOrNull()
        assertThat(result.isSuccess).isTrue()
        assertThat(permissionTaker.requestedUris).isEqualTo(listOf(TREE_URI))
        assertThat(source?.uri).isEqualTo(TREE_URI)
        assertThat(source?.displayName).isEqualTo(DISPLAY_NAME)
        assertThat(source?.kind).isEqualTo(SourceKind.SAF_TREE)
        assertThat(source?.documentCount).isEqualTo(0)
        val sourceId = requireNotNull(source?.id)
        assertThat(UUID.fromString(sourceId).toString()).isEqualTo(sourceId)
        assertThat(database.sourceDao().getById(sourceId)).isNotNull()
        assertThat(scheduler.enqueuedSourceIds).isEqualTo(listOf(sourceId))
    }

    @Test
    fun addSafSource_permission_denied_does_not_write_source_or_schedule_scan() = runBlocking {
        permissionTaker.permissionResult = AppResult.failure(
            AppError.PermissionDenied(uri = TREE_URI, message = "Разрешение не выдано"),
        )

        val result = repository.addSafSource(TREE_URI, DISPLAY_NAME)

        assertThat(result.isFailure).isTrue()
        assertThat(result.errorOrNull()).isInstanceOf(AppError.PermissionDenied::class.java)
        assertThat(database.sourceDao().countAll()).isEqualTo(0)
        assertThat(scheduler.enqueuedSourceIds).isEmpty()
    }

    @Test
    fun observeSources_emits_added_source_with_live_book_count() = runBlocking {
        val added = repository.addSafSource(TREE_URI, DISPLAY_NAME).getOrNull()
        database.bookDao().insert(book(sourceId = requireNotNull(added).id))

        val sources = repository.observeSources().first()

        assertThat(sources.map { it.id }).isEqualTo(listOf(added?.id))
        assertThat(sources.single().documentCount).isEqualTo(1)
    }

    @Test
    fun removeSource_marks_books_missing_but_keeps_them() = runBlocking {
        givenSourceWithBook()

        val result = repository.removeSource(SOURCE_ID)

        val storedBook = database.bookDao().getById(BOOK_ID)
        assertThat(result.isSuccess).isTrue()
        assertThat(database.sourceDao().getById(SOURCE_ID)).isNull()
        assertThat(storedBook).isNotNull()
        assertThat(storedBook?.isMissing).isTrue()
        assertThat(storedBook?.missingSince).isEqualTo(clock.nowMillis())
        assertThat(storedBook?.title).isEqualTo(BOOK_TITLE)
        assertThat(scheduler.cancelledSourceIds).isEqualTo(listOf(SOURCE_ID))
    }

    @Test
    fun removeSource_unknown_source_returns_not_found_without_books() = runBlocking {
        val result = repository.removeSource("нет-такого-источника")

        assertThat(result.isFailure).isTrue()
        assertThat(result.errorOrNull()).isInstanceOf(AppError.NotFound::class.java)
    }

    @Test
    fun rescan_uses_the_same_unique_scheduler_call() = runBlocking {
        givenSourceWithBook()

        val result = repository.rescan(SOURCE_ID)

        assertThat(result.isSuccess).isTrue()
        assertThat(scheduler.enqueuedSourceIds).isEqualTo(listOf(SOURCE_ID))
    }

    private suspend fun givenSourceWithBook() {
        database.sourceDao().insert(source())
        database.bookDao().insert(book(sourceId = SOURCE_ID))
    }

    private fun source() = SourceEntity(
        id = SOURCE_ID,
        uri = TREE_URI,
        displayName = DISPLAY_NAME,
        kind = io.github.bookshelf.core.database.model.SourceKind.SAF_TREE,
    )

    @Suppress("LongParameterList")
    private fun book(sourceId: String) = BookEntity(
        id = BOOK_ID,
        sourceId = sourceId,
        documentUri = "content://books/document/$BOOK_ID",
        relativePath = "Авторы/$BOOK_TITLE.fb2",
        fileName = "$BOOK_TITLE.fb2",
        extension = "fb2",
        mimeType = "application/x-fictionbook+xml",
        sizeBytes = 4_096L,
        lastModified = 1_000L,
        quickHash = "quick-hash",
        contentHash = "content-hash",
        title = BOOK_TITLE,
        sortTitle = BOOK_TITLE,
        authorsText = "Иван Петров",
        seriesName = "Классика",
        seriesIndex = 1.0,
        language = "ru",
        publishedYear = 2020,
        publisher = "Издательство",
        description = "Описание",
        isbn = "978-0-000-00000-0",
        pageCount = 320,
        coverPath = "/cache/covers/$BOOK_ID.webp",
        coverUpdatedAt = 1_100L,
        metadataSource = MetadataSource.EMBEDDED,
        parseState = ParseState.OK,
        parseError = null,
        addedAt = 1_200L,
        updatedAt = 1_300L,
        lastSeenAt = 1_400L,
        lastOpenedAt = 1_500L,
        openCount = 2,
        readingStatus = ReadingStatus.READING,
        favorite = true,
        rating = 5,
        isMissing = false,
        missingSince = null,
    )

    private companion object {
        const val SOURCE_ID = "source-1"
        const val BOOK_ID = "book-1"
        const val BOOK_TITLE = "Книга для проверки"
        const val TREE_URI = "content://com.android.externalstorage.documents/tree/books"
        const val DISPLAY_NAME = "Книги"
    }
}

/** Планировщик без Android: тесты проверяют только передачу `sourceId`, политику KEEP проверит :app. */
private class SourceRecordingScanScheduler : ScanScheduler {

    val enqueuedSourceIds = mutableListOf<String>()
    val cancelledSourceIds = mutableListOf<String>()

    override suspend fun enqueueScan(sourceId: String) {
        enqueuedSourceIds += sourceId
    }

    override suspend fun cancelScan(sourceId: String) {
        cancelledSourceIds += sourceId
    }
}

/** Подставной SAF-порт: отдельно отслеживает, что разрешение запрошено до появления строки БД. */
private class RecordingSafTreeReader : SafTreeReader {

    val requestedUris = mutableListOf<String>()
    var permissionResult: AppResult<Unit> = AppResult.success(Unit)

    override suspend fun children(treeUri: String, parentDocumentId: String): AppResult<List<DocumentEntry>> =
        AppResult.failure(AppError.Unsupported("children не вызывается в тестах репозитория"))

    override suspend fun takePersistablePermission(treeUri: String): AppResult<Unit> {
        requestedUris += treeUri
        return permissionResult
    }

    override fun hasPersistedPermission(treeUri: String): Boolean = true

    override fun documentUri(treeUri: String, documentId: String): String =
        "$treeUri/document/$documentId"
}
