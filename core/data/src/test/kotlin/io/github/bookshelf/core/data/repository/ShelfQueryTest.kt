package io.github.bookshelf.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.coroutine.AppDispatchers
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.common.testing.FakeAppClock
import io.github.bookshelf.core.common.testing.TestAppDispatchers
import io.github.bookshelf.core.data.author.BookAuthorWriter
import io.github.bookshelf.core.data.duplicates.DuplicateFinder
import io.github.bookshelf.core.data.model.LibraryQuery
import io.github.bookshelf.core.data.repository.impl.BookRepositoryImpl
import io.github.bookshelf.core.database.BookshelfDatabase
import io.github.bookshelf.core.database.dao.ReadingProgressDao
import io.github.bookshelf.core.database.entity.BookEntity
import io.github.bookshelf.core.database.entity.MetadataOverrideEntity
import io.github.bookshelf.core.database.entity.ReadingProgressEntity
import io.github.bookshelf.core.database.entity.SourceEntity
import io.github.bookshelf.core.database.model.SourceKind
import io.github.bookshelf.core.fb2opt.DefaultFb2Optimizer
import io.github.bookshelf.core.fb2opt.Fb2ImageOptimizer
import io.github.bookshelf.core.fb2opt.JpegEntropyOptimizer
import io.github.bookshelf.core.storage.files.ContentHasher
import io.github.bookshelf.core.storage.saf.SafTreeWriter
import io.github.bookshelf.core.testing.fake.FakeBookContentOpener
import io.github.bookshelf.core.testing.fake.FakeReaderSession
import io.github.bookshelf.core.testing.fake.FakeScanRepository
import io.github.bookshelf.core.testing.fake.FakeSourceRepository
import java.util.Collections
import java.util.concurrent.Executor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Выдача страницы полки: сколько обращений к базе она стоит и на каком потоке собирается.
 *
 * Дефект, ради которого написан этот тест (05.10.2026): полка просила 500 книг одним запросом, а
 * на каждую книгу делала ещё два — прогресс чтения и ручную правку. На выдаче в 500 книг это 1001
 * запрос на **каждое** обновление таблицы `books`, то есть на каждую транзакцию скана; всё, что
 * дальше пятисотой книги, на полку не попадало вовсе.
 *
 * Проверяется ровно это: число запросов не зависит ни от библиотеки, ни от размера окна, а
 * сопоставление строк в домен идёт не на вызывающем потоке (у экрана он главный).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class ShelfQueryTest {

    private lateinit var database: BookshelfDatabase

    /**
     * SQL, дошедший до базы за время теста.
     *
     * Список синхронизирован: Room зовёт колбэк со своих потоков чтения. В него попадают и
     * служебные обращения Room (журнал изменений, временные триггеры) — считаем только свои
     * запросы, см. [appQueryCount].
     */
    private val queries: MutableList<String> = Collections.synchronizedList(mutableListOf())

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BookshelfDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryCallback(
                RoomDatabase.QueryCallback { sql, _ -> queries += sql },
                Executor { command -> command.run() },
            )
            .build()
        runBlocking {
            database.sourceDao().insert(
                SourceEntity(
                    id = SOURCE_ID,
                    uri = "content://com.android.externalstorage.documents/tree/books",
                    displayName = "Books",
                    kind = SourceKind.SAF_TREE,
                ),
            )
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun shelf_page_reads_progress_and_overrides_in_batches() = runBlocking {
        // Сорок книг вместо пяти: на пяти разница между «по книге» и «пачкой» незаметна, а на
        // настоящей библиотеке именно она превращается в тысячи обращений к базе на каждую выдачу.
        givenBooks(count = 40)
        givenProgress("book-0", percent = 0.5f)
        givenOverride("book-0", title = "Правленое название")
        queries.clear()

        val page = repository().observePage(LibraryQuery(page = 0, pageSize = 40)).first()

        assertThat(page.items).hasSize(40)
        // Значения по-прежнему применяются к каждой книге: пачка — это про число запросов,
        // а не про «прогресс теперь не читается».
        val first = page.items.single { book -> book.id == "book-0" }
        assertThat(first.progressPercent).isWithin(0.01f).of(50f)
        assertThat(first.title).isEqualTo("Правленое название")

        assertThat(queries.count { sql -> sql.contains("FROM reading_progress") }).isEqualTo(1)
        assertThat(queries.count { sql -> sql.contains("FROM metadata_overrides") }).isEqualTo(1)
    }

    @Test
    fun shelf_reaches_books_beyond_five_hundred_with_a_flat_number_of_queries() = runBlocking {
        // Библиотека больше прежнего потолка в 500 книг: раньше всё, что дальше пятисотой, на полку
        // не попадало вовсе. Теперь окно растёт, и до последней книги можно долистать.
        givenBooks(count = 600)
        queries.clear()

        val first = repository().observePage(LibraryQuery(page = 0, pageSize = 50)).first()
        val firstWindowQueries = appQueryCount()

        assertThat(first.items).hasSize(50)
        assertThat(first.totalCount).isEqualTo(600)
        // Выборка, счётчик, прогресс пачкой, правки пачкой.
        assertThat(firstWindowQueries).isEqualTo(4)

        // Долистываем окнами, как это делает полка: последняя книга должна стать доступной.
        var loaded = first.items.size
        var window = 50
        while (loaded < 600) {
            window += 50
            loaded = repository().observePage(LibraryQuery(page = 0, pageSize = window)).first().items.size
        }
        assertThat(loaded).isEqualTo(600)

        // Самая большая выдача — та, что открывает всю библиотеку целиком.
        val beforeBig = appQueryCount()
        val big = repository().observePage(LibraryQuery(page = 0, pageSize = 600)).first()
        val bigWindowQueries = appQueryCount() - beforeBig

        // Число запросов приложения не растёт ни с библиотекой, ни с окном: это и есть пачка.
        assertThat(big.items).hasSize(600)
        assertThat(bigWindowQueries).isEqualTo(firstWindowQueries)
    }

    @Test
    fun shelf_page_is_assembled_off_the_calling_thread() = runBlocking {
        givenBooks(count = 5)
        val caller = Thread.currentThread().name
        val threads = Collections.synchronizedList(mutableListOf<String>())

        repository(
            progressDao = RecordingProgressDao(database.readingProgressDao(), threads),
            dispatchers = BackgroundDispatchers,
        ).observePage(LibraryQuery(page = 0, pageSize = 5)).first()

        // Полка собирается в `viewModelScope`, то есть на главном потоке: сборка там означает
        // ровно то же замирание интерфейса, из-за которого 30.09.2026 случился ANR карточки.
        assertThat(threads).isNotEmpty()
        assertThat(threads).doesNotContain(caller)
    }

    /** Репозиторий поверх текущей базы: параметры нужны тестам про потоки и диспетчеры. */
    private fun repository(
        progressDao: ReadingProgressDao = database.readingProgressDao(),
        dispatchers: AppDispatchers = TestAppDispatchers(),
    ) = BookRepositoryImpl(
        dispatchers = dispatchers,
        bookDao = database.bookDao(),
        overrideDao = database.metadataOverrideDao(),
        readingProgressDao = progressDao,
        sourceRepository = FakeSourceRepository(),
        scanRepository = FakeScanRepository(),
        readerSession = FakeReaderSession(),
        duplicateFinder = DuplicateFinder(NoContentHashes),
        fileRemover = FakeFileRemover(),
        clock = FakeAppClock(),
        authorWriter = BookAuthorWriter(database.authorDao()),
        bookCleanupDao = database.bookCleanupDao(),
        bookCopyDao = database.bookCopyDao(),
        contentOpener = FakeBookContentOpener(),
        fb2Optimizer = DefaultFb2Optimizer(Fb2ImageOptimizer(JpegEntropyOptimizer())),
    )

    /** Число запросов приложения: служебные обращения Room (журнал, триггеры) не считаем. */
    private fun appQueryCount(): Int = queries.count { sql ->
        APP_QUERIES.any { table -> sql.contains(table) }
    }

    private suspend fun givenProgress(bookId: String, percent: Float) {
        database.readingProgressDao().upsert(
            ReadingProgressEntity(
                bookId = bookId,
                percent = percent,
                updatedAt = 2_000L,
                source = "FBREADER_AIDL",
            ),
        )
    }

    /** Ручная правка книги: так же, как её пишет экран правки метаданных. */
    private suspend fun givenOverride(bookId: String, title: String) {
        database.metadataOverrideDao().upsert(
            MetadataOverrideEntity(bookId = bookId, title = title, updatedAt = 3_000L),
        )
    }

    /**
     * Библиотека из [count] книг с различимыми названиями.
     *
     * Нужна там, где важно число строк в выдаче: на пяти книгах «запрос на книгу» и «пачка»
     * стоят одинаково дешево, и дефект остаётся невидимым.
     */
    private suspend fun givenBooks(count: Int) {
        database.bookDao().upsertAll(
            (0 until count).map { index ->
                BookEntity(
                    id = "book-$index",
                    sourceId = SOURCE_ID,
                    documentUri = "content://tree/books/document/book-$index",
                    relativePath = "book-$index.fb2",
                    fileName = "book-$index.fb2",
                    extension = "fb2",
                    mimeType = "application/fb2",
                    sizeBytes = 1_000L,
                    lastModified = 1_000L,
                    title = "Книга ${index.toString().padStart(4, '0')}",
                    sortTitle = "Книга ${index.toString().padStart(4, '0')}",
                    authorsText = "Автор",
                    addedAt = 1_000L,
                    updatedAt = 1_000L,
                    lastSeenAt = 1_000L,
                )
            },
        )
    }

    /**
     * Прогресс чтения, записывающий имена потоков, с которых его спросили.
     *
     * Только так видно, где на самом деле собирается страница: `Flow` DAO уходит в свой диспетчер
     * в любом случае, а вот **место вызова** — это тот поток, на котором идёт сопоставление.
     */
    private class RecordingProgressDao(
        private val delegate: ReadingProgressDao,
        private val threads: MutableList<String>,
    ) : ReadingProgressDao {

        override suspend fun upsert(progress: ReadingProgressEntity) = delegate.upsert(progress)

        override fun observeByBook(bookId: String): Flow<ReadingProgressEntity?> =
            delegate.observeByBook(bookId)

        override suspend fun getByBook(bookId: String): ReadingProgressEntity? {
            threads += Thread.currentThread().name
            return delegate.getByBook(bookId)
        }

        override suspend fun getProgressForBooks(bookIds: List<String>): List<ReadingProgressEntity> {
            threads += Thread.currentThread().name
            return delegate.getProgressForBooks(bookIds)
        }

        override suspend fun getAll(): List<ReadingProgressEntity> = delegate.getAll()

        override suspend fun deleteByBook(bookId: String) = delegate.deleteByBook(bookId)
    }

    /** Настоящие фоновые диспетчеры: на тестовых «одном потоке» уход с главного потока не виден. */
    private object BackgroundDispatchers : AppDispatchers {
        override val io = kotlinx.coroutines.Dispatchers.IO
        override val default = kotlinx.coroutines.Dispatchers.Default
        override val main = kotlinx.coroutines.Dispatchers.Default
        override val metadata = kotlinx.coroutines.Dispatchers.Default
    }

    /** Хеши содержимого в этих проверках не нужны: дубликаты не ищутся. */
    private object NoContentHashes : ContentHasher {
        override fun contentHash(documentUri: String): String? = null
    }

    /** Удаление и запись файлов в этих проверках не участвуют: читается только страница полки. */
    @Suppress("LongParameterList")
    private class FakeFileRemover : SafTreeWriter {

        override suspend fun write(
            treeUri: String,
            relativeDir: String,
            fileName: String,
            bytes: ByteArray,
            mimeType: String,
        ): AppResult<String> = AppResult.failure(AppError.Unknown(message = "в этой проверке не используется"))

        override suspend fun writeStream(
            treeUri: String,
            relativeDir: String,
            fileName: String,
            mimeType: String,
            onProgress: suspend (bytesWritten: Long, total: Long) -> Unit,
            block: suspend (emit: suspend (ByteArray) -> Unit) -> Unit,
        ): AppResult<String> = AppResult.failure(AppError.Unknown(message = "в этой проверке не используется"))

        override suspend fun delete(documentUri: String): AppResult<Unit> =
            AppResult.failure(AppError.Unknown(message = "в этой проверке не используется"))
    }

    private companion object {
        const val SOURCE_ID = "source-1"

        /** Таблицы, по которым видно работу приложения, а не служебные обращения Room. */
        val APP_QUERIES = listOf("FROM books", "FROM reading_progress", "FROM metadata_overrides")
    }
}
