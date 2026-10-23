package io.github.bookshelf.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.bookshelf.core.common.error.AppError
import io.github.bookshelf.core.common.error.AppResult
import io.github.bookshelf.core.common.error.isSuccess
import io.github.bookshelf.core.common.testing.FakeAppClock
import io.github.bookshelf.core.common.testing.TestAppDispatchers
import io.github.bookshelf.core.data.author.BookAuthorWriter
import io.github.bookshelf.core.data.duplicates.DuplicateFinder
import io.github.bookshelf.core.data.model.Source
import io.github.bookshelf.core.data.repository.impl.BookRepositoryImpl
import io.github.bookshelf.core.database.BookshelfDatabase
import io.github.bookshelf.core.database.entity.AuthorEntity
import io.github.bookshelf.core.database.entity.BookAuthorCrossRef
import io.github.bookshelf.core.database.entity.BookCopyEntity
import io.github.bookshelf.core.database.entity.BookEntity
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
import org.junit.After
import org.junit.Before

/**
 * Общая обвязка тестов репозитория полки: in-memory Room, подменённые порты и данные библиотеки.
 *
 * Сценариев много и они проверяют один репозиторий, поэтому общая часть вынесена сюда,
 * а наборы лежат в отдельных классах — иначе один файл перестаёт читаться.
 */
abstract class BookRepositoryTestBase {
    protected lateinit var database: BookshelfDatabase

    protected lateinit var repository: BookRepository

    /** Источники и запуск сканов: только их и трогает пересканирование. */
    protected val sources = FakeSourceRepository()

    protected val scans = FakeScanRepository()

    /** Удаление файлов и хеши содержимого: обе операции подменяются, файловой системы в тесте нет. */
    protected val files = FakeFileRemover()

    protected val hashes = FakeContentHasher()

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
            sourceRepository = sources,
            scanRepository = scans,
            readerSession = FakeReaderSession(),
            duplicateFinder = DuplicateFinder(hashes),
            fileRemover = files,
            clock = FakeAppClock(),
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

    protected fun source(id: String, enabled: Boolean = true): Source = Source(
        id = id,
        uri = "content://com.android.externalstorage.documents/tree/$id",
        displayName = "Папка $id",
        kind = io.github.bookshelf.core.data.model.SourceKind.SAF_TREE,
        enabled = enabled,
    )

    protected fun AppResult<Unit>.assertOk() {
        assertThat(isSuccess).isTrue()
    }

    /** Число строк связи по книге: проверяется сырым запросом, чтобы тест не зависел от DAO. */
    protected fun countRows(table: String, bookId: String): Int =
        database.openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM $table WHERE bookId = ?", arrayOf(bookId))
            .use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }

    /** Отметить книгу открытой: так же, как это делает открытие из полки. */
    protected suspend fun givenOpenedAt(bookId: String, at: Long) {
        val book = checkNotNull(database.bookDao().getById(bookId))
        database.bookDao().update(book.copy(lastOpenedAt = at))
    }

    /** Прогресс чтения книги: доля, а не проценты — так его хранит таблица. */
    protected suspend fun givenProgress(bookId: String, percent: Float) {
        database.readingProgressDao().upsert(
            ReadingProgressEntity(
                bookId = bookId,
                percent = percent,
                updatedAt = 2_000L,
                source = "FBREADER_AIDL",
            ),
        )
    }

    protected suspend fun givenLibrary() {
        database.sourceDao().insert(
            SourceEntity(
                id = SOURCE_ID,
                uri = "content://com.android.externalstorage.documents/tree/books",
                displayName = "Books",
                kind = SourceKind.SAF_TREE,
            ),
        )
        database.bookDao().upsertAll(
            listOf(
                book("war", "Война и мир", "Лев Толстой", series = "Классика", year = 1869, ext = "fb2"),
                book("anna", "Анна Каренина", "Лев Толстой", series = "Классика", year = 1877, ext = "epub"),
                book("crime", "Преступление и наказание", "Фёдор Достоевский", "Русская классика", 1866, "fb2"),
                book("mothers", "Матери и дети", "Максим Горький", "Советская", 1910, "pdf"),
                book("resurrection", "Воскресение", "Лев Толстой", "Русская классика", 1899, "epub"),
            ),
        )
        database.authorDao().upsertAll(
            listOf(
                AuthorEntity(id = TOLSTOY, name = "Лев Толстой", sortName = "Толстой"),
                AuthorEntity(id = DOSTOEVSKY, name = "Фёдор Достоевский", sortName = "Достоевский"),
                AuthorEntity(id = GORKY, name = "Максим Горький", sortName = "Горький"),
            ),
        )
        database.authorDao().insertBookAuthors(
            listOf(
                BookAuthorCrossRef(bookId = "war", authorId = TOLSTOY),
                BookAuthorCrossRef(bookId = "anna", authorId = TOLSTOY),
                BookAuthorCrossRef(bookId = "resurrection", authorId = TOLSTOY),
                BookAuthorCrossRef(bookId = "crime", authorId = DOSTOEVSKY),
                BookAuthorCrossRef(bookId = "mothers", authorId = GORKY),
            ),
        )
    }

    /**
     * Фейк удаления файлов: помнит, что просили удалить, и умеет отказать.
     *
     * Свой интерфейс — значит фейк, а не `mockk` (ADR-012, правило 2).
     */
    protected class FakeFileRemover : SafTreeWriter {

        var refuse: io.github.bookshelf.core.common.error.AppError? = null

        val deleted = mutableListOf<String>()

        override suspend fun write(
            treeUri: String,
            relativeDir: String,
            fileName: String,
            bytes: ByteArray,
            mimeType: String,
        ): AppResult<String> = AppResult.success("content://doc/fake")

        override suspend fun writeStream(
            treeUri: String,
            relativeDir: String,
            fileName: String,
            mimeType: String,
            onProgress: suspend (bytesWritten: Long, total: Long) -> Unit,
            block: suspend (emit: suspend (ByteArray) -> Unit) -> Unit,
        ): AppResult<String> = AppResult.success("content://doc/fake")

        override suspend fun delete(documentUri: String): AppResult<Unit> {
            refuse?.let { error -> return AppResult.failure(error) }
            deleted += documentUri
            return AppResult.success(Unit)
        }
    }

    /** Фейк хеша содержимого: одна и та же строка означает «файлы идентичны». */
    protected class FakeContentHasher : ContentHasher {

        var hash: String = "разный"

        override fun contentHash(documentUri: String): String = hash
    }

    @Suppress("LongParameterList")

    protected fun book(
        id: String,
        title: String,
        author: String,
        series: String? = null,
        year: Int? = null,
        ext: String,
    ) = BookEntity(
        id = id,
        sourceId = SOURCE_ID,
        documentUri = "content://tree/books/document/$id",
        relativePath = "$title.$ext",
        fileName = "$title.$ext",
        extension = ext,
        mimeType = "application/$ext",
        sizeBytes = 1000L,
        lastModified = 1000L,
        title = title,
        sortTitle = title,
        authorsText = author,
        seriesName = series,
        publishedYear = year,
        addedAt = 1000L,
        updatedAt = 1000L,
        lastSeenAt = 1000L,
    )


    /** Копия, созданная читалкой: тот же файл под другим адресом. */
    protected suspend fun givenCopy(bookId: String, copyUri: String) {
        database.bookCopyDao().upsert(
            BookCopyEntity(
                copyDocumentUri = copyUri,
                bookId = bookId,
                sourceId = "source-1",
                sizeBytes = 100L,
                lastModified = 100L,
                contentHash = "hash-1",
            ),
        )
    }

    protected companion object {
        const val DOWNLOAD_ID = "download:one"

        const val COPY_URI =
            "content://com.android.externalstorage.documents/tree/primary%3ABooks/document/" +
                "primary%3ABooks%2F2026-09-30%2Fbook.fb2.zip"

        const val SOURCE_ID = "source-1"

        // Ключ автора — значение books.authorsText: нормализованные authors/book_authors
        // приложение не заполняет, и отбор по ним не находил ни одной книги.
        const val TOLSTOY = "Лев Толстой"
        const val DOSTOEVSKY = "Фёдор Достоевский"
        const val GORKY = "Максим Горький"

        const val ALEXANDER = "Александр Тарарев"
        const val YURY = "Юрий Тарарев"

        /** Идентификатор автора — тот же, что пишет приложение: `author:` + имя в нижнем регистре. */
        fun authorIdOf(name: String): String = AUTHOR_ID_PREFIX + name.lowercase()

        const val AUTHOR_ID_PREFIX = "author:"
    }
}
